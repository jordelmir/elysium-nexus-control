package com.elysium.nexus.fabric.infrared.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * §7 Diagnostic probe for the on-device catalog install state.
 *
 * Reports (via the transmit XML failure message, the only channel readable on
 * this device) the exact filesystem state and the
 * [IrCatalogDatabaseManager.InstallResult] so the "no such table ... (OS error
 * - 2)" failure can be attributed precisely. Conflicts with a naive file-open
 * at probe time are avoided by not opening SQLite here — we only inspect FS.
 */
@RunWith(AndroidJUnit4::class)
class DbManagerProbeInstrumentedTest {

    @Test
    fun probeInstallState() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = IrCatalogDatabaseManager.getInstance(ctx)

        val noBackup = ctx.noBackupFilesDir
        val targetDir = File(noBackup, "ir-catalog")
        val dbFile = File(targetDir, "ir_catalog.db")
        val tmpFile = File(targetDir, "ir_catalog.db.tmp")

        val sb = StringBuilder()
        sb.append("[P] noBackup=${noBackup.absolutePath} exists=${noBackup.exists()}\n")
        sb.append("[P] targetDir=${targetDir.absolutePath} exists=${targetDir.exists()}\n")
        sb.append("[P] dbFile=${dbFile.absolutePath} exists=${dbFile.exists()} len=${if (dbFile.exists()) dbFile.length() else -1}\n")
        sb.append("[P] tmpFile exists=${tmpFile.exists()} len=${if (tmpFile.exists()) tmpFile.length() else -1}\n")
        try {
            ctx.assets.open("ir/ir_catalog.db").use { input ->
                sb.append("[P] asset ok size=${input.available()}\n")
            }
        } catch (e: Exception) {
            sb.append("[P] asset OPEN FAILED: ${e.message}\n")
        }

        val result = manager.ensureDatabaseInstalled()
        sb.append("[P] result=${result.javaClass.simpleName}: $result\n")
        sb.append("[P] post-db exists=${dbFile.exists()} len=${if (dbFile.exists()) dbFile.length() else -1}\n")
        sb.append("[P] post-tmp exists=${tmpFile.exists()} len=${if (tmpFile.exists()) tmpFile.length() else -1}\n")

        if (result is IrCatalogDatabaseManager.InstallResult.Failed) {
            throw AssertionError("install failed. $sb")
        }
        assertTrue("dbFile must exist. $sb", dbFile.exists())
        assertTrue("dbFile must be non-empty. $sb", dbFile.length() > 0L)
    }
}