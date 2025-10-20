package com.medreminder.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

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
    private val MORNING_HOUR = intPreferencesKey("morning_hour")
    private val MORNING_MINUTE = intPreferencesKey("morning_minute")
    private val LUNCH_HOUR = intPreferencesKey("lunch_hour")
    private val LUNCH_MINUTE = intPreferencesKey("lunch_minute")
    private val EVENING_HOUR = intPreferencesKey("evening_hour")
    private val EVENING_MINUTE = intPreferencesKey("evening_minute")
    private val BEDTIME_HOUR = intPreferencesKey("bedtime_hour")
    private val BEDTIME_MINUTE = intPreferencesKey("bedtime_minute")

    fun getPresetTimesFlow(context: Context): Flow<PresetTimes> {
        return context.userPrefs.data.map { prefs ->
            prefs.toPresetTimes()
        }
    }

    // Synchronous convenience method (used rarely)
    fun getPresetTimes(context: Context): PresetTimes =
        runBlocking { context.userPrefs.data.map { it.toPresetTimes() }.first() }

    suspend fun savePresetTimes(context: Context, presetTimes: PresetTimes) {
        context.userPrefs.edit { prefs ->
            prefs[MORNING_HOUR] = presetTimes.morningHour
            prefs[MORNING_MINUTE] = presetTimes.morningMinute
            prefs[LUNCH_HOUR] = presetTimes.lunchHour
            prefs[LUNCH_MINUTE] = presetTimes.lunchMinute
            prefs[EVENING_HOUR] = presetTimes.eveningHour
            prefs[EVENING_MINUTE] = presetTimes.eveningMinute
            prefs[BEDTIME_HOUR] = presetTimes.bedtimeHour
            prefs[BEDTIME_MINUTE] = presetTimes.bedtimeMinute
        }
    }

    private fun Preferences.toPresetTimes(): PresetTimes {
        return PresetTimes(
            morningHour = this[MORNING_HOUR] ?: 8,
            morningMinute = this[MORNING_MINUTE] ?: 0,
            lunchHour = this[LUNCH_HOUR] ?: 12,
            lunchMinute = this[LUNCH_MINUTE] ?: 0,
            eveningHour = this[EVENING_HOUR] ?: 18,
            eveningMinute = this[EVENING_MINUTE] ?: 0,
            bedtimeHour = this[BEDTIME_HOUR] ?: 22,
            bedtimeMinute = this[BEDTIME_MINUTE] ?: 0
        )
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
