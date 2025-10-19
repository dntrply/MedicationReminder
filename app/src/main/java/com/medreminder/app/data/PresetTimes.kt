package com.medreminder.app.data

import android.content.Context

data class PresetTimes(
    val morningHour: Int = 8,
    val morningMinute: Int = 0,
    val lunchHour: Int = 12,
    val lunchMinute: Int = 0,
    val eveningHour: Int = 18,
    val eveningMinute: Int = 0,
    val bedtimeHour: Int = 22,
    val bedtimeMinute: Int = 0
)

object PresetTimesManager {
    private const val PREFS_NAME = "preset_times"
    private const val KEY_MORNING_HOUR = "morning_hour"
    private const val KEY_MORNING_MINUTE = "morning_minute"
    private const val KEY_LUNCH_HOUR = "lunch_hour"
    private const val KEY_LUNCH_MINUTE = "lunch_minute"
    private const val KEY_EVENING_HOUR = "evening_hour"
    private const val KEY_EVENING_MINUTE = "evening_minute"
    private const val KEY_BEDTIME_HOUR = "bedtime_hour"
    private const val KEY_BEDTIME_MINUTE = "bedtime_minute"

    fun getPresetTimes(context: Context): PresetTimes {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PresetTimes(
            morningHour = prefs.getInt(KEY_MORNING_HOUR, 8),
            morningMinute = prefs.getInt(KEY_MORNING_MINUTE, 0),
            lunchHour = prefs.getInt(KEY_LUNCH_HOUR, 12),
            lunchMinute = prefs.getInt(KEY_LUNCH_MINUTE, 0),
            eveningHour = prefs.getInt(KEY_EVENING_HOUR, 18),
            eveningMinute = prefs.getInt(KEY_EVENING_MINUTE, 0),
            bedtimeHour = prefs.getInt(KEY_BEDTIME_HOUR, 22),
            bedtimeMinute = prefs.getInt(KEY_BEDTIME_MINUTE, 0)
        )
    }

    fun savePresetTimes(context: Context, presetTimes: PresetTimes) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MORNING_HOUR, presetTimes.morningHour)
            .putInt(KEY_MORNING_MINUTE, presetTimes.morningMinute)
            .putInt(KEY_LUNCH_HOUR, presetTimes.lunchHour)
            .putInt(KEY_LUNCH_MINUTE, presetTimes.lunchMinute)
            .putInt(KEY_EVENING_HOUR, presetTimes.eveningHour)
            .putInt(KEY_EVENING_MINUTE, presetTimes.eveningMinute)
            .putInt(KEY_BEDTIME_HOUR, presetTimes.bedtimeHour)
            .putInt(KEY_BEDTIME_MINUTE, presetTimes.bedtimeMinute)
            .apply()
    }
}

fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour >= 12) "PM" else "AM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format("%02d:%02d %s", displayHour, minute, amPm)
}
