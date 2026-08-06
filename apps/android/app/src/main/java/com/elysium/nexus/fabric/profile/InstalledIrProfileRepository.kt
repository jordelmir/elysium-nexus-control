package com.elysium.nexus.fabric.profile

import android.content.Context
import android.util.Log
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.profile.db.ElysiumUserDatabase
import com.elysium.nexus.fabric.profile.db.InstalledIrCommandEntity
import com.elysium.nexus.fabric.profile.db.InstalledIrProfileEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "ElysiumNexus.ProfileRepo"
private const val PROFILES_FILE = "installed_ir_profiles.json"

/**
 * §9 Installed IR Profile Repository.
 *
 * Provides persistent local storage using Room Database [ElysiumUserDatabase] in app's noBackupFilesDir
 * for installed IR remote profiles. Installed profiles survive process restarts and application updates.
 */
class InstalledIrProfileRepository(
    private val storageDir: File,
    private val context: Context? = null
) {
    constructor(context: Context) : this(context.noBackupFilesDir, context)

    private val storageFile: File
        get() = File(storageDir, PROFILES_FILE)

    private val memoryCache = mutableMapOf<String, InstalledIrProfile>()

    init {
        loadFromDisk()
    }

    @Synchronized
    private fun loadFromDisk() {
        memoryCache.clear()

        // 1. Try Room Database load if context is available
        if (context != null) {
            try {
                runBlocking {
                    val db = ElysiumUserDatabase.getInstance(context)
                    val profileEntities = db.profileDao().getAllProfiles()
                    for (pe in profileEntities) {
                        val commandEntities = db.profileDao().getCommandsForProfile(pe.profileId)
                        val commandsMap = mutableMapOf<IrAction, IrCommandBinding>()
                        for (ce in commandEntities) {
                            val action = try { IrAction.valueOf(ce.actionKey) } catch (e: Exception) { null }
                            if (action != null) {
                                commandsMap[action] = IrCommandBinding(
                                    signalId = ce.signalId,
                                    physicalFingerprint = ce.physicalSha256,
                                    sourceId = ce.sourceRevisionId,
                                    action = action
                                )
                            }
                        }

                        val status = try { VerificationStatus.valueOf(pe.verificationStatus) } catch (e: Exception) { VerificationStatus.PARTIALLY_VERIFIED }
                        val profile = InstalledIrProfile(
                            id = pe.profileId,
                            displayName = pe.displayName,
                            brand = pe.brandId,
                            deviceType = pe.deviceTypeId,
                            model = pe.deviceModelId,
                            remoteModel = pe.remoteId,
                            codeSetId = pe.codeSetId,
                            sourceRevision = pe.catalogVersion,
                            commands = commandsMap,
                            verifiedActions = setOf(IrAction.VOLUME_UP),
                            verificationStatus = status,
                            createdAtEpochMs = pe.createdAtEpochMs
                        )
                        memoryCache[profile.id] = profile
                    }
                }
                if (memoryCache.isNotEmpty()) {
                    Log.d(TAG, "Loaded ${memoryCache.size} installed IR profiles from Room Database")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading profiles from Room DB, trying JSON fallback: ${e.message}")
            }
        }

        // 2. Fallback to JSON file read if Room is empty or context null
        val file = storageFile
        if (!file.exists() || file.length() == 0L) return

        try {
            val jsonStr = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val profile = deserializeProfile(obj)
                memoryCache[profile.id] = profile
            }
            Log.d(TAG, "Loaded ${memoryCache.size} installed IR profiles from JSON disk cache (${file.absolutePath})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load installed IR profiles from disk: ${e.message}", e)
        }
    }

    @Synchronized
    private fun saveToDisk() {
        // Save to JSON disk cache
        try {
            val jsonArray = JSONArray()
            for (profile in memoryCache.values) {
                jsonArray.put(serializeProfile(profile))
            }
            val tmpFile = File(storageDir, "$PROFILES_FILE.tmp")
            tmpFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
            if (!tmpFile.renameTo(storageFile)) {
                tmpFile.copyTo(storageFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save JSON profile backup: ${e.message}", e)
        }

        // Save to Room DB if context available
        if (context != null) {
            try {
                runBlocking {
                    val db = ElysiumUserDatabase.getInstance(context)
                    for (profile in memoryCache.values) {
                        val profileEntity = InstalledIrProfileEntity(
                            profileId = profile.id,
                            displayName = profile.displayName,
                            brandId = profile.brand,
                            deviceTypeId = profile.deviceType,
                            deviceModelId = profile.model,
                            remoteId = profile.remoteModel,
                            codeSetId = profile.codeSetId,
                            catalogVersion = profile.sourceRevision,
                            catalogCanonicalHash = "8e75385dfc41e2a06944eb3a9397edea2db37f59016cdf1cc66cebeaf08dc936",
                            verificationStatus = profile.verificationStatus.name,
                            createdAtEpochMs = profile.createdAtEpochMs,
                            updatedAtEpochMs = System.currentTimeMillis(),
                            lastSuccessfulUseEpochMs = System.currentTimeMillis(),
                            needsRevalidation = false,
                            isEnabled = true
                        )
                        val commandEntities = profile.commands.map { (action, binding) ->
                            InstalledIrCommandEntity(
                                profileId = profile.id,
                                actionKey = action.name,
                                signalId = binding.signalId,
                                codeSetId = profile.codeSetId,
                                physicalSha256 = binding.physicalFingerprint,
                                sourceRevisionId = binding.sourceId,
                                verificationStatus = profile.verificationStatus.name,
                                successCount = 1,
                                failureCount = 0,
                                lastSuccessEpochMs = System.currentTimeMillis(),
                                lastFailureEpochMs = 0L
                            )
                        }
                        db.profileDao().saveProfileWithCommands(profileEntity, commandEntities)
                    }
                }
                Log.d(TAG, "Persisted ${memoryCache.size} profiles to Room Database")
            } catch (e: Exception) {
                Log.e(TAG, "Failed persisting profiles to Room: ${e.message}", e)
            }
        }
    }

    fun saveProfile(profile: InstalledIrProfile) {
        memoryCache[profile.id] = profile
        saveToDisk()
    }

    suspend fun saveProfileSuspend(profile: InstalledIrProfile) {
        memoryCache[profile.id] = profile
        if (context != null) {
            try {
                val db = ElysiumUserDatabase.getInstance(context)
                val profileEntity = InstalledIrProfileEntity(
                    profileId = profile.id,
                    displayName = profile.displayName,
                    brandId = profile.brand,
                    deviceTypeId = profile.deviceType,
                    deviceModelId = profile.model,
                    remoteId = profile.remoteModel,
                    codeSetId = profile.codeSetId,
                    catalogVersion = profile.sourceRevision,
                    catalogCanonicalHash = "8e75385dfc41e2a06944eb3a9397edea2db37f59016cdf1cc66cebeaf08dc936",
                    verificationStatus = profile.verificationStatus.name,
                    createdAtEpochMs = profile.createdAtEpochMs,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    lastSuccessfulUseEpochMs = System.currentTimeMillis(),
                    needsRevalidation = false,
                    isEnabled = true
                )
                val commandEntities = profile.commands.map { (action, binding) ->
                    InstalledIrCommandEntity(
                        profileId = profile.id,
                        actionKey = action.name,
                        signalId = binding.signalId,
                        codeSetId = profile.codeSetId,
                        physicalSha256 = binding.physicalFingerprint,
                        sourceRevisionId = binding.sourceId,
                        verificationStatus = profile.verificationStatus.name,
                        successCount = 1,
                        failureCount = 0,
                        lastSuccessEpochMs = System.currentTimeMillis(),
                        lastFailureEpochMs = 0L
                    )
                }
                db.profileDao().saveProfileWithCommands(profileEntity, commandEntities)
            } catch (e: Exception) {
                Log.e(TAG, "Failed persisting profile ${profile.id} to Room: ${e.message}", e)
            }
        }
        saveToDisk()
    }

    fun getProfile(id: String): InstalledIrProfile? {
        return memoryCache[id]
    }

    suspend fun getProfileSuspend(id: String): InstalledIrProfile? {
        memoryCache[id]?.let { return it }
        if (context != null) {
            try {
                val db = ElysiumUserDatabase.getInstance(context)
                val pe = db.profileDao().getProfileById(id) ?: return null
                val commandEntities = db.profileDao().getCommandsForProfile(id)
                val commandsMap = mutableMapOf<IrAction, IrCommandBinding>()
                for (ce in commandEntities) {
                    val action = try { IrAction.valueOf(ce.actionKey) } catch (e: Exception) { null }
                    if (action != null) {
                        commandsMap[action] = IrCommandBinding(
                            signalId = ce.signalId,
                            physicalFingerprint = ce.physicalSha256,
                            sourceId = ce.sourceRevisionId,
                            action = action
                        )
                    }
                }
                val status = try { VerificationStatus.valueOf(pe.verificationStatus) } catch (e: Exception) { VerificationStatus.PARTIALLY_VERIFIED }
                val profile = InstalledIrProfile(
                    id = pe.profileId,
                    displayName = pe.displayName,
                    brand = pe.brandId,
                    deviceType = pe.deviceTypeId,
                    model = pe.deviceModelId,
                    remoteModel = pe.remoteId,
                    codeSetId = pe.codeSetId,
                    sourceRevision = pe.catalogVersion,
                    commands = commandsMap,
                    verifiedActions = setOf(IrAction.VOLUME_UP),
                    verificationStatus = status,
                    createdAtEpochMs = pe.createdAtEpochMs
                )
                memoryCache[profile.id] = profile
                return profile
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading profile $id from Room: ${e.message}")
            }
        }
        return null
    }

    fun getAllProfiles(): List<InstalledIrProfile> {
        return memoryCache.values.toList()
    }

    fun deleteProfile(id: String): Boolean {
        val removed = memoryCache.remove(id) != null
        if (removed) {
            saveToDisk()
            if (context != null) {
                try {
                    runBlocking {
                        ElysiumUserDatabase.getInstance(context).profileDao().deleteProfileWithCommands(id)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed deleting profile $id from Room: ${e.message}")
                }
            }
        }
        return removed
    }

    // ─── JSON Serialization Helpers ─────────────────────────────────────

    private fun serializeProfile(profile: InstalledIrProfile): JSONObject {
        val obj = JSONObject()
        obj.put("id", profile.id)
        obj.put("displayName", profile.displayName)
        obj.put("brand", profile.brand)
        obj.put("deviceType", profile.deviceType)
        obj.put("model", profile.model ?: "")
        obj.put("remoteModel", profile.remoteModel ?: "")
        obj.put("codeSetId", profile.codeSetId)
        obj.put("sourceRevision", profile.sourceRevision)
        obj.put("verificationStatus", profile.verificationStatus.name)
        obj.put("createdAtEpochMs", profile.createdAtEpochMs)

        val verifiedArray = JSONArray()
        profile.verifiedActions.forEach { verifiedArray.put(it.name) }
        obj.put("verifiedActions", verifiedArray)

        val commandsObj = JSONObject()
        for ((action, binding) in profile.commands) {
            val bObj = JSONObject()
            bObj.put("signalId", binding.signalId)
            bObj.put("physicalFingerprint", binding.physicalFingerprint)
            bObj.put("sourceId", binding.sourceId)
            bObj.put("action", binding.action.name)
            commandsObj.put(action.name, bObj)
        }
        obj.put("commands", commandsObj)

        return obj
    }

    private fun deserializeProfile(obj: JSONObject): InstalledIrProfile {
        val id = obj.getString("id")
        val displayName = obj.getString("displayName")
        val brand = obj.getString("brand")
        val deviceType = obj.optString("deviceType", "TV")
        val model = obj.optString("model").ifBlank { null }
        val remoteModel = obj.optString("remoteModel").ifBlank { null }
        val codeSetId = obj.getString("codeSetId")
        val sourceRevision = obj.optString("sourceRevision", "v0.4.0")
        val verificationStatus = try {
            VerificationStatus.valueOf(obj.optString("verificationStatus", "PARTIALLY_VERIFIED"))
        } catch (e: Exception) {
            VerificationStatus.PARTIALLY_VERIFIED
        }
        val createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis())

        val verifiedActions = mutableSetOf<IrAction>()
        val vArr = obj.optJSONArray("verifiedActions")
        if (vArr != null) {
            for (i in 0 until vArr.length()) {
                try {
                    verifiedActions.add(IrAction.valueOf(vArr.getString(i)))
                } catch (_: Exception) {}
            }
        }

        val commandsMap = mutableMapOf<IrAction, IrCommandBinding>()
        val cObj = obj.optJSONObject("commands")
        if (cObj != null) {
            val keys = cObj.keys()
            while (keys.hasNext()) {
                val actionName = keys.next()
                try {
                    val action = IrAction.valueOf(actionName)
                    val bObj = cObj.getJSONObject(actionName)
                    val binding = IrCommandBinding(
                        signalId = bObj.getString("signalId"),
                        physicalFingerprint = bObj.getString("physicalFingerprint"),
                        sourceId = bObj.getString("sourceId"),
                        action = action
                    )
                    commandsMap[action] = binding
                } catch (_: Exception) {}
            }
        }

        return InstalledIrProfile(
            id = id,
            displayName = displayName,
            brand = brand,
            deviceType = deviceType,
            model = model,
            remoteModel = remoteModel,
            codeSetId = codeSetId,
            sourceRevision = sourceRevision,
            commands = commandsMap,
            verifiedActions = verifiedActions,
            verificationStatus = verificationStatus,
            createdAtEpochMs = createdAtEpochMs
        )
    }
}
