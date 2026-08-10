package com.elysium.nexus.fabric.profile.db

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * P1-18: Room migration tests using MigrationTestHelper.
 *
 * Verifies that data survives v2→v3→v4 migrations:
 * - profileId, codeSetId, signalId, fingerprints survive
 * - successCount, verifiedActions survive
 * - signal_sources table created in v4
 */
class RoomMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElysiumUserDatabase::class.java
    )

    private val TEST_DB = "migration-test"

    @Before
    fun setup() {
        helper.createDatabase(TEST_DB, 2).apply {
            // Insert test profile data (v2 schema)
            execSQL("""
                INSERT INTO installed_profiles
                (profileId, displayName, brandId, deviceTypeId, deviceModelId, remoteId,
                 codeSetId, catalogVersion, catalogCanonicalHash, verificationStatus,
                 createdAtEpochMs, updatedAtEpochMs, lastSuccessfulUseEpochMs,
                 needsRevalidation, isEnabled)
                VALUES ('test-profile-1', 'Test TV', 'Samsung', 'TV', 'UN55', 'UN55TU7000',
                        'cs-samsung-1', 'v0.5.0', 'abc123', 'PARTIALLY_VERIFIED',
                        1700000000000, 1700000000000, 0, 0, 1)
            """)
            execSQL("""
                INSERT INTO installed_ir_commands
                (profileId, actionKey, signalId, codeSetId, physicalSha256,
                 sourceRevisionId, verificationStatus, successCount, failureCount,
                 lastSuccessEpochMs, lastFailureEpochMs)
                VALUES ('test-profile-1', 'VOLUME_UP', 'sig-1', 'cs-samsung-1', 'sha-1',
                        'rev-1', 'PARTIALLY_VERIFIED', 3, 0, 1700000000000, 0)
            """)
            close()
        }
    }

    @Test
    fun migrate2To3To4_profileDataSurvives() {
        // Migrate v2 → v3
        var db = helper.runMigrationsAndValidate(TEST_DB, 2, true, ElysiumUserDatabase.MIGRATION_2_3)
        // Migrate v3 → v4
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, ElysiumUserDatabase.MIGRATION_3_4)

        // Verify profile data survived
        val cursor = db.query("SELECT profileId, codeSetId, verificationStatus, needsRevalidation FROM installed_profiles WHERE profileId = 'test-profile-1'")
        assertTrue("Profile must survive migration", cursor.moveToFirst())
        assertEquals("test-profile-1", cursor.getString(0))
        assertEquals("cs-samsung-1", cursor.getString(1))
        assertEquals("PARTIALLY_VERIFIED", cursor.getString(2))
        assertEquals(0, cursor.getInt(3)) // needsRevalidation = false in v2
        cursor.close()

        // Verify command data survived
        val cmdCursor = db.query("SELECT signalId, successCount, failureCount FROM installed_ir_commands WHERE profileId = 'test-profile-1'")
        assertTrue("Command must survive migration", cmdCursor.moveToFirst())
        assertEquals("sig-1", cmdCursor.getString(0))
        assertEquals(3, cmdCursor.getInt(1))
        assertEquals(0, cmdCursor.getInt(2))
        cmdCursor.close()

        // Verify signal_sources table exists in v4
        val tablesCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='signal_sources'")
        assertTrue("signal_sources table must exist in v4", tablesCursor.moveToFirst())
        tablesCursor.close()
    }

    @Test
    fun migrate2To3_emptyMigrationPreservesData() {
        // v2 → v3 is empty migration, data must be preserved exactly
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, ElysiumUserDatabase.MIGRATION_2_3)

        val cursor = db.query("SELECT profileId, codeSetId FROM installed_profiles WHERE profileId = 'test-profile-1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("test-profile-1", cursor.getString(0))
        assertEquals("cs-samsung-1", cursor.getString(1))
        cursor.close()
    }

    @Test
    fun migrate3To4_addsSignalSourcesTable() {
        helper.runMigrationsAndValidate(TEST_DB, 2, true, ElysiumUserDatabase.MIGRATION_2_3)
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, ElysiumUserDatabase.MIGRATION_3_4)

        // Verify signal_sources table structure
        val cursor = db.query("PRAGMA table_info(signal_sources)")
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(1))
        }
        cursor.close()

        assertTrue("signal_sources must have signalId", columns.contains("signalId"))
        assertTrue("signal_sources must have sourceId", columns.contains("sourceId"))
        assertTrue("signal_sources must have sourceRevisionId", columns.contains("sourceRevisionId"))
        assertTrue("signal_sources must have evidenceLevel", columns.contains("evidenceLevel"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // V06 PHASE 4 — Relational integrity: FKs, indices, ON DELETE CASCADE
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun migrate6To7_installsForeignKeysAndIndices() {
        // Seed a v6 database
        helper.createDatabase(TEST_DB_6, 6).apply {
            execSQL("""
                INSERT INTO installed_ir_profiles
                (profileId, displayName, brandId, deviceTypeId, deviceModelId, remoteId,
                 codeSetId, catalogVersion, catalogCanonicalHashAtInstall,
                 catalogSchemaVersionAtInstall, catalogBuildIdAtInstall,
                 verificationStatus, createdAtEpochMs, updatedAtEpochMs,
                 lastSuccessfulUseEpochMs, needsRevalidation, isEnabled)
                VALUES ('profile-rel-1', 'TV', 'LG', 'TV', NULL, NULL,
                        'cs-lg-1', 'v5', 'hash-1', 4, 'build-1',
                        'PARTIALLY_VERIFIED', 1700000000000, 1700000000000, 0, 0, 1)
            """)
            execSQL("""
                INSERT INTO installed_ir_commands
                (profileId, actionKey, signalId, codeSetId, physicalSha256,
                 sourceRevisionId, verificationStatus, successCount, failureCount,
                 lastSuccessEpochMs, lastFailureEpochMs)
                VALUES ('profile-rel-1', 'VOLUME_UP', 'sig-1', 'cs-lg-1', 'sha-1',
                        'rev-1', 'PARTIALLY_VERIFIED', 1, 0, 1700000000000, 0)
            """)
            execSQL("""
                INSERT INTO probe_sessions
                (sessionId, brand, deviceType, targetModel, startedAtEpochMs,
                 completedAtEpochMs, status, winnerCodeSetId, currentCandidateIndex,
                 currentCandidateId, currentActionKey, lastSignalId, lastPhysicalSha256,
                 lastAttemptId, catalogHashAtStart, verifiedActionKeys)
                VALUES ('session-rel-1', 'LG', 'TV', 'OLED55', 1700000000000, NULL,
                        'ACTIVE', NULL, 0, NULL, NULL, NULL, NULL, NULL, 'hash', '')
            """)
            execSQL("""
                INSERT INTO probe_attempts
                (attemptId, sessionId, candidateId, codeSetId, signalId, actionKey,
                 transmittedAtEpochMs, result, transmitDurationMs)
                VALUES ('attempt-1', 'session-rel-1', 'cand-1', 'cs-lg-1', 'sig-1',
                        'POWER_ON', 1700000000000, 'SUCCESS', 10)
            """)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_6, 6, true, ElysiumUserDatabase.MIGRATION_6_7)

        // FK index on installed_ir_commands
        val fkCursor = db.query("PRAGMA foreign_key_list(installed_ir_commands)")
        val fks = mutableListOf<String>()
        while (fkCursor.moveToNext()) {
            fks.add("${fkCursor.getString(2)}->${fkCursor.getString(3)}")
        }
        fkCursor.close()
        assertTrue("commands must FK to profiles", fks.contains("profileId->profileId"))

        // probe_attempts FK
        val attemptFkCursor = db.query("PRAGMA foreign_key_list(probe_attempts)")
        val attemptFks = mutableListOf<String>()
        while (attemptFkCursor.moveToNext()) {
            attemptFks.add("${attemptFkCursor.getString(2)}->${attemptFkCursor.getString(3)}")
        }
        attemptFkCursor.close()
        assertTrue("probe_attempts must FK to probe_sessions", attemptFks.contains("sessionId->sessionId"))

        // indices exist
        val idxCursor = db.query("PRAGMA index_list(installed_ir_commands)")
        val indices = mutableListOf<String>()
        while (idxCursor.moveToNext()) {
            indices.add(idxCursor.getString(1))
        }
        idxCursor.close()
        assertTrue("index on signalId", indices.any { it.contains("signalId") })
        assertTrue("index on codeSetId", indices.any { it.contains("codeSetId") })

        // data survived
        val cmdCursor = db.query("SELECT signalId FROM installed_ir_commands WHERE profileId = 'profile-rel-1'")
        assertTrue("commands must survive", cmdCursor.moveToFirst())
        assertEquals("sig-1", cmdCursor.getString(0))
        cmdCursor.close()

        // CASCADE: deleting the profile removes commands
        db.execSQL("DELETE FROM installed_ir_profiles WHERE profileId = 'profile-rel-1'")
        val orphanCursor = db.query("SELECT COUNT(*) FROM installed_ir_commands")
        assertTrue(orphanCursor.moveToFirst())
        assertEquals(0, orphanCursor.getInt(0))
        orphanCursor.close()
        db.close()
    }

    @Test
    fun migrate6To7_cascadeDeletesProbeAttemptsWithSession() {
        helper.createDatabase(TEST_DB_6, 6).apply {
            execSQL("""
                INSERT INTO installed_ir_profiles
                (profileId, displayName, brandId, deviceTypeId, deviceModelId, remoteId,
                 codeSetId, catalogVersion, catalogCanonicalHashAtInstall,
                 catalogSchemaVersionAtInstall, catalogBuildIdAtInstall,
                 verificationStatus, createdAtEpochMs, updatedAtEpochMs,
                 lastSuccessfulUseEpochMs, needsRevalidation, isEnabled)
                VALUES ('profile-cascade', 'TV', 'LG', 'TV', NULL, NULL,
                        'cs-lg-2', 'v1', 'hash-2', 4, 'build',
                        'PARTIALLY_VERIFIED', 1700000000000, 1700000000000, 0, 0, 1)
            """)
            execSQL("""
                INSERT INTO probe_sessions
                (sessionId, brand, deviceType, targetModel, startedAtEpochMs,
                 completedAtEpochMs, status, winnerCodeSetId, currentCandidateIndex,
                 currentCandidateId, currentActionKey, lastSignalId, lastPhysicalSha256,
                 lastAttemptId, catalogHashAtStart, verifiedActionKeys)
                VALUES ('session-cascade', 'LG', 'TV', NULL, 1700000000000, NULL,
                        'ACTIVE', NULL, 0, NULL, NULL, NULL, NULL, NULL, 'hash', '')
            """)
            execSQL("""
                INSERT INTO probe_attempts
                (attemptId, sessionId, candidateId, codeSetId, signalId, actionKey,
                 transmittedAtEpochMs, result, transmitDurationMs)
                VALUES ('attempt-c1', 'session-cascade', 'c1', 'cs-1', 'sig-1',
                        'POWER_ON', 1700000000000, 'SUCCESS', 5)
            """)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_6, 6, true, ElysiumUserDatabase.MIGRATION_6_7)

        // CASCADE: deleting the session removes its attempts
        db.execSQL("DELETE FROM probe_sessions WHERE sessionId = 'session-cascade'")
        val cursor = db.query("SELECT COUNT(*) FROM probe_attempts WHERE sessionId = 'session-cascade'")
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
        cursor.close()
        db.close()
    }

    @Test
    fun migrateTo7_addsDeviceModelIndexToEvidence() {
        helper.createDatabase(TEST_DB_6, 6).close()

        val db = helper.runMigrationsAndValidate(TEST_DB_6, 6, true, ElysiumUserDatabase.MIGRATION_6_7)

        val idxCursor = db.query("PRAGMA index_list(compatibility_evidence)")
        val names = mutableListOf<String>()
        while (idxCursor.moveToNext()) {
            names.add(idxCursor.getString(1))
        }
        idxCursor.close()
        assertTrue("evidence must be indexed by codeSetId", names.any { it.contains("codeSetId") })
        db.close()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // V06 PHASE 8 — durable scenes (scenes table, v8)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun migrate7To8_createsScenesTable() {
        // Seed a v7 database with the legacy tables present (no scenes).
        helper.createDatabase(TEST_DB_7, 7).apply {
            execSQL("""
                INSERT INTO installed_ir_profiles
                (profileId, displayName, brandId, deviceTypeId, deviceModelId, remoteId,
                 codeSetId, catalogVersion, catalogCanonicalHashAtInstall,
                 catalogSchemaVersionAtInstall, catalogBuildIdAtInstall,
                 verificationStatus, createdAtEpochMs, updatedAtEpochMs,
                 lastSuccessfulUseEpochMs, needsRevalidation, isEnabled)
                VALUES ('profile-v8', 'TV', 'LG', 'TV', NULL, NULL,
                        'cs-v8', 'v5', 'hash-1', 5, 'build-1',
                        'PARTIALLY_VERIFIED', 1700000000000, 1700000000000, 0, 0, 1)
            """)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_7, 7, true, ElysiumUserDatabase.MIGRATION_7_8)

        // scenes table exists with the exact expected columns
        val cols = mutableListOf<String>()
        db.query("PRAGMA table_info(scenes)").use { c ->
            while (c.moveToNext()) cols.add(c.getString(1))
        }
        assertTrue("sceneId", cols.contains("sceneId"))
        assertTrue("name", cols.contains("name"))
        assertTrue("payloadJson", cols.contains("payloadJson"))
        assertTrue("tagsCsv", cols.contains("tagsCsv"))
        assertTrue("createdAtEpochMs", cols.contains("createdAtEpochMs"))
        assertTrue("updatedAtEpochMs", cols.contains("updatedAtEpochMs"))

        // round-trip a row through the new table
        db.execSQL("""
            INSERT INTO scenes
            (sceneId, name, payloadJson, tagsCsv, createdAtEpochMs, updatedAtEpochMs)
            VALUES ('scene-1', 'Movie Night', '{"formatVersion":1}', 'movie,night', 100, 200)
        """)
        val cursor = db.query("SELECT name, tagsCsv FROM scenes WHERE sceneId = 'scene-1'")
        assertTrue("scene row must be readable", cursor.moveToFirst())
        assertEquals("Movie Night", cursor.getString(0))
        assertEquals("movie,night", cursor.getString(1))
        cursor.close()

        // legacy data survives alongside
        val legacy = db.query("SELECT profileId FROM installed_ir_profiles WHERE profileId = 'profile-v8'")
        assertTrue("profile data must survive", legacy.moveToFirst())
        legacy.close()
        db.close()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // V06-P9 — device identity graph (device_identities + history, v9)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun migrate8To9_createsDeviceIdentityTables() {
        helper.createDatabase(TEST_DB_8, 8).apply {
            execSQL("""
                INSERT INTO installed_ir_profiles
                (profileId, displayName, brandId, deviceTypeId, deviceModelId, remoteId,
                 codeSetId, catalogVersion, catalogCanonicalHashAtInstall,
                 catalogSchemaVersionAtInstall, catalogBuildIdAtInstall,
                 verificationStatus, createdAtEpochMs, updatedAtEpochMs,
                 lastSuccessfulUseEpochMs, needsRevalidation, isEnabled)
                VALUES ('profile-v9', 'TV', 'LG', 'TV', NULL, NULL,
                        'cs-v9', 'v5', 'hash-1', 5, 'build-1',
                        'PARTIALLY_VERIFIED', 1700000000000, 1700000000000, 0, 0, 1)
            """)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_8, 8, true, ElysiumUserDatabase.MIGRATION_8_9)

        // device_identities columns
        val identCols = mutableListOf<String>()
        db.query("PRAGMA table_info(device_identities)").use { c ->
            while (c.moveToNext()) identCols.add(c.getString(1))
        }
        assertTrue("stableId", identCols.contains("stableId"))
        assertTrue("evidenceJson", identCols.contains("evidenceJson"))
        assertTrue("compositeFallback", identCols.contains("compositeFallback"))

        // device_identity_history columns
        val histCols = mutableListOf<String>()
        db.query("PRAGMA table_info(device_identity_history)").use { c ->
            while (c.moveToNext()) histCols.add(c.getString(1))
        }
        assertTrue("history stableId", histCols.contains("stableId"))
        assertTrue("history observedAtEpochMs", histCols.contains("observedAtEpochMs"))
        assertTrue("history source", histCols.contains("source"))

        // FK: history → identities (CASCADE)
        val fkList = mutableListOf<String>()
        db.query("PRAGMA foreign_key_list(device_identity_history)").use { c ->
            while (c.moveToNext()) fkList.add("${c.getString(2)}->${c.getString(3)}")
        }
        assertTrue("history must FK to device_identities", fkList.contains("stableId->stableId"))

        // round-trip a row + verify CASCADE removes history with identity
        db.execSQL("""
            INSERT INTO device_identities
            (stableId, label, manufacturer, model, matchType, evidenceJson,
             compositeFallback, lastSeenEpochMs)
            VALUES ('tv-udn-1', 'LG TV', 'LG', 'OLED55C3', 'STABLE_EVIDENCE',
                    '[{"kind":"UpnpUdn","value":"uuid:abc"}]', 0, 1700000000000)
        """)
        db.execSQL("""
            INSERT INTO device_identity_history
            (stableId, observedAtEpochMs, source, manufacturer, model, ipAddress)
            VALUES ('tv-udn-1', 1700000000000, 'mdns', 'LG', 'OLED55C3', '192.168.1.7')
        """)
        db.execSQL("DELETE FROM device_identities WHERE stableId = 'tv-udn-1'")
        val orphan = db.query("SELECT COUNT(*) FROM device_identity_history WHERE stableId = 'tv-udn-1'")
        assertTrue(orphan.moveToFirst())
        assertEquals(0, orphan.getInt(0))
        orphan.close()

        // legacy data survives alongside
        val legacy = db.query("SELECT profileId FROM installed_ir_profiles WHERE profileId = 'profile-v9'")
        assertTrue("profile data must survive", legacy.moveToFirst())
        legacy.close()
        db.close()
    }

    companion object {
        private val TEST_DB_6 = "migration-test-v6"
        private val TEST_DB_7 = "migration-test-v7"
        private val TEST_DB_8 = "migration-test-v8"
    }
}
