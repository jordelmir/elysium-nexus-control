package com.elysium.nexus.databases.pairing

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PairedDeviceEntity::class],
    version = 2,
    exportSchema = true
)
abstract class PairedDeviceDatabase : RoomDatabase() {

    abstract fun pairedDeviceDao(): PairedDeviceDao

    companion object {
        @Volatile
        private var INSTANCE: PairedDeviceDatabase? = null

        /** V06 §4: add stable_identity column + index. Never use IP as identity. */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE paired_devices ADD COLUMN stable_identity TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_paired_devices_stableIdentity ON paired_devices(stable_identity)")
            }
        }

        fun getInstance(context: Context): PairedDeviceDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        fun build(context: Context): PairedDeviceDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PairedDeviceDatabase::class.java,
                "paired_devices.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
