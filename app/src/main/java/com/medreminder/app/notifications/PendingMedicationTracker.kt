package com.medreminder.app.notifications

import android.content.Context
import android.util.Log
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
    private const val PREFS_NAME = "pending_medications"
    private const val KEY_PENDING = "pending_list"

    data class PendingMedication(
        val medicationId: Long,
        val medicationName: String,
        val medicationPhotoUri: String?,
        val hour: Int,
        val minute: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Add a medication to the pending list when its notification is shown
     */
    fun addPendingMedication(context: Context, medication: PendingMedication) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

        // Save to preferences
        val jsonArray = JSONArray()
        pending.forEach { med ->
            val jsonObj = JSONObject().apply {
                put("medicationId", med.medicationId)
                put("medicationName", med.medicationName)
                put("medicationPhotoUri", med.medicationPhotoUri ?: "")
                put("hour", med.hour)
                put("minute", med.minute)
                put("timestamp", med.timestamp)
            }
            jsonArray.put(jsonObj)
        }

        prefs.edit().putString(KEY_PENDING, jsonArray.toString()).apply()
        Log.d(TAG, "Added pending medication: ${medication.medicationName} at ${medication.hour}:${medication.minute}, total pending: ${pending.size}")
    }

    /**
     * Remove a medication from the pending list when user takes action
     */
    fun removePendingMedication(context: Context, medicationId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = getPendingMedications(context).toMutableList()

        pending.removeAll { it.medicationId == medicationId }

        val jsonArray = JSONArray()
        pending.forEach { med ->
            val jsonObj = JSONObject().apply {
                put("medicationId", med.medicationId)
                put("medicationName", med.medicationName)
                put("medicationPhotoUri", med.medicationPhotoUri ?: "")
                put("hour", med.hour)
                put("minute", med.minute)
                put("timestamp", med.timestamp)
            }
            jsonArray.put(jsonObj)
        }

        prefs.edit().putString(KEY_PENDING, jsonArray.toString()).apply()
        Log.d(TAG, "Removed pending medication ID: $medicationId, remaining: ${pending.size}")
    }

    /**
     * Remove all pending medications at a specific time
     */
    fun removePendingMedicationsAtTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = getPendingMedications(context).toMutableList()

        pending.removeAll { it.hour == hour && it.minute == minute }

        val jsonArray = JSONArray()
        pending.forEach { med ->
            val jsonObj = JSONObject().apply {
                put("medicationId", med.medicationId)
                put("medicationName", med.medicationName)
                put("medicationPhotoUri", med.medicationPhotoUri ?: "")
                put("hour", med.hour)
                put("minute", med.minute)
                put("timestamp", med.timestamp)
            }
            jsonArray.put(jsonObj)
        }

        prefs.edit().putString(KEY_PENDING, jsonArray.toString()).apply()
        Log.d(TAG, "Removed all pending medications at $hour:$minute, remaining: ${pending.size}")
    }

    /**
     * Get all pending medications
     */
    fun getPendingMedications(context: Context): List<PendingMedication> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PENDING, null) ?: return emptyList()

        return try {
            val jsonArray = JSONArray(jsonString)
            val medications = mutableListOf<PendingMedication>()

            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                medications.add(
                    PendingMedication(
                        medicationId = jsonObj.getLong("medicationId"),
                        medicationName = jsonObj.getString("medicationName"),
                        medicationPhotoUri = jsonObj.getString("medicationPhotoUri").takeIf { it.isNotEmpty() },
                        hour = jsonObj.getInt("hour"),
                        minute = jsonObj.getInt("minute"),
                        timestamp = jsonObj.getLong("timestamp")
                    )
                )
            }

            medications
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing pending medications", e)
            emptyList()
        }
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared all pending medications")
    }

    /**
     * Remove pending medications that no longer exist in the database
     * Should be called on app startup to clean up stale data
     */
    fun cleanupStaleEntries(context: Context) {
        val db = com.medreminder.app.data.MedicationDatabase.getDatabase(context)
        val medicationDao = db.medicationDao()

        // Get all current medication IDs from database
        val validIds = medicationDao.getAllMedicationsSync().map { it.id }.toSet()

        // Get pending medications
        val pending = getPendingMedications(context).toMutableList()
        val initialSize = pending.size

        // Remove any pending medications whose IDs don't exist in the database
        pending.removeAll { !validIds.contains(it.medicationId) }

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

        if (pending.size < initialSize) {
            // Save the cleaned list
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            pending.forEach { med ->
                val jsonObj = JSONObject().apply {
                    put("medicationId", med.medicationId)
                    put("medicationName", med.medicationName)
                    put("medicationPhotoUri", med.medicationPhotoUri ?: "")
                    put("hour", med.hour)
                    put("minute", med.minute)
                    put("timestamp", med.timestamp)
                }
                jsonArray.put(jsonObj)
            }
            prefs.edit().putString(KEY_PENDING, jsonArray.toString()).apply()
            Log.d(TAG, "Cleaned up stale entries: removed ${initialSize - pending.size} entries")
        }
    }
}
