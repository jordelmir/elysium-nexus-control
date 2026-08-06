package com.elysium.nexus.fabric.infrared.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.elysium.nexus.core.device.CodeProvenance
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.infrared.IrProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

private const val TAG = "ElysiumNexus.IrCatalog"
private const val DB_ASSET = "ir/ir_catalog.db"
private const val MAX_PATTERN_SLICES = 4096
private const val MAX_PATTERN_BYTES = MAX_PATTERN_SLICES * 4

/**
 * The §21 Production SQLite IR Catalog Repository.
 *
 * Implements [IrCatalog]. Reads precompiled `ir_catalog.db` from Android assets,
 * copies it atomically with integrity checks to local cache, and queries candidate
 * [IrCodeSet]s for [IrProbeEngine].
 *
 * Local-first: 0 network calls required.
 */
class IrCatalogRepository(
    private val context: Context
) : IrCatalog {
    private var db: SQLiteDatabase? = null

    /**
     * Atomically copy database from assets to cache directory with integrity verification.
     */
    private fun ensureDb(): SQLiteDatabase {
        db?.let { if (it.isOpen) return it }

        val dbFile = File(context.cacheDir, "ir_catalog.db")
        val tmpFile = File(context.cacheDir, "ir_catalog.db.tmp")

        if (!dbFile.exists() || dbFile.length() == 0L) {
            Log.d(TAG, "Copying ir_catalog.db atomically from assets...")
            tmpFile.delete()

            context.assets.open(DB_ASSET).use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 16384)
                }
            }

            // Verify integrity of tmp DB before making it active
            val testDb = SQLiteDatabase.openDatabase(
                tmpFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            val isOk = testDb.rawQuery("PRAGMA quick_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            testDb.close()

            if (!isOk) {
                tmpFile.delete()
                throw IllegalStateException("Asset ir_catalog.db failed SQLite integrity check")
            }

            // Atomic rename
            if (!tmpFile.renameTo(dbFile)) {
                tmpFile.copyTo(dbFile, overwrite = true)
                tmpFile.delete()
            }
            Log.d(TAG, "Successfully initialized active ir_catalog.db (${dbFile.length()} bytes)")
        }

        val database = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        db = database
        return database
    }

    override suspend fun getCandidatesForBrand(
        brand: String,
        deviceType: String,
        action: IrAction
    ): List<IrCodeSet> = withContext(Dispatchers.IO) {
        val database = ensureDb()
        val actionName = action.name
        val results = mutableListOf<IrCodeSet>()

        // ─── 1. Encoded Commands ─────────────────────────────────────
        val encodedQuery = """
            SELECT ce.id, ce.action, ce.protocol, ce.carrier_hz, ce.address,
                   ce.sub_device, ce.command, ce.fingerprint,
                   b.name AS brand_name, r.model, r.remote_model,
                   s.name AS source_name, s.license
            FROM commands_encoded ce
            JOIN remotes r ON ce.remote_id = r.id
            JOIN brands b ON r.brand_id = b.id
            JOIN device_types dt ON r.device_type_id = dt.id
            JOIN sources s ON r.source_id = s.id
            WHERE b.name LIKE ? AND ce.action = ?
              AND s.production_enabled = 1
            ORDER BY r.id
            LIMIT 500
        """.trimIndent()

        database.rawQuery(encodedQuery, arrayOf("%$brand%", actionName)).use { cursor ->
            while (cursor.moveToNext()) {
                val ceId = cursor.getInt(0)
                val proto = cursor.getString(2) ?: "NEC"
                val carrierHz = cursor.getInt(3)
                val address = cursor.getInt(4)
                val subDevice = cursor.getInt(5)
                val command = cursor.getInt(6)
                val brandName = cursor.getString(8) ?: brand
                val model = cursor.getString(9) ?: ""
                val remoteModel = cursor.getString(10) ?: ""
                val sourceName = cursor.getString(11) ?: ""
                val license = cursor.getString(12) ?: ""

                val irProtocol = mapProtocol(proto)
                val signal: IrSignal = IrSignal.Encoded(
                    carrierHz = carrierHz,
                    protocol = irProtocol,
                    address = address,
                    subDevice = if (subDevice >= 0) subDevice else null,
                    command = command
                )

                results.add(
                    IrCodeSet(
                        id = "cat-enc-$ceId",
                        brand = brandName,
                        modelPatterns = setOf(model),
                        remoteModels = if (remoteModel.isNotBlank()) setOf(remoteModel) else emptySet(),
                        commands = mapOf(action to signal),
                        provenance = CodeProvenance(
                            sourceName = sourceName,
                            sourceUrl = "",
                            licenseSpdx = license
                        ),
                        verification = VerificationStatus.UNVERIFIED
                    )
                )
            }
        }

        // ─── 2. Raw Commands ─────────────────────────────────────────
        val rawQuery = """
            SELECT cr.id, cr.action, cr.carrier_hz, cr.pattern_blob,
                   cr.duration_us, cr.fingerprint,
                   b.name AS brand_name, r.model, r.remote_model,
                   s.name AS source_name, s.license
            FROM commands_raw cr
            JOIN remotes r ON cr.remote_id = r.id
            JOIN brands b ON r.brand_id = b.id
            JOIN device_types dt ON r.device_type_id = dt.id
            JOIN sources s ON r.source_id = s.id
            WHERE b.name LIKE ? AND cr.action = ?
              AND s.production_enabled = 1
            ORDER BY r.id
            LIMIT 500
        """.trimIndent()

        database.rawQuery(rawQuery, arrayOf("%$brand%", actionName)).use { cursor ->
            while (cursor.moveToNext()) {
                val crId = cursor.getInt(0)
                val carrierHz = cursor.getInt(2)
                val blobBytes = cursor.getBlob(3) ?: continue
                val brandName = cursor.getString(6) ?: brand
                val model = cursor.getString(7) ?: ""
                val remoteModel = cursor.getString(8) ?: ""
                val sourceName = cursor.getString(9) ?: ""
                val license = cursor.getString(10) ?: ""

                val pattern = decompressPattern(blobBytes) ?: continue

                // Strict validation: all durations must be > 0 and carrier in valid range
                if (pattern.any { it <= 0 } || carrierHz <= 0) continue

                val signal: IrSignal = IrSignal.Raw(
                    carrierHz = carrierHz,
                    patternUs = pattern
                )

                results.add(
                    IrCodeSet(
                        id = "cat-raw-$crId",
                        brand = brandName,
                        modelPatterns = setOf(model),
                        remoteModels = if (remoteModel.isNotBlank()) setOf(remoteModel) else emptySet(),
                        commands = mapOf(action to signal),
                        provenance = CodeProvenance(
                            sourceName = sourceName,
                            sourceUrl = "",
                            licenseSpdx = license
                        ),
                        verification = VerificationStatus.UNVERIFIED
                    )
                )
            }
        }

        Log.d(TAG, "getCandidatesForBrand(brand=$brand, action=$actionName): ${results.size} candidates from SQLite")
        results
    }

    override suspend fun searchBrands(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val database = ensureDb()
        val results = mutableListOf<String>()

        database.rawQuery(
            "SELECT DISTINCT name FROM brands WHERE name LIKE ? ORDER BY name LIMIT 50",
            arrayOf("%$query%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
        }
        results
    }

    override suspend fun searchDevices(query: String): List<DeviceSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val database = ensureDb()
        val results = mutableListOf<DeviceSearchResult>()

        val searchQuery = """
            SELECT DISTINCT b.name, dt.name, r.model, r.remote_model, s.name
            FROM remotes r
            JOIN brands b ON r.brand_id = b.id
            JOIN device_types dt ON r.device_type_id = dt.id
            JOIN sources s ON r.source_id = s.id
            WHERE (b.name LIKE ? OR r.model LIKE ? OR r.remote_model LIKE ?)
              AND s.production_enabled = 1
            ORDER BY b.name, r.model
            LIMIT 100
        """.trimIndent()

        val q = "%$query%"
        database.rawQuery(searchQuery, arrayOf(q, q, q)).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(DeviceSearchResult(
                    id = "${cursor.getString(0)}_${cursor.getString(2)}",
                    brand = cursor.getString(0),
                    model = cursor.getString(2) ?: "",
                    category = cursor.getString(1) ?: "Miscellaneous",
                    remoteModel = cursor.getString(3) ?: "",
                    source = cursor.getString(4) ?: ""
                ))
            }
        }
        results
    }

    override suspend fun getStats(): CatalogStats = withContext(Dispatchers.IO) {
        val database = ensureDb()
        var brands = 0; var types = 0; var remotes = 0
        var encoded = 0; var raw = 0; var protocols = 0

        database.rawQuery("SELECT COUNT(*) FROM brands", null).use { if (it.moveToFirst()) brands = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM device_types", null).use { if (it.moveToFirst()) types = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM remotes", null).use { if (it.moveToFirst()) remotes = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM commands_encoded", null).use { if (it.moveToFirst()) encoded = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM commands_raw", null).use { if (it.moveToFirst()) raw = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM protocols", null).use { if (it.moveToFirst()) protocols = it.getInt(0) }

        CatalogStats(brands, types, remotes, encoded, raw, encoded + raw, protocols)
    }

    fun close() {
        db?.close()
        db = null
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun mapProtocol(proto: String): IrProtocol = when {
        proto.startsWith("NEC", ignoreCase = true) -> IrProtocol.Nec
        proto.startsWith("Samsung", ignoreCase = true) -> IrProtocol.Samsung
        proto.startsWith("SIRC", ignoreCase = true) || proto.startsWith("Sony", ignoreCase = true) -> IrProtocol.SonySirc
        proto.startsWith("RC5", ignoreCase = true) -> IrProtocol.Rc5
        proto.startsWith("RC6", ignoreCase = true) -> IrProtocol.Rc6
        proto.startsWith("Kaseikyo", ignoreCase = true) || proto.startsWith("Panasonic", ignoreCase = true) -> IrProtocol.Kaseikyo
        proto.startsWith("NECx", ignoreCase = true) || proto.startsWith("NECext", ignoreCase = true) -> IrProtocol.NecExtended
        else -> IrProtocol.Nec
    }

    /**
     * Safely decompress zlib-compressed binary blob to IntArray of microsecond durations.
     * Enforces slice limits to prevent OOM/DoS.
     */
    private fun decompressPattern(blob: ByteArray): IntArray? {
        return try {
            val inflater = Inflater()
            inflater.setInput(blob)
            val output = ByteArray(MAX_PATTERN_BYTES)
            val decompressedLen = inflater.inflate(output)
            inflater.end()

            if (decompressedLen <= 0 || decompressedLen % 4 != 0) return null
            val count = decompressedLen / 4
            if (count !in 2..MAX_PATTERN_SLICES) return null

            val buffer = ByteBuffer.wrap(output, 0, decompressedLen)
                .order(ByteOrder.LITTLE_ENDIAN)
            val result = IntArray(count)
            for (i in 0 until count) {
                val valUs = buffer.getInt()
                if (valUs <= 0) return null // All slice durations must be strictly positive
                result[i] = valUs
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decompress pattern blob: ${e.message}")
            null
        }
    }
}
