package com.medreminder.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Medication::class, MedicationHistory::class, Profile::class, TranscriptionStats::class],
    version = 8,
    exportSchema = false
)
abstract class MedicationDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun historyDao(): MedicationHistoryDao
    abstract fun profileDao(): ProfileDao
    abstract fun transcriptionStatsDao(): TranscriptionStatsDao

    companion object {
        @Volatile
        private var INSTANCE: MedicationDatabase? = null

        fun getDatabase(context: Context): MedicationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedicationDatabase::class.java,
                    "medication_database"
                )
                    .fallbackToDestructiveMigration() // For development - will recreate DB on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
