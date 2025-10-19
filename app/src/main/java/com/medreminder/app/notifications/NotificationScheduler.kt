package com.medreminder.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.medreminder.app.data.Medication
import com.medreminder.app.data.ReminderTime
import java.util.Calendar

object NotificationScheduler {

    private const val TAG = "NotificationScheduler"
    private const val REPEAT_INTERVAL_MINUTES = 10

    /**
     * Schedule notifications for a medication based on its reminder times
     */
    fun scheduleMedicationNotifications(context: Context, medication: Medication) {
        val reminderTimes = parseReminderTimes(medication.reminderTimesJson ?: return)

        if (reminderTimes.isEmpty()) {
            Log.d(TAG, "No reminder times for medication: ${medication.name}")
            return
        }

        reminderTimes.forEach { reminderTime ->
            scheduleNotification(context, medication, reminderTime)
        }
    }

    /**
     * Schedule a single notification for a specific time
     */
    private fun scheduleNotification(
        context: Context,
        medication: Medication,
        reminderTime: ReminderTime
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("MEDICATION_ID", medication.id)
            putExtra("MEDICATION_NAME", medication.name)
            putExtra("MEDICATION_PHOTO_URI", medication.photoUri)
            putExtra("HOUR", reminderTime.hour)
            putExtra("MINUTE", reminderTime.minute)
        }

        // Create unique request code using medication ID and time
        val requestCode = (medication.id.toInt() * 10000) + (reminderTime.hour * 100) + reminderTime.minute

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate the time for the alarm
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminderTime.hour)
            set(Calendar.MINUTE, reminderTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Schedule the alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ requires exact alarm permission check
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled notification for ${medication.name} at ${reminderTime.hour}:${reminderTime.minute}")
                } else {
                    Log.w(TAG, "Cannot schedule exact alarms - permission not granted")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled notification for ${medication.name} at ${reminderTime.hour}:${reminderTime.minute}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm", e)
        }
    }

    /**
     * Schedule a repeating reminder that triggers every 10 minutes
     */
    fun scheduleRepeatingReminder(
        context: Context,
        medicationId: Long,
        medicationName: String,
        medicationPhotoUri: String?,
        hour: Int,
        minute: Int,
        repeatCount: Int = 0
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("MEDICATION_ID", medicationId)
            putExtra("MEDICATION_NAME", medicationName)
            putExtra("MEDICATION_PHOTO_URI", medicationPhotoUri)
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
            putExtra("REPEAT_COUNT", repeatCount)
        }

        // Use a unique request code that includes repeat count
        val requestCode = (medicationId.toInt() * 100000) + (hour * 100) + minute + (repeatCount * 10000)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule for 10 minutes from now
        val triggerTime = System.currentTimeMillis() + (REPEAT_INTERVAL_MINUTES * 60 * 1000)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled repeat reminder #$repeatCount for $medicationName in $REPEAT_INTERVAL_MINUTES minutes")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled repeat reminder #$repeatCount for $medicationName in $REPEAT_INTERVAL_MINUTES minutes")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling repeat alarm", e)
        }
    }

    /**
     * Cancel repeating reminders for a specific medication/time
     */
    fun cancelRepeatingReminders(
        context: Context,
        medicationId: Long,
        hour: Int,
        minute: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel up to 10 possible repeats (100 minutes of reminders)
        for (repeatCount in 0..10) {
            val intent = Intent(context, ReminderBroadcastReceiver::class.java)
            val requestCode = (medicationId.toInt() * 100000) + (hour * 100) + minute + (repeatCount * 10000)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
        }

        Log.d(TAG, "Cancelled all repeating reminders for medication ID: $medicationId")
    }

    /**
     * Cancel all notifications for a medication
     */
    fun cancelMedicationNotifications(context: Context, medication: Medication) {
        val reminderTimes = parseReminderTimes(medication.reminderTimesJson ?: return)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        reminderTimes.forEach { reminderTime ->
            val intent = Intent(context, ReminderBroadcastReceiver::class.java)
            val requestCode = (medication.id.toInt() * 10000) + (reminderTime.hour * 100) + reminderTime.minute

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Cancelled notification for ${medication.name} at ${reminderTime.hour}:${reminderTime.minute}")
        }
    }

    /**
     * Reschedule all notifications (used after boot or app update)
     */
    suspend fun rescheduleAllNotifications(context: Context, medications: List<Medication>) {
        Log.d(TAG, "Rescheduling notifications for ${medications.size} medications")
        medications.forEach { medication ->
            scheduleMedicationNotifications(context, medication)
        }
    }

    /**
     * Parse reminder times from JSON
     */
    private fun parseReminderTimes(jsonString: String): List<ReminderTime> {
        try {
            val times = mutableListOf<ReminderTime>()
            val timePattern = """"hour":(\d+),"minute":(\d+),"days":\[([\d,]*)\]""".toRegex()

            timePattern.findAll(jsonString).forEach { match ->
                val hour = match.groupValues[1].toInt()
                val minute = match.groupValues[2].toInt()
                val daysString = match.groupValues[3]
                val days = if (daysString.isNotEmpty()) {
                    daysString.split(",").map { it.toInt() }.toSet()
                } else {
                    setOf(1, 2, 3, 4, 5, 6, 7)
                }

                times.add(ReminderTime(hour, minute, days))
            }

            return times
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing reminder times", e)
            return emptyList()
        }
    }
}
