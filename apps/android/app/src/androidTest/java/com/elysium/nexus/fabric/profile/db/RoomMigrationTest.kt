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
        ElysiumUserDatabase::class.java,
        listOf(ElysiumUserDatabase.DestructiveMigrationSuspender())
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
}
