package com.medreminder.app

/**
 * Constants used across test files.
 * Centralizing these values makes tests more maintainable.
 */
object TestConstants {

    // ============================================
    // Test IDs
    // ============================================

    const val TEST_PROFILE_ID = 1L
    const val TEST_MEDICATION_ID = 100L
    const val TEST_HISTORY_ID = 1000L

    // ============================================
    // Test Names & Text
    // ============================================

    const val TEST_PROFILE_NAME = "Test Profile"
    const val TEST_MEDICATION_NAME = "Test Medication"
    const val TEST_MEDICATION_DOSAGE = "1 tablet"
    const val TEST_MEDICATION_NOTES = "Take with food"

    // Common medication names for variety in tests
    const val MEDICATION_ASPIRIN = "Aspirin"
    const val MEDICATION_INSULIN = "Insulin"
    const val MEDICATION_VITAMIN_D = "Vitamin D"
    const val MEDICATION_BLOOD_PRESSURE = "Blood Pressure Med"

    // ============================================
    // Time Constants
    // ============================================

    // Common reminder times (hour in 24-hour format)
    const val MORNING_HOUR = 8
    const val MORNING_MINUTE = 0

    const val NOON_HOUR = 12
    const val NOON_MINUTE = 0

    const val EVENING_HOUR = 18
    const val EVENING_MINUTE = 0

    const val NIGHT_HOUR = 21
    const val NIGHT_MINUTE = 0

    // Time windows and tolerances
    const val ONE_MINUTE_MS = 60_000L
    const val FIVE_MINUTES_MS = 5 * ONE_MINUTE_MS
    const val ONE_HOUR_MS = 60 * ONE_MINUTE_MS
    const val ONE_DAY_MS = 24 * ONE_HOUR_MS
    const val ONE_WEEK_MS = 7 * ONE_DAY_MS

    // Default tolerance for "on time" medication taking (30 minutes)
    const val ON_TIME_TOLERANCE_MS = 30 * ONE_MINUTE_MS

    // ============================================
    // Days of Week (0-6 format: Sunday-Saturday)
    // ============================================

    const val SUNDAY = 0
    const val MONDAY = 1
    const val TUESDAY = 2
    const val WEDNESDAY = 3
    const val THURSDAY = 4
    const val FRIDAY = 5
    const val SATURDAY = 6

    // Common day sets
    val ALL_DAYS = setOf(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY)
    val WEEKDAYS = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
    val WEEKENDS = setOf(SATURDAY, SUNDAY)

    // ============================================
    // Action Types (for MedicationHistory)
    // ============================================

    const val ACTION_TAKEN = "TAKEN"
    const val ACTION_SKIPPED = "SKIPPED"
    const val ACTION_MISSED = "MISSED"

    // ============================================
    // Sample JSON Data
    // ============================================

    /**
     * Sample reminder times JSON for a medication taken 3 times daily.
     */
    const val SAMPLE_THREE_TIMES_DAILY_JSON = """
        [
          {"hour":8, "minute":0, "days":[0,1,2,3,4,5,6]},
          {"hour":14, "minute":0, "days":[0,1,2,3,4,5,6]},
          {"hour":20, "minute":0, "days":[0,1,2,3,4,5,6]}
        ]
    """

    /**
     * Sample reminder times JSON for a medication taken once daily (morning).
     */
    const val SAMPLE_ONCE_DAILY_MORNING_JSON = """
        [
          {"hour":8, "minute":0, "days":[0,1,2,3,4,5,6]}
        ]
    """

    /**
     * Sample reminder times JSON for a medication taken only on weekdays.
     */
    const val SAMPLE_WEEKDAYS_ONLY_JSON = """
        [
          {"hour":8, "minute":0, "days":[1,2,3,4,5]}
        ]
    """

    // ============================================
    // File Paths (for testing audio/photo features)
    // ============================================

    const val TEST_PHOTO_URI = "file:///test/photo.jpg"
    const val TEST_AUDIO_PATH = "/test/audio/note.m4a"

    // ============================================
    // Notification Templates
    // ============================================

    const val NOTIFICATION_TEMPLATE_DEFAULT = "Time to take {medicationName}"
    const val NOTIFICATION_TEMPLATE_WITH_PROFILE = "Time for {profileName} to take {medicationName}"
}
