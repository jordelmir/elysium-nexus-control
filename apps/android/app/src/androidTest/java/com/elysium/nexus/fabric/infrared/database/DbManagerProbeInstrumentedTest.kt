package com.elysium.nexus.fabric.infrared.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * §7 Diagnostic probe for the on-device catalog install state (PTG-01 layout).
 *
 * Reports (via the transmit XML failure message, the only channel readable on
 * this device) the exact filesystem state and the
 * [IrCatalogDatabaseManager.InstallResult] so install failures can be
 * attributed precisely. PTG-01 layout: builds/<catalogBuildId>/ir_catalog.db +
 * current pointer. A fresh install MUST move the database INTO the build
 * directory (no root-level ir_catalog.db) and MUST clear the temp file.
 */
@RunWith(AndroidJUnit4::class)
class DbManagerProbeInstrumentedTest {

    @Test
    fun probeInstallState() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = IrCatalogDatabaseManager.getInstance(ctx)

        val noBackup = ctx.noBackupFilesDir
        val targetDir = File(noBackup, "ir-catalog")
        val buildsDir = File(targetDir, "builds")
        val currentFile = File(targetDir, "current")
        val tmpFile = File(targetDir, "ir_catalog.db.tmp")

        val sb = StringBuilder()
        sb.append("[P] noBackup=${noBackup.absolutePath} exists=${noBackup.exists()}\n")
        sb.append("[P] targetDir=${targetDir.absolutePath} exists=${targetDir.exists()}\n")
        sb.append("[P] buildsDir=${buildsDir.absolutePath} exists=${buildsDir.exists()}\n")
        sb.append("[P] currentFile=${currentFile.absolutePath} exists=${currentFile.exists()} content=${runCatching { currentFile.readText() }.getOrDefault("?")}\n")
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

        val activeDb = manager.databaseFile
        sb.append("[P] activeDb=${activeDb.absolutePath} exists=${activeDb.exists()} len=${if (activeDb.exists()) activeDb.length() else -1}\n")
        sb.append("[P] currentBuildId=${manager.currentBuildId()}\n")
        sb.append("[P] currentCatalogMetadata=${manager.currentCatalogMetadata()}\n")
        sb.append("[P] post-tmp exists=${tmpFile.exists()}\n")

        if (result is IrCatalogDatabaseManager.InstallResult.Failed) {
            throw AssertionError("install failed. $sb")
        }
        assertTrue("activeDb must exist after install. $sb", activeDb.exists())
        assertTrue("activeDb must be non-empty. $sb", activeDb.length() > 0L)
        assertTrue("current pointer must be set. $sb", manager.currentBuildId() != null)
        assertTrue("metadata must resolve for installed build. $sb", manager.currentCatalogMetadata() != null)
    }
}