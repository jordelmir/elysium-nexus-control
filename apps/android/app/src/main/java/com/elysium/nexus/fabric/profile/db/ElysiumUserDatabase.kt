package com.elysium.nexus.fabric.profile.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import android.content.Context

// ═══════════════════════════════════════════════════════════════════════════
// Installed Profile + Commands — §2 Authoritative persistent IR profile
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "installed_ir_profiles")
data class InstalledIrProfileEntity(
    @PrimaryKey val profileId: String,
    val displayName: String,
    val brandId: String,
    val deviceTypeId: String,
    val deviceModelId: String?,
    val remoteId: String?,
    val codeSetId: String,
    val catalogVersion: String,
    val catalogCanonicalHash: String,
    val verificationStatus: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastSuccessfulUseEpochMs: Long,
    val needsRevalidation: Boolean,
    val isEnabled: Boolean
)

@Entity(
    tableName = "installed_ir_commands",
    primaryKeys = ["profileId", "actionKey"]
)
data class InstalledIrCommandEntity(
    val profileId: String,
    val actionKey: String,
    val signalId: String,
    val codeSetId: String,
    val physicalSha256: String,
    val sourceRevisionId: String,
    val verificationStatus: String,
    val successCount: Int,
    val failureCount: Int,
    val lastSuccessEpochMs: Long,
    val lastFailureEpochMs: Long
)

// ═══════════════════════════════════════════════════════════════════════════
// Probe Session — §23 Tracks a probing session for a brand/device
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "probe_sessions")
data class ProbeSessionEntity(
    @PrimaryKey val sessionId: String,
    val brand: String,
    val deviceType: String,
    val targetModel: String?,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val status: String,
    val winnerCodeSetId: String?
)

// ═══════════════════════════════════════════════════════════════════════════
// Probe Attempt — §23 Individual probe attempt with attemptId
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "probe_attempts")
data class ProbeAttemptEntity(
    @PrimaryKey val attemptId: String,
    val sessionId: String,
    val candidateId: String,
    val codeSetId: String,
    val signalId: String,
    val actionKey: String,
    val transmittedAtEpochMs: Long,
    val result: String,
    val transmitDurationMs: Long
)

// ═══════════════════════════════════════════════════════════════════════════
// Compatibility Evidence — §23 Community/verified compatibility records
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "compatibility_evidence")
data class CompatibilityEvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codeSetId: String,
    val brand: String,
    val deviceType: String,
    val actionKey: String,
    val success: Boolean,
    val reportSource: String,
    val reportedAtEpochMs: Long,
    val notes: String?
)

// ═══════════════════════════════════════════════════════════════════════════
// Candidate Penalty — §23 Penalizes candidates that repeatedly fail
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "candidate_penalties")
data class CandidatePenaltyEntity(
    @PrimaryKey val codeSetId: String,
    val penaltyScore: Int,
    val failCount: Int,
    val lastFailEpochMs: Long,
    val reason: String
)

// ═══════════════════════════════════════════════════════════════════════════
// Catalog Migration Record — §14 Tracks catalog version transitions
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "catalog_migrations")
data class CatalogMigrationEntity(
    @PrimaryKey val migrationId: String,
    val fromVersion: String,
    val toVersion: String,
    val migratedAtEpochMs: Long,
    val profileCount: Int,
    val status: String
)

// ═══════════════════════════════════════════════════════════════════════════
// DAO — Complete CRUD for all entities
// ═══════════════════════════════════════════════════════════════════════════

@Dao
interface InstalledProfileDao {

    // ── Profiles ──────────────────────────────────────────────────────

    @Query("SELECT * FROM installed_ir_profiles WHERE isEnabled = 1 ORDER BY updatedAtEpochMs DESC")
    suspend fun getAllProfiles(): List<InstalledIrProfileEntity>

    @Query("SELECT * FROM installed_ir_profiles WHERE profileId = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: String): InstalledIrProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: InstalledIrProfileEntity)

    @Query("DELETE FROM installed_ir_profiles WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)

    // ── Commands ──────────────────────────────────────────────────────

    @Query("SELECT * FROM installed_ir_commands WHERE profileId = :profileId")
    suspend fun getCommandsForProfile(profileId: String): List<InstalledIrCommandEntity>

    @Query("SELECT * FROM installed_ir_commands WHERE profileId = :profileId AND actionKey = :actionKey LIMIT 1")
    suspend fun getCommandForAction(profileId: String, actionKey: String): InstalledIrCommandEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommands(commands: List<InstalledIrCommandEntity>)

    @Query("DELETE FROM installed_ir_commands WHERE profileId = :profileId")
    suspend fun deleteCommands(profileId: String)

    @Transaction
    suspend fun saveProfileWithCommands(profile: InstalledIrProfileEntity, commands: List<InstalledIrCommandEntity>) {
        insertProfile(profile)
        insertCommands(commands)
    }

    @Transaction
    suspend fun deleteProfileWithCommands(profileId: String) {
        deleteCommands(profileId)
        deleteProfile(profileId)
    }

    // ── Probe Sessions ────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProbeSession(session: ProbeSessionEntity)

    @Query("SELECT * FROM probe_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getProbeSession(sessionId: String): ProbeSessionEntity?

    @Query("UPDATE probe_sessions SET status = :status, completedAtEpochMs = :completedAtMs, winnerCodeSetId = :winnerCodeSetId WHERE sessionId = :sessionId")
    suspend fun completeProbeSession(sessionId: String, status: String, completedAtMs: Long, winnerCodeSetId: String?)

    // ── Probe Attempts ────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProbeAttempt(attempt: ProbeAttemptEntity)

    @Query("SELECT * FROM probe_attempts WHERE sessionId = :sessionId ORDER BY transmittedAtEpochMs DESC")
    suspend fun getAttemptsForSession(sessionId: String): List<ProbeAttemptEntity>

    @Query("SELECT * FROM probe_attempts WHERE sessionId = :sessionId AND actionKey = :actionKey AND result = 'SUCCESS' ORDER BY transmittedAtEpochMs DESC LIMIT 1")
    suspend fun getLastSuccessfulAttempt(sessionId: String, actionKey: String): ProbeAttemptEntity?

    // ── Compatibility Evidence ─────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: CompatibilityEvidenceEntity)

    @Query("SELECT * FROM compatibility_evidence WHERE codeSetId = :codeSetId AND actionKey = :actionKey AND success = 1")
    suspend fun getSuccessfulEvidence(codeSetId: String, actionKey: String): List<CompatibilityEvidenceEntity>

    // ── Candidate Penalties ───────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPenalty(penalty: CandidatePenaltyEntity)

    @Query("SELECT * FROM candidate_penalties WHERE codeSetId = :codeSetId LIMIT 1")
    suspend fun getPenalty(codeSetId: String): CandidatePenaltyEntity?

    @Query("SELECT * FROM candidate_penalties ORDER BY penaltyScore DESC LIMIT :limit")
    suspend fun getTopPenalties(limit: Int): List<CandidatePenaltyEntity>

    // ── Catalog Migrations ────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMigration(migration: CatalogMigrationEntity)

    @Query("SELECT * FROM catalog_migrations ORDER BY migratedAtEpochMs DESC LIMIT 1")
    suspend fun getLastMigration(): CatalogMigrationEntity?
}

// ═══════════════════════════════════════════════════════════════════════════
// Room Database — §2 Complete with all audit entities
// ═══════════════════════════════════════════════════════════════════════════

@Database(
    entities = [
        InstalledIrProfileEntity::class,
        InstalledIrCommandEntity::class,
        ProbeSessionEntity::class,
        ProbeAttemptEntity::class,
        CompatibilityEvidenceEntity::class,
        CandidatePenaltyEntity::class,
        CatalogMigrationEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class ElysiumUserDatabase : RoomDatabase() {
    abstract fun profileDao(): InstalledProfileDao

    companion object {
        @Volatile
        private var INSTANCE: ElysiumUserDatabase? = null

        // P0-12: Explicit migrations. No fallbackToDestructiveMigration.
        // v2→v3: empty migration (no schema change, but forces version bump for exportSchema)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // No schema changes — version bump to enable exportSchema tracking.
            }
        }

        fun getInstance(context: Context): ElysiumUserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ElysiumUserDatabase::class.java,
                    "elysium_user_database.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
