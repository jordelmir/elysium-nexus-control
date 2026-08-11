package com.elysium.nexus.fabric.infrared.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

/**
 * V06.3 Phase: Catalog signal integrity gate.
 *
 * Guards against the cmd/sub_device parameter swap bug where ALL parametric
 * signals had command_value=-1, causing NEC/SIRC/Samsung encoders to fail
 * with "command must be in [0, 255] (got -1)".
 *
 * Every PARAMETRIC signal must have a valid command_value in [0, 255].
 */
class CatalogSignalIntegrityTest {

    private fun findProjectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null && !File(dir, "ir-data/sources.lock.json").exists()) {
            dir = dir.parentFile
        }
        return dir ?: File(".")
    }

    @Test
    fun allParametricSignalsHaveValidCommandValues() {
        val rootDir = findProjectRoot()
        val dbFile = File(rootDir, "apps/android/app/src/main/assets/ir/ir_catalog.db")
        assertTrue("ir_catalog.db must exist", dbFile.exists())

        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        try {
            // Count parametric signals with invalid command_value
            val invalidCount = conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM signals WHERE encoding_type = 'PARAMETRIC' AND (command_value < 0 OR command_value > 255)"
                )
                rs.getInt(1)
            }

            assertEquals(
                "All PARAMETRIC signals must have command_value in [0, 255] " +
                    "(cmd/sub_device parameter swap regression)",
                0, invalidCount
            )
        } finally {
            conn.close()
        }
    }

    @Test
    fun volumeUpCandidatesExist() {
        val rootDir = findProjectRoot()
        val dbFile = File(rootDir, "apps/android/app/src/main/assets/ir/ir_catalog.db")
        assertTrue("ir_catalog.db must exist", dbFile.exists())

        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        try {
            val count = conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    """
                    SELECT COUNT(DISTINCT cs.id)
                    FROM code_sets cs
                    JOIN command_bindings cb ON cb.code_set_id = cs.id
                    JOIN actions a ON cb.action_id = a.id
                    JOIN signals sig ON cb.signal_id = sig.id
                    WHERE a.canonical_key = 'VOLUME_UP'
                      AND sig.command_value >= 0 AND sig.command_value <= 255
                    """
                )
                rs.getInt(1)
            }

            assertTrue(
                "Must have at least 100 valid VOLUME_UP candidates (got $count)",
                count >= 100
            )
        } finally {
            conn.close()
        }
    }

    @Test
    fun necProtocolSignalsHaveValidCommandValues() {
        val rootDir = findProjectRoot()
        val dbFile = File(rootDir, "apps/android/app/src/main/assets/ir/ir_catalog.db")
        assertTrue("ir_catalog.db must exist", dbFile.exists())

        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        try {
            val invalidCount = conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    """
                    SELECT COUNT(*) FROM signals sig
                    JOIN protocol_definitions pd ON sig.protocol_definition_id = pd.id
                    WHERE sig.encoding_type = 'PARAMETRIC'
                      AND pd.family_name = 'NEC'
                      AND (sig.command_value < 0 OR sig.command_value > 255)
                    """
                )
                rs.getInt(1)
            }

            assertEquals(
                "All NEC parametric signals must have valid command_value",
                0, invalidCount
            )
        } finally {
            conn.close()
        }
    }
}
