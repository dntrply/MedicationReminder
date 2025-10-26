package com.medreminder.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.userPrefs by preferencesDataStore(name = "user_prefs")

object SettingsStore {
    private val LANGUAGE = stringPreferencesKey("language")
    private val SHOW_FULL_ON_LOCK = booleanPreferencesKey("show_full_on_lockscreen")
    private val REPEAT_INTERVAL_MIN = intPreferencesKey("repeat_interval_minutes")
    private val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")

    fun languageFlow(context: Context): Flow<String> =
        context.userPrefs.data.map { prefs -> prefs[LANGUAGE] ?: "en" }

    suspend fun setLanguage(context: Context, lang: String) {
        context.userPrefs.edit { prefs -> prefs[LANGUAGE] = lang }
    }

    fun showFullOnLockscreenFlow(context: Context): Flow<Boolean> =
        context.userPrefs.data.map { prefs -> prefs[SHOW_FULL_ON_LOCK] ?: false }

    suspend fun setShowFullOnLockscreen(context: Context, show: Boolean) {
        context.userPrefs.edit { prefs -> prefs[SHOW_FULL_ON_LOCK] = show }
    }

    fun repeatIntervalFlow(context: Context): Flow<Int> =
        context.userPrefs.data.map { prefs ->
            val raw = prefs[REPEAT_INTERVAL_MIN] ?: 10
            raw.coerceIn(2, 120)
        }

    suspend fun setRepeatInterval(context: Context, minutes: Int) {
        val clamped = minutes.coerceIn(2, 120)
        context.userPrefs.edit { prefs -> prefs[REPEAT_INTERVAL_MIN] = clamped }
    }

    fun activeProfileIdFlow(context: Context): Flow<Long?> =
        context.userPrefs.data.map { prefs -> prefs[ACTIVE_PROFILE_ID] }

    suspend fun getActiveProfileId(context: Context): Long? {
        return context.userPrefs.data.map { it[ACTIVE_PROFILE_ID] }.first()
    }

    suspend fun setActiveProfileId(context: Context, profileId: Long) {
        context.userPrefs.edit { prefs -> prefs[ACTIVE_PROFILE_ID] = profileId }
    }
}

