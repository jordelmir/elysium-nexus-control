package com.elysium.nexus.fabric.profile

import android.content.Context
import android.util.Log
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.VerificationStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "ElysiumNexus.ProfileRepo"
private const val PROFILES_FILE = "installed_ir_profiles.json"

/**
 * §9 Installed IR Profile Repository.
 *
 * Provides persistent local storage (in app's noBackupFilesDir) for installed IR remote profiles.
 * Installed profiles survive process restarts and application updates.
 */
class InstalledIrProfileRepository(
    private val storageDir: File
) {
    constructor(context: Context) : this(context.noBackupFilesDir)

    private val storageFile: File
        get() = File(storageDir, PROFILES_FILE)

    private val memoryCache = mutableMapOf<String, InstalledIrProfile>()

    init {
        loadFromDisk()
    }

    @Synchronized
    private fun loadFromDisk() {
        memoryCache.clear()
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
            Log.d(TAG, "Loaded ${memoryCache.size} installed IR profiles from disk (${file.absolutePath})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load installed IR profiles from disk: ${e.message}", e)
        }
    }

    @Synchronized
    private fun saveToDisk() {
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
            Log.d(TAG, "Saved ${memoryCache.size} installed IR profiles to disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save installed IR profiles: ${e.message}", e)
        }
    }

    fun saveProfile(profile: InstalledIrProfile) {
        memoryCache[profile.id] = profile
        saveToDisk()
    }

    fun getProfile(id: String): InstalledIrProfile? {
        return memoryCache[id]
    }

    fun getAllProfiles(): List<InstalledIrProfile> {
        return memoryCache.values.toList()
    }

    fun deleteProfile(id: String): Boolean {
        val removed = memoryCache.remove(id) != null
        if (removed) saveToDisk()
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
        val sourceRevision = obj.optString("sourceRevision", "v0.3.0")
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
