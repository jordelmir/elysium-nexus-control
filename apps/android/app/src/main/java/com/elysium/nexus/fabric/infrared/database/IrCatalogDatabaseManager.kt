package com.elysium.nexus.fabric.infrared.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.security.MessageDigest

private const val TAG = "ElysiumNexus.DbManager"
private const val DB_DIR_NAME = "ir-catalog"
private const val DB_FILE_NAME = "ir_catalog.db"
private const val EXPECTED_MANIFEST_HASH = ""

/**
 * §7.1 Application Singleton Database Manager for IR Catalog.
 *
 * Manages the atomic, checksum-verified installation of `ir_catalog.db` from assets into
 * `context.noBackupFilesDir/ir-catalog/ir_catalog.db`.
 * Ensures thread-safe single initialization via [Mutex] and supports database verification.
 */
class IrCatalogDatabaseManager private constructor(
    private val applicationContext: Context
) : Closeable {

    private val mutex = Mutex()
    private var repositoryInstance: IrCatalogRepository? = null

    val targetDirectory: File
        get() = File(applicationContext.noBackupFilesDir, DB_DIR_NAME).apply { if (!exists()) mkdirs() }

    val databaseFile: File
        get() = File(targetDirectory, DB_FILE_NAME)

    /**
     * §7 Installation result — propagates success/failure to caller.
     */
    sealed interface InstallResult {
        data object AlreadyInstalled : InstallResult
        data class Installed(val dbHash: String) : InstallResult
        data class Failed(val reason: String, val cause: Exception? = null) : InstallResult
    }

    suspend fun getRepository(): IrCatalogRepository = mutex.withLock {
        repositoryInstance?.let { return it }

        val installResult = ensureDatabaseInstalled()
        if (installResult is InstallResult.Failed) {
            Log.e(TAG, "Database installation failed: ${installResult.reason}")
        }

        val repo = IrCatalogRepository.getInstance(applicationContext)
        repositoryInstance = repo
        repo
    }

        /**
     * §7 Public path used by [IrCatalogRepository] to guarantee the on-disk
     * catalog always matches the packaged asset before any query runs.
     * Does NOT take the mutex (callers own their own serialization).
     */
    fun ensureDatabaseInstalled(): InstallResult = ensureDatabaseInstalledInternal()

    private fun ensureDatabaseInstalledInternal(): InstallResult {
        val dbFile = databaseFile
        val assetManager = applicationContext.assets

        // If database already exists and is non-empty, verify it matches the shipped asset.
        // §7 API up-sell: an in-place `adb install -r` (or Play update) must refresh the
        // catalog — reusing a stale copy silently ships a DB that lacks new brands/code
        // sets (e.g. curated Kintech / Control Universal TV). We compare the SHA-256 of
        // the installed file with the asset; any drift triggers a reinstall.
        if (dbFile.exists() && dbFile.length() > 0L) {
            val installedHash = computeSha256(dbFile)
            val assetHash = computeAssetSha256()
            if (installedHash == assetHash) {
                val integrityOk = verifyDatabaseIntegrity(dbFile)
                if (integrityOk) {
                    Log.d(TAG, "Existing IR catalog database matches asset at ${dbFile.absolutePath} (${dbFile.length()} bytes)")
                    return InstallResult.AlreadyInstalled
                }
                Log.w(TAG, "Existing database failed integrity check, reinstalling from assets")
            } else {
                Log.i(TAG, "Asset SHA256 differs from installed database — refreshing catalog (installed=$installedHash asset=$assetHash)")
            }
        } else if (dbFile.exists()) {
            Log.i(TAG, "Existing database file is empty (0 bytes), reinstalling from assets")
        }

        Log.i(TAG, "Installing IR catalog database from assets to ${dbFile.absolutePath}...")
        // P0-16: Atomic installation — write to temp file, verify, then rename.
        // Prevents corrupt DB if process dies during copy.
        val tempFile = File(targetDirectory, "$DB_FILE_NAME.tmp")
        try {
            assetManager.open("ir/$DB_FILE_NAME").use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }

            // P0-16: Verify temp file before atomic rename
            val computedHash = computeSha256(tempFile)
            Log.i(TAG, "Database asset extracted to temp. Size: ${tempFile.length()} bytes, SHA256: $computedHash")

            if (EXPECTED_MANIFEST_HASH.isNotBlank() && computedHash != EXPECTED_MANIFEST_HASH) {
                tempFile.delete()
                return InstallResult.Failed("Checksum mismatch: expected=$EXPECTED_MANIFEST_HASH, actual=$computedHash")
            }

            if (tempFile.length() <= 0L) {
                Log.e(TAG, "Database file is empty after copy (${tempFile.length()} bytes)")
                tempFile.delete()
                return InstallResult.Failed("Installed database is empty after copy")
            }

            if (!verifyDatabaseIntegrity(tempFile)) {
                tempFile.delete()
                return InstallResult.Failed("Post-install integrity check failed on extracted asset")
            }

            // P0-16: Atomic rename — temp → target. On Android/Linux, rename is atomic
            // for same-filesystem moves. Old file is replaced only after temp is verified.
            if (dbFile.exists()) dbFile.delete()
            val renamed = tempFile.renameTo(dbFile)
            if (!renamed) {
                tempFile.delete()
                return InstallResult.Failed("Atomic rename failed: temp=${tempFile.absolutePath} → target=${dbFile.absolutePath}")
            }

            Log.i(TAG, "Successfully installed ir_catalog.db (atomic swap)")
            return InstallResult.Installed(computedHash)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install ir_catalog.db asset: ${e.message}", e)
            return InstallResult.Failed("Installation exception: ${e.message}", e)
        }
    }

    /**
     * §7 Verify database integrity using SQLite PRAGMA checks.
     */
    private fun verifyDatabaseIntegrity(dbFile: File): Boolean {
        return try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            db.use {
                // §7 foreign_key_check
                val fkCursor = it.rawQuery("PRAGMA foreign_key_check", null)
                fkCursor.use { c ->
                    if (c.count > 0) {
                        Log.w(TAG, "foreign_key_check found violations")
                        return false
                    }
                }
                // §7 quick_check
                val qcCursor = it.rawQuery("PRAGMA quick_check", null)
                qcCursor.use { c ->
                    if (c.moveToFirst()) {
                        val result = c.getString(0)
                        if (result != "ok") {
                            Log.w(TAG, "quick_check returned: $result")
                            return false
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Integrity check failed: ${e.message}")
            false
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * §7 Stream the packed asset and compute its SHA-256 without loading the whole
     * ~19 MB into memory. `AssetManager` does not expose a File handle, so we hash
     * while streaming from the open stream.
     */
    private fun computeAssetSha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        applicationContext.assets.open("ir/$DB_FILE_NAME").use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun close() {
        repositoryInstance = null
    }

    companion object {
        @Volatile
        private var instance: IrCatalogDatabaseManager? = null

        fun getInstance(context: Context): IrCatalogDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: IrCatalogDatabaseManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
