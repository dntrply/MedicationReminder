package com.medreminder.app.ui

import com.medreminder.app.data.Medication
import com.medreminder.app.data.MedicationHistory
import org.json.JSONArray
import java.util.*

/**
 * Helper to calculate which medications were scheduled but not taken (missed doses)
 */
object MissedDoseCalculator {

    /**
     * Calculate missed doses for a given date range
     * Returns list of virtual MedicationHistory entries with action="MISSED"
     */
    fun calculateMissedDoses(
        medications: List<Medication>,
        existingHistory: List<MedicationHistory>,
        startTime: Long,
        endTime: Long
    ): List<MedicationHistory> {
        val missedDoses = mutableListOf<MedicationHistory>()

        // Get current time to avoid marking future doses as missed
        val now = System.currentTimeMillis()

        // For each day in the range
        val startCal = Calendar.getInstance().apply { timeInMillis = startTime }
        val endCal = Calendar.getInstance().apply { timeInMillis = endTime }

        while (startCal.timeInMillis <= endCal.timeInMillis) {
            val currentDay = startCal.get(Calendar.DAY_OF_YEAR)
            val currentYear = startCal.get(Calendar.YEAR)
            val currentDayOfWeek = startCal.get(Calendar.DAY_OF_WEEK)

            // For each medication
            for (medication in medications) {
                if (medication.reminderTimesJson == null) continue

                // Parse scheduled times for this day
                val scheduledTimes = parseReminderTimesForDay(
                    medication.reminderTimesJson,
                    currentDayOfWeek
                )

                // Check each scheduled time
                for ((hour, minute) in scheduledTimes) {
                    // Create the scheduled timestamp for this specific day
                    val scheduledCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, currentYear)
                        set(Calendar.DAY_OF_YEAR, currentDay)
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val scheduledTimestamp = scheduledCal.timeInMillis

                    // Skip if this is in the future (not yet time to take it)
                    if (scheduledTimestamp > now) continue

                    // Check if there's a history entry for this medication at this time
                    val hasHistoryEntry = existingHistory.any { history ->
                        history.medicationId == medication.id &&
                        isSameScheduledTime(history.scheduledTime, scheduledTimestamp)
                    }

                    // If no history entry, this is a missed dose
                    if (!hasHistoryEntry) {
                        missedDoses.add(
                            MedicationHistory(
                                id = 0, // Virtual entry, not persisted
                                medicationId = medication.id,
                                medicationName = medication.name,
                                scheduledTime = scheduledTimestamp,
                                takenTime = scheduledTimestamp, // Same as scheduled for missed
                                wasOnTime = false,
                                action = "MISSED"
                            )
                        )
                    }
                }
            }

            // Move to next day
            startCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return missedDoses
    }

    /**
     * Parse reminder times from JSON for a specific day of week
     * Returns list of (hour, minute) pairs
     */
    private fun parseReminderTimesForDay(
        reminderTimesJson: String,
        dayOfWeek: Int
    ): List<Pair<Int, Int>> {
        val times = mutableListOf<Pair<Int, Int>>()

        try {
            val jsonArray = JSONArray(reminderTimesJson)
            for (i in 0 until jsonArray.length()) {
                val timeObj = jsonArray.getJSONObject(i)
                val hour = timeObj.getInt("hour")
                val minute = timeObj.getInt("minute")

                // Check if this time applies to the current day
                val daysArray = timeObj.getJSONArray("days")
                val daysOfWeek = mutableListOf<Int>()
                for (j in 0 until daysArray.length()) {
                    daysOfWeek.add(daysArray.getInt(j))
                }

                // Convert Calendar day (1=Sunday, 2=Monday...) to match days array format
                // Assuming days array uses: 0=Sunday, 1=Monday, ..., 6=Saturday
                val calendarDayIndex = when (dayOfWeek) {
                    Calendar.SUNDAY -> 0
                    Calendar.MONDAY -> 1
                    Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 3
                    Calendar.THURSDAY -> 4
                    Calendar.FRIDAY -> 5
                    Calendar.SATURDAY -> 6
                    else -> -1
                }

                if (calendarDayIndex in daysOfWeek) {
                    times.add(hour to minute)
                }
            }
        } catch (e: Exception) {
            // If parsing fails, return empty list
        }

        return times
    }

    /**
     * Check if two timestamps represent the same scheduled time
     * (same day, same hour, same minute)
     */
    private fun isSameScheduledTime(time1: Long, time2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
               cal1.get(Calendar.HOUR_OF_DAY) == cal2.get(Calendar.HOUR_OF_DAY) &&
               cal1.get(Calendar.MINUTE) == cal2.get(Calendar.MINUTE)
    }
}
