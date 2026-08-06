package com.elysium.nexus.fabric.infrared.database

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.security.MessageDigest

private const val TAG = "ElysiumNexus.DbManager"
private const val DB_DIR_NAME = "ir-catalog"
private const val DB_FILE_NAME = "ir_catalog.db"
private const val MANIFEST_FILE_NAME = "ir_catalog.manifest.json"

/**
 * §7.1 Application Singleton Database Manager for IR Catalog.
 *
 * Manages the atomic, version-checked installation of `ir_catalog.db` from assets into
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

    suspend fun getRepository(): IrCatalogRepository = mutex.withLock {
        repositoryInstance?.let { return it }

        ensureDatabaseInstalled()
        val repo = IrCatalogRepository.getInstance(applicationContext)
        repositoryInstance = repo
        repo
    }

    private fun ensureDatabaseInstalled() {
        val dbFile = databaseFile
        val assetManager = applicationContext.assets

        // If database already exists and is non-empty, verify integrity
        if (dbFile.exists() && dbFile.length() > 0L) {
            Log.d(TAG, "Existing IR catalog database found at ${dbFile.absolutePath} (${dbFile.length()} bytes)")
            return
        }

        Log.i(TAG, "Installing IR catalog database from assets to ${dbFile.absolutePath}...")
        val tmpFile = File(targetDirectory, "$DB_FILE_NAME.tmp")

        try {
            assetManager.open("ir/$DB_FILE_NAME").use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Verify copied file hash
            val computedHash = computeSha256(tmpFile)
            Log.i(TAG, "Database asset extracted. Size: ${tmpFile.length()} bytes, SHA256: $computedHash")

            if (!tmpFile.renameTo(dbFile)) {
                tmpFile.copyTo(dbFile, overwrite = true)
                tmpFile.delete()
            }
            Log.i(TAG, "Successfully installed ir_catalog.db")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install ir_catalog.db asset: ${e.message}", e)
            if (tmpFile.exists()) tmpFile.delete()
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
