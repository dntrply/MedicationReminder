package com.medreminder.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.medreminder.app.data.MedicationDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receiver that listens for device boot completed events
 * to reschedule medication reminder notifications
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "Device boot completed, rescheduling notifications")

        // Use goAsync() to allow async work in the receiver
        val pendingResult = goAsync()

        // Use a coroutine scope to handle the async database query
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val database = MedicationDatabase.getDatabase(context)
                val medications = database.medicationDao().getAllMedications().first()

                Log.d(TAG, "Rescheduling ${medications.size} medications")
                NotificationScheduler.rescheduleAllNotifications(context, medications)

                Log.d(TAG, "Notifications rescheduled successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling notifications", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
