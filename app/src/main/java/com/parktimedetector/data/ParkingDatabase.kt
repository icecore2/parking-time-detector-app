package com.parktimedetector.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ParkingSession::class, LogEntry::class], version = 5, exportSchema = false)
abstract class ParkingDatabase : RoomDatabase() {
    abstract fun parkingDao(): ParkingDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: ParkingDatabase? = null

        fun getDatabase(context: Context): ParkingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ParkingDatabase::class.java,
                    "parking_time_detector_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
