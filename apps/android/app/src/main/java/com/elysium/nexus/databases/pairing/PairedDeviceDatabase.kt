package com.elysium.nexus.databases.pairing

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PairedDeviceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PairedDeviceDatabase : RoomDatabase() {

    abstract fun pairedDeviceDao(): PairedDeviceDao

    companion object {
        @Volatile
        private var INSTANCE: PairedDeviceDatabase? = null

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
                .fallbackToDestructiveMigration()
                .build()
    }
}
