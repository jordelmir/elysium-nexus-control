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
import android.content.Context

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

@Dao
interface InstalledProfileDao {

    @Query("SELECT * FROM installed_ir_profiles WHERE isEnabled = 1 ORDER BY updatedAtEpochMs DESC")
    suspend fun getAllProfiles(): List<InstalledIrProfileEntity>

    @Query("SELECT * FROM installed_ir_profiles WHERE profileId = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: String): InstalledIrProfileEntity?

    @Query("SELECT * FROM installed_ir_commands WHERE profileId = :profileId")
    suspend fun getCommandsForProfile(profileId: String): List<InstalledIrCommandEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: InstalledIrProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommands(commands: List<InstalledIrCommandEntity>)

    @Transaction
    suspend fun saveProfileWithCommands(profile: InstalledIrProfileEntity, commands: List<InstalledIrCommandEntity>) {
        insertProfile(profile)
        insertCommands(commands)
    }

    @Query("DELETE FROM installed_ir_profiles WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Query("DELETE FROM installed_ir_commands WHERE profileId = :profileId")
    suspend fun deleteCommands(profileId: String)

    @Transaction
    suspend fun deleteProfileWithCommands(profileId: String) {
        deleteCommands(profileId)
        deleteProfile(profileId)
    }
}

@Database(
    entities = [
        InstalledIrProfileEntity::class,
        InstalledIrCommandEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ElysiumUserDatabase : RoomDatabase() {
    abstract fun profileDao(): InstalledProfileDao

    companion object {
        @Volatile
        private var INSTANCE: ElysiumUserDatabase? = null

        fun getInstance(context: Context): ElysiumUserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ElysiumUserDatabase::class.java,
                    "elysium_user_database.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
