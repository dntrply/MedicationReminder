package com.medreminder.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Medication::class, MedicationHistory::class, Profile::class],
    version = 6,
    exportSchema = false
)
abstract class MedicationDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun historyDao(): MedicationHistoryDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: MedicationDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create profiles table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `photoUri` TEXT,
                        `notificationSoundUri` TEXT,
                        `notificationMessageTemplate` TEXT,
                        `isDefault` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())

                // Insert default profile
                database.execSQL("""
                    INSERT INTO `profiles` (`name`, `isDefault`, `createdAt`)
                    VALUES ('Me', 1, ${System.currentTimeMillis()})
                """.trimIndent())

                // Add profileId column to medications table
                database.execSQL("""
                    ALTER TABLE `medications` ADD COLUMN `profileId` INTEGER NOT NULL DEFAULT 1
                """.trimIndent())

                // Create index on medications.profileId
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_medications_profileId` ON `medications` (`profileId`)
                """.trimIndent())

                // Add profileId column to medication_history table
                database.execSQL("""
                    ALTER TABLE `medication_history` ADD COLUMN `profileId` INTEGER NOT NULL DEFAULT 1
                """.trimIndent())

                // Create indexes on medication_history
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_medication_history_profileId` ON `medication_history` (`profileId`)
                """.trimIndent())
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_medication_history_medicationId` ON `medication_history` (`medicationId`)
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): MedicationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedicationDatabase::class.java,
                    "medication_database"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration() // For development - will recreate DB on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
