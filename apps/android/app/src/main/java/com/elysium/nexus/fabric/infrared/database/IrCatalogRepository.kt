package com.elysium.nexus.fabric.infrared.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.elysium.nexus.core.device.CatalogCommandBinding
import com.elysium.nexus.core.device.CodeProvenance
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.ProtocolCodecRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

private const val TAG = "ElysiumNexus.IrCatalogV4"
private const val MAX_PATTERN_SLICES = 4096

private data class CodeSetCommandsResult(
    val commands: Map<IrAction, IrSignal>,
    val commandSignalIds: Map<IrAction, String>,
    val commandBindings: List<CatalogCommandBinding>
)

/**
 * §2 Canonical Schema v4 SQLite IR Catalog Repository.
 *
 * Provides authoritative query access to Schema v4 [code_sets], [command_bindings],
 * and [signals] tables.
 * Guaranteed: A single [IrCodeSet] contains ALL command bindings belonging to that remote
 * with exact database [signalId]s. Zero manufactured signal IDs.
 */
class IrCatalogRepository(
    private val context: Context
) : IrCatalog {

    private fun getDatabase(): SQLiteDatabase {
        val manager = IrCatalogDatabaseManager.getInstance(context)
        val dbFile = manager.databaseFile
        if (!dbFile.exists() || dbFile.length() == 0L) {
            val assetManager = context.assets
            val targetDir = manager.targetDirectory
            val tmpFile = File(targetDir, "ir_catalog.db.tmp")
            assetManager.open("ir/ir_catalog.db").use { input ->
                tmpFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmpFile.renameTo(dbFile)) {
                tmpFile.copyTo(dbFile, overwrite = true)
                tmpFile.delete()
            }
        }
        return SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
    }

    override suspend fun getCandidatesForBrand(
        brand: String,
        deviceType: String,
        action: IrAction
    ): List<IrCodeSet> = withContext(Dispatchers.IO) {
        val database = getDatabase()
        val actionKey = action.name
        val results = mutableListOf<IrCodeSet>()

        val query = """
            SELECT cs.id AS cs_id, b.display_name AS brand_name, r.display_remote_model,
                   s.id AS source_name, s.license_id, dt.canonical_name AS device_type
            FROM code_sets cs
            JOIN remotes r ON cs.remote_id = r.id
            JOIN brands b ON r.brand_id = b.id
            JOIN device_types dt ON r.device_type_id = dt.id
            JOIN source_revisions sr ON cs.source_revision_id = sr.id
            JOIN sources s ON sr.source_id = s.id
            JOIN command_bindings cb ON cb.code_set_id = cs.id
            JOIN actions a ON cb.action_id = a.id
            WHERE (b.display_name LIKE ? OR b.normalized_name LIKE ?)
              AND a.canonical_key = ?
              AND (dt.canonical_name = ? OR ? = '')
              AND s.production_approved = 1
            GROUP BY cs.id
            ORDER BY cs.id
            LIMIT 200
        """.trimIndent()

        val brandArg = "%${brand.trim()}%"
        val devTypeArg = deviceType.trim()
        database.rawQuery(query, arrayOf(brandArg, brandArg, actionKey, devTypeArg, devTypeArg)).use { cursor ->
            while (cursor.moveToNext()) {
                val csId = cursor.getString(0)
                val brandName = cursor.getString(1) ?: brand
                val remoteModel = cursor.getString(2) ?: ""
                val sourceName = cursor.getString(3) ?: "Elysium Nexus Data Fabric"
                val licenseSpdx = cursor.getString(4) ?: "MIT"

                val codeSetResult = getCommandsForCodeSetInternal(database, csId)
                if (codeSetResult.commands.isNotEmpty()) {
                    results.add(
                        IrCodeSet(
                            id = csId,
                            brand = brandName,
                            modelPatterns = setOf(remoteModel),
                            remoteModels = if (remoteModel.isNotBlank()) setOf(remoteModel) else emptySet(),
                            commands = codeSetResult.commands,
                            commandSignalIds = codeSetResult.commandSignalIds,
                            commandBindings = codeSetResult.commandBindings,
                            provenance = CodeProvenance(
                                sourceName = sourceName,
                                sourceUrl = "",
                                licenseSpdx = licenseSpdx
                            ),
                            verification = VerificationStatus.UNVERIFIED
                        )
                    )
                }
            }
        }

        database.close()
        Log.d(TAG, "getCandidatesForBrand(brand=$brand, deviceType=$deviceType, action=$actionKey): ${results.size} multi-command Code Sets from Schema v4")
        results
    }

    private fun getCommandsForCodeSetInternal(
        database: SQLiteDatabase,
        codeSetId: String
    ): CodeSetCommandsResult {
        val commands = mutableMapOf<IrAction, IrSignal>()
        val commandSignalIds = mutableMapOf<IrAction, String>()
        val commandBindings = mutableListOf<CatalogCommandBinding>()

        val query = """
            SELECT a.canonical_key, sig.encoding_type, sig.codec_id, sig.carrier_hz,
                   sig.address_value, sig.sub_device_value, sig.command_value,
                   sig.pattern_blob, sig.id AS signal_id, cb.id AS binding_id,
                   sig.physical_sha256
            FROM command_bindings cb
            JOIN actions a ON cb.action_id = a.id
            JOIN signals sig ON cb.signal_id = sig.id
            WHERE cb.code_set_id = ?
        """.trimIndent()

        database.rawQuery(query, arrayOf(codeSetId)).use { cursor ->
            while (cursor.moveToNext()) {
                val actionStr = cursor.getString(0)
                val encodingType = cursor.getString(1)
                val codecId = cursor.getString(2)
                val carrierHz = cursor.getInt(3)
                val address = cursor.getInt(4)
                val subDevice = cursor.getInt(5)
                val command = cursor.getInt(6)
                val blob = cursor.getBlob(7)
                val signalId = cursor.getString(8)
                val bindingId = cursor.getString(9)
                val physicalSha256 = cursor.getString(10) ?: signalId

                val irAction = mapActionKeyToIrAction(actionStr) ?: continue

                val signal: IrSignal? = if (encodingType == "PARAMETRIC") {
                    val codecSpec = codecId?.let { ProtocolCodecRegistry.getCodec(it) }
                    if (codecSpec == null) {
                        Log.w(TAG, "Unsupported codec '$codecId' for signalId=$signalId in codeSetId=$codeSetId. Skipping without NEC fallback.")
                        null
                    } else {
                        IrSignal.Encoded(
                            carrierHz = carrierHz,
                            protocol = codecSpec.protocol,
                            address = address,
                            subDevice = if (subDevice >= 0) subDevice else null,
                            command = command
                        )
                    }
                } else if (encodingType == "RAW" && blob != null) {
                    val pattern = decompressPattern(blob)
                    if (pattern != null && pattern.all { it > 0 }) {
                        IrSignal.Raw(carrierHz = carrierHz, patternUs = pattern)
                    } else null
                } else null

                if (signal != null) {
                    commands[irAction] = signal
                    commandSignalIds[irAction] = signalId
                    commandBindings.add(
                        CatalogCommandBinding(
                            bindingId = bindingId,
                            codeSetId = codeSetId,
                            action = irAction,
                            signalId = signalId,
                            physicalSha256 = physicalSha256,
                            signal = signal
                        )
                    )
                }
            }
        }

        return CodeSetCommandsResult(commands, commandSignalIds, commandBindings)
    }

    override suspend fun getSignal(signalId: String): IrSignal? = withContext(Dispatchers.IO) {
        val database = getDatabase()
        var resultSignal: IrSignal? = null

        val query = """
            SELECT encoding_type, codec_id, carrier_hz, address_value,
                   sub_device_value, command_value, pattern_blob
            FROM signals
            WHERE id = ?
        """.trimIndent()

        database.rawQuery(query, arrayOf(signalId)).use { cursor ->
            if (cursor.moveToFirst()) {
                val encodingType = cursor.getString(0)
                val codecId = cursor.getString(1)
                val carrierHz = cursor.getInt(2)
                val address = cursor.getInt(3)
                val subDevice = cursor.getInt(4)
                val command = cursor.getInt(5)
                val blob = cursor.getBlob(6)

                if (encodingType == "PARAMETRIC") {
                    val codecSpec = codecId?.let { ProtocolCodecRegistry.getCodec(it) }
                    if (codecSpec != null) {
                        resultSignal = IrSignal.Encoded(
                            carrierHz = carrierHz,
                            protocol = codecSpec.protocol,
                            address = address,
                            subDevice = if (subDevice >= 0) subDevice else null,
                            command = command
                        )
                    }
                } else if (encodingType == "RAW" && blob != null) {
                    val pattern = decompressPattern(blob)
                    if (pattern != null && pattern.all { it > 0 }) {
                        resultSignal = IrSignal.Raw(carrierHz = carrierHz, patternUs = pattern)
                    }
                }
            }
        }

        database.close()
        resultSignal
    }

    override suspend fun searchBrands(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val database = getDatabase()
        val results = mutableListOf<String>()

        database.rawQuery(
            "SELECT DISTINCT display_name FROM brands WHERE display_name LIKE ? OR normalized_name LIKE ? ORDER BY display_name LIMIT 50",
            arrayOf("%$query%", "%${query.lowercase()}%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
        }
        database.close()
        results
    }

    override suspend fun searchDevices(query: String): List<DeviceSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val database = getDatabase()
        val results = mutableListOf<DeviceSearchResult>()

        val searchQuery = """
            SELECT r.id, b.display_name, dt.canonical_name, r.display_remote_model, s.display_name
            FROM remotes r
            JOIN brands b ON r.brand_id = b.id
            JOIN device_types dt ON r.device_type_id = dt.id
            JOIN source_files sf ON r.source_file_id = sf.id
            JOIN source_revisions sr ON sf.source_revision_id = sr.id
            JOIN sources s ON sr.source_id = s.id
            WHERE (b.display_name LIKE ? OR r.display_remote_model LIKE ?)
              AND s.production_approved = 1
            LIMIT 100
        """.trimIndent()

        val q = "%$query%"
        database.rawQuery(searchQuery, arrayOf(q, q)).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    DeviceSearchResult(
                        id = cursor.getString(0),
                        brand = cursor.getString(1),
                        model = cursor.getString(3) ?: "",
                        category = cursor.getString(2) ?: "TV",
                        remoteModel = cursor.getString(3) ?: "",
                        source = cursor.getString(4) ?: "Elysium Data Fabric"
                    )
                )
            }
        }
        database.close()
        results
    }

    override suspend fun getStats(): CatalogStats = withContext(Dispatchers.IO) {
        val database = getDatabase()
        var brands = 0; var types = 0; var remotes = 0
        var codeSets = 0; var signals = 0; var bindings = 0

        database.rawQuery("SELECT COUNT(*) FROM brands", null).use { if (it.moveToFirst()) brands = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM device_types", null).use { if (it.moveToFirst()) types = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM remotes", null).use { if (it.moveToFirst()) remotes = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM code_sets", null).use { if (it.moveToFirst()) codeSets = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM signals", null).use { if (it.moveToFirst()) signals = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM command_bindings", null).use { if (it.moveToFirst()) bindings = it.getInt(0) }
        database.close()

        CatalogStats(brands, types, remotes, codeSets, signals, bindings, 7)
    }

    private fun mapActionKeyToIrAction(actionKey: String): IrAction? = try {
        IrAction.valueOf(actionKey)
    } catch (e: Exception) {
        when (actionKey) {
            "POWER_TOGGLE", "POWER" -> IrAction.POWER_TOGGLE
            "POWER_ON" -> IrAction.POWER_ON
            "POWER_OFF" -> IrAction.POWER_OFF
            "VOLUME_UP", "VOL_UP" -> IrAction.VOLUME_UP
            "VOLUME_DOWN", "VOL_DN" -> IrAction.VOLUME_DOWN
            "MUTE" -> IrAction.MUTE
            "CHANNEL_UP", "CH_UP" -> IrAction.CHANNEL_UP
            "CHANNEL_DOWN", "CH_DN" -> IrAction.CHANNEL_DOWN
            "INPUT", "SOURCE" -> IrAction.INPUT
            "MENU" -> IrAction.MENU
            "OK", "ENTER" -> IrAction.OK
            "UP" -> IrAction.UP
            "DOWN" -> IrAction.DOWN
            "LEFT" -> IrAction.LEFT
            "RIGHT" -> IrAction.RIGHT
            "BACK", "RETURN" -> IrAction.BACK
            "HOME" -> IrAction.HOME
            "PLAY" -> IrAction.PLAY
            "PAUSE" -> IrAction.PAUSE
            "STOP" -> IrAction.STOP
            else -> null
        }
    }

    private fun decompressPattern(blob: ByteArray): IntArray? {
        return try {
            val inflater = Inflater()
            inflater.setInput(blob)
            val output = ByteArray(MAX_PATTERN_SLICES * 4)
            val decompressedLen = inflater.inflate(output)
            inflater.end()

            if (decompressedLen <= 0 || decompressedLen % 4 != 0) return null
            val count = decompressedLen / 4
            val buffer = ByteBuffer.wrap(output, 0, decompressedLen).order(ByteOrder.LITTLE_ENDIAN)
            val result = IntArray(count)
            for (i in 0 until count) {
                val valUs = buffer.getInt()
                if (valUs <= 0) return null
                result[i] = valUs
            }
            result
        } catch (e: Exception) {
            null
        }
    }
}
