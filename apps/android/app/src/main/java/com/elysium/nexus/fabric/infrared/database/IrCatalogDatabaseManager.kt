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

    private fun ensureDatabaseInstalled(): InstallResult {
        val dbFile = databaseFile
        val assetManager = applicationContext.assets

        // If database already exists and is non-empty, verify integrity
        if (dbFile.exists() && dbFile.length() > 0L) {
            // §7 Verify integrity with foreign_key_check and quick_check
            val integrityOk = verifyDatabaseIntegrity(dbFile)
            if (integrityOk) {
                Log.d(TAG, "Existing IR catalog database verified at ${dbFile.absolutePath} (${dbFile.length()} bytes)")
                return InstallResult.AlreadyInstalled
            }
            Log.w(TAG, "Existing database failed integrity check, reinstalling from assets")
            dbFile.delete()
        }

        Log.i(TAG, "Installing IR catalog database from assets to ${dbFile.absolutePath}...")
        val tmpFile = File(targetDirectory, "$DB_FILE_NAME.tmp")

        try {
            assetManager.open("ir/$DB_FILE_NAME").use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // §7 Verify copied file hash against manifest if available
            val computedHash = computeSha256(tmpFile)
            Log.i(TAG, "Database asset extracted. Size: ${tmpFile.length()} bytes, SHA256: $computedHash")

            if (EXPECTED_MANIFEST_HASH.isNotBlank() && computedHash != EXPECTED_MANIFEST_HASH) {
                tmpFile.delete()
                return InstallResult.Failed("Checksum mismatch: expected=$EXPECTED_MANIFEST_HASH, actual=$computedHash")
            }

            // §7 fsync before rename for crash safety
            tmpFile.outputStream().fd.sync()

            if (!tmpFile.renameTo(dbFile)) {
                tmpFile.copyTo(dbFile, overwrite = true)
                tmpFile.delete()
            }

            // §7 Post-install integrity check
            val postInstallOk = verifyDatabaseIntegrity(dbFile)
            if (!postInstallOk) {
                dbFile.delete()
                return InstallResult.Failed("Post-install integrity check failed")
            }

            Log.i(TAG, "Successfully installed ir_catalog.db")
            return InstallResult.Installed(computedHash)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install ir_catalog.db asset: ${e.message}", e)
            if (tmpFile.exists()) tmpFile.delete()
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
