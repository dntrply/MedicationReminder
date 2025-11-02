package com.medreminder.app.utils

import android.content.Context
import com.medreminder.app.R

/**
 * Utility functions for formatting time differences in a human-readable way.
 * Used across the app for consistent time difference display.
 */
object TimeUtils {

    /**
     * Format a time difference in milliseconds to a human-readable string.
     *
     * @param diffMillis The time difference in milliseconds (can be positive for late, negative for early)
     * @param context Android context for accessing string resources
     * @param isLate Whether this represents lateness (true) or earliness (false)
     * @return Formatted string like "15 minutes late" or "1 hour 30 minutes early"
     */
    fun formatTimeDifference(diffMillis: Long, context: Context, isLate: Boolean = true): String {
        val absDiffMillis = Math.abs(diffMillis)
        val diffMinutes = (absDiffMillis / (1000 * 60)).toInt()

        return if (diffMinutes < 60) {
            // Less than an hour - show in minutes
            if (isLate) {
                context.getString(R.string.taken_late_minutes, diffMinutes)
            } else {
                context.getString(R.string.taken_early_minutes, diffMinutes)
            }
        } else {
            // An hour or more - show in hours and minutes
            val hours = diffMinutes / 60
            val minutes = diffMinutes % 60

            if (minutes == 0) {
                // Exact hours (e.g., "2 hours late")
                if (isLate) {
                    context.getString(R.string.taken_late_hours_only, hours)
                } else {
                    context.getString(R.string.taken_early_hours_only, hours)
                }
            } else {
                // Hours and minutes (e.g., "2 hours 30 minutes late")
                if (isLate) {
                    context.getString(R.string.taken_late_hours_minutes, hours, minutes)
                } else {
                    context.getString(R.string.taken_early_hours_minutes, hours, minutes)
                }
            }
        }
    }

    /**
     * Format lateness information for display.
     * Automatically determines if the medication was taken late or early based on the time difference.
     *
     * @param scheduledTime When the medication was scheduled (milliseconds)
     * @param takenTime When the medication was actually taken (milliseconds)
     * @param context Android context for accessing string resources
     * @param onTimeThresholdMinutes Threshold in minutes to consider "on time" (default: 5)
     * @return Formatted string or null if taken on time
     */
    fun formatLateness(
        scheduledTime: Long,
        takenTime: Long,
        context: Context,
        onTimeThresholdMinutes: Int = 5
    ): String? {
        val diffMillis = takenTime - scheduledTime
        val diffMinutes = Math.abs(diffMillis / (1000 * 60)).toInt()

        return when {
            // Taken on time (within threshold)
            diffMinutes <= onTimeThresholdMinutes -> null

            // Taken late
            diffMillis > 0 -> formatTimeDifference(diffMillis, context, isLate = true)

            // Taken early
            else -> formatTimeDifference(diffMillis, context, isLate = false)
        }
    }

    /**
     * Format time in 12-hour format with AM/PM.
     *
     * @param hour Hour in 24-hour format (0-23)
     * @param minute Minute (0-59)
     * @return Formatted time string like "02:30 PM"
     */
    fun formatTime12Hour(hour: Int, minute: Int): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%02d:%02d %s", displayHour, minute, amPm)
    }
}
