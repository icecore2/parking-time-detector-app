package com.parktimedetector.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ParkingSession::class, LogEntry::class], version = 7, exportSchema = false)
abstract class ParkingDatabase : RoomDatabase() {
    abstract fun parkingDao(): ParkingDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: ParkingDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parking_sessions ADD COLUMN spotDetails TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE parking_sessions ADD COLUMN parkedLatitude REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE parking_sessions ADD COLUMN parkedLongitude REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE parking_sessions ADD COLUMN walkingBufferMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE parking_sessions ADD COLUMN isNotifiedWalkBuffer INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parking_sessions ADD COLUMN calendarEventId INTEGER DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): ParkingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ParkingDatabase::class.java,
                    "parking_time_detector_db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
