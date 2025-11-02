package com.medreminder.app

import com.medreminder.app.data.Medication
import com.medreminder.app.data.MedicationHistory
import com.medreminder.app.data.Profile
import com.medreminder.app.data.ReminderTime
import java.util.Calendar

/**
 * Test utility functions for creating test data and common test operations.
 * These helpers reduce boilerplate and make tests more readable.
 */
object TestUtils {

    // ============================================
    // Test Data Builders
    // ============================================

    /**
     * Creates a test Medication with sensible defaults.
     * All parameters are optional to allow customization.
     */
    fun createTestMedication(
        id: Long = 1L,
        profileId: Long = 1L,
        name: String = "Test Medication",
        photoUri: String? = null,
        audioNotePath: String? = null,
        audioTranscription: String? = null,
        audioTranscriptionLanguage: String? = null,
        dosage: String? = "1 tablet",
        notes: String? = null,
        reminderTimesJson: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ): Medication {
        return Medication(
            id = id,
            profileId = profileId,
            name = name,
            photoUri = photoUri,
            audioNotePath = audioNotePath,
            audioTranscription = audioTranscription,
            audioTranscriptionLanguage = audioTranscriptionLanguage,
            dosage = dosage,
            notes = notes,
            reminderTimesJson = reminderTimesJson,
            createdAt = createdAt
        )
    }

    /**
     * Creates a test Profile with sensible defaults.
     */
    fun createTestProfile(
        id: Long = 1L,
        name: String = "Test Profile",
        photoUri: String? = null,
        notificationSoundUri: String? = null,
        notificationMessageTemplate: String? = null,
        isDefault: Boolean = true,
        createdAt: Long = System.currentTimeMillis()
    ): Profile {
        return Profile(
            id = id,
            name = name,
            photoUri = photoUri,
            notificationSoundUri = notificationSoundUri,
            notificationMessageTemplate = notificationMessageTemplate,
            isDefault = isDefault,
            createdAt = createdAt
        )
    }

    /**
     * Creates a test MedicationHistory entry with sensible defaults.
     */
    fun createTestHistory(
        id: Long = 0L,
        profileId: Long = 1L,
        medicationId: Long = 1L,
        medicationName: String = "Test Medication",
        scheduledTime: Long = System.currentTimeMillis(),
        takenTime: Long = System.currentTimeMillis(),
        wasOnTime: Boolean = true,
        action: String = "TAKEN",
        notes: String? = null
    ): MedicationHistory {
        return MedicationHistory(
            id = id,
            profileId = profileId,
            medicationId = medicationId,
            medicationName = medicationName,
            scheduledTime = scheduledTime,
            takenTime = takenTime,
            wasOnTime = wasOnTime,
            action = action,
            notes = notes
        )
    }

    /**
     * Creates a ReminderTime for testing.
     */
    fun createReminderTime(
        hour: Int,
        minute: Int,
        daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7) // All days by default
    ): ReminderTime {
        return ReminderTime(hour = hour, minute = minute, daysOfWeek = daysOfWeek)
    }

    /**
     * Converts a list of ReminderTime objects to JSON string format
     * that matches the app's storage format.
     */
    fun reminderTimesToJson(reminderTimes: List<ReminderTime>): String {
        val jsonArray = reminderTimes.joinToString(",\n  ", "[\n  ", "\n]") { reminder ->
            val days = reminder.daysOfWeek.joinToString(", ")
            """{"hour":${reminder.hour}, "minute":${reminder.minute}, "days":[$days]}"""
        }
        return jsonArray
    }

    // ============================================
    // Calendar/Time Utilities
    // ============================================

    /**
     * Creates a Calendar instance set to a specific time today.
     * Useful for creating predictable timestamps in tests.
     */
    fun createCalendarToday(hour: Int, minute: Int, second: Int = 0): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }
    }

    /**
     * Creates a Calendar instance set to a specific date and time.
     */
    fun createCalendar(
        year: Int,
        month: Int, // Calendar.JANUARY, Calendar.FEBRUARY, etc.
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0
    ): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }
    }

    /**
     * Gets today's day of week in the app's 0-6 format (Sunday=0, Saturday=6).
     * This matches the format used in PendingMedicationTracker.
     */
    fun getTodayDayOfWeekIndex(): Int {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> -1
        }
    }

    /**
     * Adds days to a timestamp.
     */
    fun addDays(timestamp: Long, days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return calendar.timeInMillis
    }

    /**
     * Adds hours to a timestamp.
     */
    fun addHours(timestamp: Long, hours: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.add(Calendar.HOUR_OF_DAY, hours)
        return calendar.timeInMillis
    }

    /**
     * Adds minutes to a timestamp.
     */
    fun addMinutes(timestamp: Long, minutes: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.add(Calendar.MINUTE, minutes)
        return calendar.timeInMillis
    }

    // ============================================
    // Assertion Helpers
    // ============================================

    /**
     * Checks if two timestamps are within a certain tolerance (in milliseconds).
     * Useful for checking if times are "approximately equal".
     */
    fun assertTimestampsClose(
        expected: Long,
        actual: Long,
        toleranceMs: Long = 1000L
    ): Boolean {
        return Math.abs(expected - actual) <= toleranceMs
    }

    /**
     * Formats a timestamp for easier debugging in test output.
     */
    fun formatTimestamp(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return String.format(
            "%04d-%02d-%02d %02d:%02d:%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
    }
}
