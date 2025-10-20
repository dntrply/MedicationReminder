package com.medreminder.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.userPrefs by preferencesDataStore(name = "user_prefs")

object SettingsStore {
    private val LANGUAGE = stringPreferencesKey("language")

    fun languageFlow(context: Context): Flow<String> =
        context.userPrefs.data.map { prefs -> prefs[LANGUAGE] ?: "en" }

    suspend fun setLanguage(context: Context, lang: String) {
        context.userPrefs.edit { prefs -> prefs[LANGUAGE] = lang }
    }
}

