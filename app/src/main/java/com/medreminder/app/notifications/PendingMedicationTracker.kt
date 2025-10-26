package com.medreminder.app.notifications

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.medreminder.app.data.userPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tracks medications that have active notifications at the current time
 * Used for grouping notifications when multiple medications are due simultaneously
 *
 * This tracker stores pending medications in SharedPreferences and provides:
 * - Deduplication based on medication ID + hour + minute to prevent duplicate notifications
 * - Auto-cleanup of stale entries on app startup
 * - Removal of entries for deleted medications
 *
 * Key design decisions:
 * - Uses (medicationId, hour, minute) as unique key to allow same medication at different times
 * - Automatically cleans entries older than 2 hours
 * - Syncs with database on app start to remove stale references
 */
object PendingMedicationTracker {
    private const val TAG = "PendingMedTracker"
    private val KEY_PENDING = stringPreferencesKey("pending_list")
    private val KEY_LAST_CLEANUP = androidx.datastore.preferences.core.longPreferencesKey("last_cleanup_time")

    data class PendingMedication(
        val medicationId: Long,
        val medicationName: String,
        val medicationPhotoUri: String?,
        val profileId: Long,
        val hour: Int,
        val minute: Int,
        val timestamp: Long = System.currentTimeMillis(),
        val repeatCount: Int = 0
    )

    /**
     * Add a medication to the pending list when its notification is shown
     */
    fun addPendingMedication(context: Context, medication: PendingMedication) {
        val pending = getPendingMedications(context).toMutableList()

        // Remove any existing entry for this EXACT medication (same ID, hour, and minute)
        // This prevents duplicates when the same alarm fires multiple times
        pending.removeAll {
            it.medicationId == medication.medicationId &&
            it.hour == medication.hour &&
            it.minute == medication.minute
        }

        // Add the new entry
        pending.add(medication)

        // Clean up old entries (older than 2 hours)
        val cutoffTime = System.currentTimeMillis() - (2 * 60 * 60 * 1000)
        pending.removeAll { it.timestamp < cutoffTime }

        // Save to DataStore
        val jsonArray = JSONArray()
        pending.forEach { med ->
            val jsonObj = JSONObject().apply {
                put("medicationId", med.medicationId)
                put("medicationName", med.medicationName)
                put("medicationPhotoUri", med.medicationPhotoUri ?: "")
                put("profileId", med.profileId)
                put("hour", med.hour)
                put("minute", med.minute)
                put("timestamp", med.timestamp)
                put("repeatCount", med.repeatCount)
            }
            jsonArray.put(jsonObj)
        }

        runBlocking {
            context.userPrefs.edit { prefs ->
                prefs[KEY_PENDING] = jsonArray.toString()
            }
        }
        Log.d(TAG, "Added pending medication: ${medication.medicationName} at ${medication.hour}:${medication.minute}, total pending: ${pending.size}")
    }

    /**
     * Remove a medication from the pending list when user takes action
     */
    fun removePendingMedication(context: Context, medicationId: Long) {
        val pending = getPendingMedications(context).toMutableList()

        pending.removeAll { it.medicationId == medicationId }

        val jsonArray = JSONArray()
        pending.forEach { med ->
            val jsonObj = JSONObject().apply {
                put("medicationId", med.medicationId)
                put("medicationName", med.medicationName)
                put("medicationPhotoUri", med.medicationPhotoUri ?: "")
                put("profileId", med.profileId)
                put("hour", med.hour)
                put("minute", med.minute)
                put("timestamp", med.timestamp)
                put("repeatCount", med.repeatCount)
            }
            jsonArray.put(jsonObj)
        }

        runBlocking {
            context.userPrefs.edit { prefs ->
                prefs[KEY_PENDING] = jsonArray.toString()
            }
        }
        Log.d(TAG, "Removed pending medication ID: $medicationId, remaining: ${pending.size}")
    }

    /**
     * Remove all pending medications at a specific time
     */
    fun removePendingMedicationsAtTime(context: Context, hour: Int, minute: Int) {
        val pending = getPendingMedications(context).toMutableList()

        pending.removeAll { it.hour == hour && it.minute == minute }

        val jsonArray = JSONArray()
        pending.forEach { med ->
            val jsonObj = JSONObject().apply {
                put("medicationId", med.medicationId)
                put("medicationName", med.medicationName)
                put("medicationPhotoUri", med.medicationPhotoUri ?: "")
                put("profileId", med.profileId)
                put("hour", med.hour)
                put("minute", med.minute)
                put("timestamp", med.timestamp)
                put("repeatCount", med.repeatCount)
            }
            jsonArray.put(jsonObj)
        }

        runBlocking {
            context.userPrefs.edit { prefs ->
                prefs[KEY_PENDING] = jsonArray.toString()
            }
        }
        Log.d(TAG, "Removed all pending medications at $hour:$minute, remaining: ${pending.size}")
    }

    /**
     * Remove all pending medications for a specific profile
     * Used when deleting a profile
     */
    fun removePendingMedicationsForProfile(context: Context, profileId: Long) {
        val pending = getPendingMedications(context).toMutableList()

        pending.removeAll { it.profileId == profileId }

        val jsonArray = JSONArray()
        pending.forEach { med ->
            val jsonObj = JSONObject().apply {
                put("medicationId", med.medicationId)
                put("medicationName", med.medicationName)
                put("medicationPhotoUri", med.medicationPhotoUri ?: "")
                put("profileId", med.profileId)
                put("hour", med.hour)
                put("minute", med.minute)
                put("timestamp", med.timestamp)
                put("repeatCount", med.repeatCount)
            }
            jsonArray.put(jsonObj)
        }

        runBlocking {
            context.userPrefs.edit { prefs ->
                prefs[KEY_PENDING] = jsonArray.toString()
            }
        }
        Log.d(TAG, "Removed all pending medications for profile ID: $profileId, remaining: ${pending.size}")
    }

    /**
     * Get all pending medications
     */
    fun getPendingMedications(context: Context): List<PendingMedication> {
        val jsonString = runBlocking {
            context.userPrefs.data.first()[KEY_PENDING]
        } ?: return emptyList()
        return parsePending(jsonString)
    }

    fun pendingMedicationsFlow(context: Context): Flow<List<PendingMedication>> {
        return context.userPrefs.data.map { prefs ->
            prefs[KEY_PENDING]?.let { parsePending(it) } ?: emptyList()
        }
    }

    private fun parsePending(jsonString: String): List<PendingMedication> = try {
        val jsonArray = JSONArray(jsonString)
        val medications = mutableListOf<PendingMedication>()
        for (i in 0 until jsonArray.length()) {
            val jsonObj = jsonArray.getJSONObject(i)
            medications.add(
                PendingMedication(
                    medicationId = jsonObj.getLong("medicationId"),
                    medicationName = jsonObj.getString("medicationName"),
                    medicationPhotoUri = jsonObj.getString("medicationPhotoUri").takeIf { it.isNotEmpty() },
                    profileId = jsonObj.optLong("profileId", 1L), // Default to 1 for backward compatibility
                    hour = jsonObj.getInt("hour"),
                    minute = jsonObj.getInt("minute"),
                    timestamp = jsonObj.getLong("timestamp"),
                    repeatCount = jsonObj.optInt("repeatCount", 0)
                )
            )
        }
        medications
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing pending medications", e)
        emptyList()
    }

    /**
     * Get pending medications at a specific time
     */
    fun getPendingMedicationsAtTime(context: Context, hour: Int, minute: Int): List<PendingMedication> {
        return getPendingMedications(context).filter { it.hour == hour && it.minute == minute }
    }

    /**
     * Clear all pending medications
     */
    fun clearAll(context: Context) {
        runBlocking {
            context.userPrefs.edit { prefs ->
                prefs.remove(KEY_PENDING)
            }
        }
        Log.d(TAG, "Cleared all pending medications")
    }

    /**
     * Remove pending medications that no longer exist in the database
     * Also records MISSED entries for old pending medications that were never acted upon
     * Additionally scans for missed doses during app downtime (gap period)
     * Should be called on app startup to clean up stale data
     */
    fun cleanupStaleEntries(context: Context) {
        val db = com.medreminder.app.data.MedicationDatabase.getDatabase(context)
        val medicationDao = db.medicationDao()
        val historyDao = db.historyDao()

        // Get last cleanup timestamp
        val lastCleanupTime = runBlocking {
            context.userPrefs.data.first()[KEY_LAST_CLEANUP] ?: 0L
        }

        // Get all medications
        val allMedications = medicationDao.getAllMedicationsSync()

        // Get all current medication IDs from database
        val validIds = allMedications.map { it.id }.toSet()

        // Get pending medications
        val pending = getPendingMedications(context).toMutableList()
        val initialSize = pending.size

        // Current time for checking if pending medications are stale
        val now = System.currentTimeMillis()
        val cutoffTime = now - (24 * 60 * 60 * 1000) // 24 hours ago

        // Track medications to record as MISSED
        val missedMedications = mutableListOf<PendingMedication>()

        // Remove any pending medications whose IDs don't exist in the database
        pending.removeAll { !validIds.contains(it.medicationId) }

        // Identify and remove stale pending medications (older than 24 hours)
        pending.removeAll { med ->
            // Calculate the actual scheduled time for this medication
            val scheduledCal = java.util.Calendar.getInstance()
            scheduledCal.timeInMillis = med.timestamp
            scheduledCal.set(java.util.Calendar.HOUR_OF_DAY, med.hour)
            scheduledCal.set(java.util.Calendar.MINUTE, med.minute)
            scheduledCal.set(java.util.Calendar.SECOND, 0)
            scheduledCal.set(java.util.Calendar.MILLISECOND, 0)

            val scheduledTime = scheduledCal.timeInMillis

            // If scheduled time was more than 24 hours ago, it's stale
            if (scheduledTime < cutoffTime) {
                Log.d(TAG, "Found stale pending: ${med.medicationName} at ${med.hour}:${med.minute}")
                missedMedications.add(med)
                true // Remove from pending
            } else {
                false // Keep it
            }
        }

        // Remove duplicates: keep only one entry per unique (medicationId, hour, minute) combination
        val seen = mutableSetOf<String>()
        pending.removeAll { med ->
            val key = "${med.medicationId}_${med.hour}_${med.minute}"
            if (seen.contains(key)) {
                Log.d(TAG, "Removing duplicate: ${med.medicationName} at ${med.hour}:${med.minute}")
                true
            } else {
                seen.add(key)
                false
            }
        }

        // Scan for missed doses during gap period (app downtime)
        if (lastCleanupTime > 0) {
            val gapStart = lastCleanupTime
            val gapEnd = now

            Log.d(TAG, "Scanning for missed doses from gap period: ${(gapEnd - gapStart) / (1000 * 60 * 60)} hours")

            // Get all existing history to avoid duplicates
            val existingHistory = runBlocking {
                historyDao.getHistoryForDateRange(gapStart, gapEnd).first()
            }

            // For each medication, check scheduled times during gap
            for (medication in allMedications) {
                if (medication.reminderTimesJson == null) continue

                val gapMissedDoses = findMissedDosesInGap(
                    medication = medication,
                    gapStart = gapStart,
                    gapEnd = now,
                    existingHistory = existingHistory
                )

                // Add to missed medications list for recording
                gapMissedDoses.forEach { (scheduledTime, hour, minute) ->
                    missedMedications.add(
                        PendingMedication(
                            medicationId = medication.id,
                            medicationName = medication.name,
                            medicationPhotoUri = medication.photoUri,
                            profileId = medication.profileId,
                            hour = hour,
                            minute = minute,
                            timestamp = scheduledTime
                        )
                    )
                }

                if (gapMissedDoses.isNotEmpty()) {
                    Log.d(TAG, "Found ${gapMissedDoses.size} missed doses for ${medication.name} during gap period")
                }
            }
        }

        // Record MISSED entries in history database
        if (missedMedications.isNotEmpty()) {
            missedMedications.forEach { med ->
                // Create scheduled time
                val scheduledCal = java.util.Calendar.getInstance()
                scheduledCal.timeInMillis = med.timestamp
                scheduledCal.set(java.util.Calendar.HOUR_OF_DAY, med.hour)
                scheduledCal.set(java.util.Calendar.MINUTE, med.minute)
                scheduledCal.set(java.util.Calendar.SECOND, 0)
                scheduledCal.set(java.util.Calendar.MILLISECOND, 0)

                val history = com.medreminder.app.data.MedicationHistory(
                    profileId = med.profileId,
                    medicationId = med.medicationId,
                    medicationName = med.medicationName,
                    scheduledTime = scheduledCal.timeInMillis,
                    takenTime = System.currentTimeMillis(),
                    wasOnTime = false,
                    action = "MISSED"
                )

                runBlocking {
                    try {
                        historyDao.insertHistory(history)
                        Log.d(TAG, "Recorded MISSED entry for ${med.medicationName} at ${med.hour}:${med.minute}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error recording MISSED entry", e)
                    }
                }
            }
        }

        if (pending.size < initialSize) {
            // Save the cleaned list to DataStore
            val jsonArray = JSONArray()
            pending.forEach { med ->
                val jsonObj = JSONObject().apply {
                    put("medicationId", med.medicationId)
                    put("medicationName", med.medicationName)
                    put("medicationPhotoUri", med.medicationPhotoUri ?: "")
                    put("profileId", med.profileId)
                    put("hour", med.hour)
                    put("minute", med.minute)
                    put("timestamp", med.timestamp)
                    put("repeatCount", med.repeatCount)
                }
                jsonArray.put(jsonObj)
            }
            runBlocking {
                context.userPrefs.edit { prefs ->
                    prefs[KEY_PENDING] = jsonArray.toString()
                }
            }
            Log.d(TAG, "Cleaned up stale entries: removed ${initialSize - pending.size} entries, recorded ${missedMedications.size} as MISSED")
        }

        // Update last cleanup timestamp
        runBlocking {
            context.userPrefs.edit { prefs ->
                prefs[KEY_LAST_CLEANUP] = now
            }
        }
        Log.d(TAG, "Updated last cleanup timestamp to $now")
    }

    /**
     * Find missed doses for a medication during a gap period
     * Returns list of (scheduledTime, hour, minute) for doses that should be marked as MISSED
     */
    private fun findMissedDosesInGap(
        medication: com.medreminder.app.data.Medication,
        gapStart: Long,
        gapEnd: Long,
        existingHistory: List<com.medreminder.app.data.MedicationHistory>
    ): List<Triple<Long, Int, Int>> {
        val missedDoses = mutableListOf<Triple<Long, Int, Int>>()

        try {
            val jsonArray = JSONArray(medication.reminderTimesJson)

            // Parse all scheduled times
            data class ScheduledTime(val hour: Int, val minute: Int, val days: IntArray)
            val scheduledTimes = mutableListOf<ScheduledTime>()

            for (i in 0 until jsonArray.length()) {
                val timeObj = jsonArray.getJSONObject(i)
                val hour = timeObj.getInt("hour")
                val minute = timeObj.getInt("minute")

                val daysArray = timeObj.getJSONArray("days")
                val days = IntArray(daysArray.length())
                for (j in 0 until daysArray.length()) {
                    days[j] = daysArray.getInt(j)
                }

                scheduledTimes.add(ScheduledTime(hour, minute, days))
            }

            // Iterate through each day in the gap
            val startCal = java.util.Calendar.getInstance().apply { timeInMillis = gapStart }
            val endCal = java.util.Calendar.getInstance().apply { timeInMillis = gapEnd }

            while (startCal.timeInMillis <= endCal.timeInMillis) {
                val dayOfWeek = startCal.get(java.util.Calendar.DAY_OF_WEEK)
                val calendarDayIndex = when (dayOfWeek) {
                    java.util.Calendar.SUNDAY -> 0
                    java.util.Calendar.MONDAY -> 1
                    java.util.Calendar.TUESDAY -> 2
                    java.util.Calendar.WEDNESDAY -> 3
                    java.util.Calendar.THURSDAY -> 4
                    java.util.Calendar.FRIDAY -> 5
                    java.util.Calendar.SATURDAY -> 6
                    else -> -1
                }

                // Check each scheduled time for this day
                for (scheduledTime in scheduledTimes) {
                    // Check if this time is scheduled for this day of week
                    if (calendarDayIndex !in scheduledTime.days) continue

                    // Create the scheduled timestamp for this specific occurrence
                    val scheduledCal = startCal.clone() as java.util.Calendar
                    scheduledCal.set(java.util.Calendar.HOUR_OF_DAY, scheduledTime.hour)
                    scheduledCal.set(java.util.Calendar.MINUTE, scheduledTime.minute)
                    scheduledCal.set(java.util.Calendar.SECOND, 0)
                    scheduledCal.set(java.util.Calendar.MILLISECOND, 0)

                    val scheduledTimestamp = scheduledCal.timeInMillis

                    // Only consider if this time has passed
                    if (scheduledTimestamp > gapEnd) continue

                    // Check if there's a history entry for this time
                    val hasHistory = existingHistory.any { history ->
                        history.medicationId == medication.id &&
                        isSameScheduledTime(history.scheduledTime, scheduledTimestamp)
                    }

                    if (!hasHistory) {
                        missedDoses.add(Triple(scheduledTimestamp, scheduledTime.hour, scheduledTime.minute))
                    }
                }

                // Move to next day
                startCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding missed doses for ${medication.name}", e)
        }

        return missedDoses
    }

    /**
     * Check if two timestamps represent the same scheduled time
     * (same day, same hour, same minute)
     */
    private fun isSameScheduledTime(time1: Long, time2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }

        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR) &&
               cal1.get(java.util.Calendar.HOUR_OF_DAY) == cal2.get(java.util.Calendar.HOUR_OF_DAY) &&
               cal1.get(java.util.Calendar.MINUTE) == cal2.get(java.util.Calendar.MINUTE)
    }
}
