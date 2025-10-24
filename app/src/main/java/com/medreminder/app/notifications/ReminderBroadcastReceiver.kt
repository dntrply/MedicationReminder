package com.medreminder.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.medreminder.app.data.SettingsStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.medreminder.app.MainActivity
import com.medreminder.app.R
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.data.MedicationHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID_PRIVATE = "medication_reminders_private"
        private const val CHANNEL_ID_FULL = "medication_reminders_full"
        private const val CHANNEL_NAME_PRIVATE = "Medication Reminders (Private)"
        private const val CHANNEL_NAME_FULL = "Medication Reminders (Full)"
        private const val GROUP_KEY = "medication_group"
        const val ACTION_MARK_TAKEN = "com.medreminder.app.ACTION_MARK_TAKEN"
        const val ACTION_SNOOZE = "com.medreminder.app.ACTION_SNOOZE"
        const val ACTION_SKIP = "com.medreminder.app.ACTION_SKIP"
        const val ACTION_MARK_ALL_TAKEN = "com.medreminder.app.ACTION_MARK_ALL_TAKEN"
        const val ACTION_SNOOZE_ALL = "com.medreminder.app.ACTION_SNOOZE_ALL"
        const val ACTION_SKIP_ALL = "com.medreminder.app.ACTION_SKIP_ALL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm triggered: ${intent.action}")

        when (intent.action) {
            ACTION_MARK_TAKEN -> handleMarkTaken(context, intent)
            ACTION_SNOOZE -> handleSnooze(context, intent)
            ACTION_SKIP -> handleSkip(context, intent)
            ACTION_MARK_ALL_TAKEN -> handleMarkAllTaken(context, intent)
            ACTION_SNOOZE_ALL -> handleSnoozeAll(context, intent)
            ACTION_SKIP_ALL -> handleSkipAll(context, intent)
            else -> showNotification(context, intent)
        }
    }

    private fun showNotification(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra("MEDICATION_ID", -1)
        val medicationName = intent.getStringExtra("MEDICATION_NAME") ?: "Medication"
        val medicationPhotoUri = intent.getStringExtra("MEDICATION_PHOTO_URI")
        val hour = intent.getIntExtra("HOUR", 0)
        val minute = intent.getIntExtra("MINUTE", 0)
        val repeatCount = intent.getIntExtra("REPEAT_COUNT", 0)

        if (medicationId == -1L) {
            Log.e(TAG, "Invalid medication ID")
            return
        }

        Log.d(TAG, "Showing notification for $medicationName (repeat #$repeatCount)")

        createNotificationChannels(context)

        // Add this medication to pending tracker (include repeatCount)
        PendingMedicationTracker.addPendingMedication(
            context,
            PendingMedicationTracker.PendingMedication(
                medicationId, medicationName, medicationPhotoUri, hour, minute,
                repeatCount = repeatCount
            )
        )

        // Check if there are other medications at the same time
        val pendingAtSameTime = PendingMedicationTracker.getPendingMedicationsAtTime(context, hour, minute)

        if (pendingAtSameTime.size > 1) {
            // Multiple medications - show grouped notification
            showGroupedNotification(context, pendingAtSameTime, hour, minute, repeatCount)
            return
        }

        // Single medication - show individual notification with action buttons
        createNotificationChannels(context)

        // Intent to open the app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            medicationId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full-screen intent to surface the app
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            medicationId.toInt() + 5000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for "Mark as Taken" action
        val markTakenIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_MARK_TAKEN
            putExtra("MEDICATION_ID", medicationId)
            putExtra("MEDICATION_NAME", medicationName)
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
        }
        val markTakenPendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.toInt() + 1000,
            markTakenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for "Snooze" action
        val snoozeIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("MEDICATION_ID", medicationId)
            putExtra("MEDICATION_NAME", medicationName)
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.toInt() + 2000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for "Skip" action
        val skipIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_SKIP
            putExtra("MEDICATION_ID", medicationId)
            putExtra("MEDICATION_NAME", medicationName)
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.toInt() + 3000,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showFull = runBlocking { SettingsStore.showFullOnLockscreenFlow(context).first() }
        val channelId = if (showFull) CHANNEL_ID_FULL else CHANNEL_ID_PRIVATE

        // Build the notification
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val isLocked = km.isKeyguardLocked
        val title = if (!showFull && isLocked) {
            context.getString(R.string.medication_reminder)
        } else {
            context.getString(R.string.time_to_take, medicationName)
        }

        // Check if device is actively being used (screen on and unlocked)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isScreenOn = powerManager.isInteractive
        val shouldUseFullScreenIntent = !isScreenOn || isLocked

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_medication)
            .setContentTitle(title)
            .setContentText(formatTime(hour, minute))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)  // Don't auto-dismiss when tapped
            .setOngoing(true)  // Make it persistent - can't be swiped away
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(openAppPendingIntent)

        // Only use full-screen intent if screen is off or device is locked
        if (shouldUseFullScreenIntent) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        builder.addAction(
                R.drawable.ic_check,
                context.getString(R.string.ive_taken_it),
                markTakenPendingIntent
            )
            .addAction(
                R.drawable.ic_snooze,
                context.getString(
                    R.string.remind_in_minutes,
                    runBlocking { com.medreminder.app.data.SettingsStore.repeatIntervalFlow(context).first() }
                ),
                snoozePendingIntent
            )
            .addAction(
                R.drawable.ic_cancel,
                context.getString(R.string.skip_dose),
                skipPendingIntent
            )
            .setTimeoutAfter(0)  // Never timeout

        if (!showFull) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            val publicNotification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification_medication)
                .setContentTitle(context.getString(R.string.medication_reminder))
                .setContentText(formatTime(hour, minute))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            builder.setPublicVersion(publicNotification)
        } else {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        val notification = builder.build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(medicationId.toInt(), notification)

        Log.d(TAG, "Notification shown for: $medicationName")

        // Schedule the next repeat reminder (limit to 6 repeats = 60 minutes total)
        if (repeatCount < 6) {
            NotificationScheduler.scheduleRepeatingReminder(
                context,
                medicationId,
                medicationName,
                medicationPhotoUri,
                hour,
                minute,
                repeatCount + 1
            )
        } else {
            Log.d(TAG, "Max repeats reached for $medicationName")
        }
    }

    private fun showGroupedNotification(
        context: Context,
        medications: List<PendingMedicationTracker.PendingMedication>,
        hour: Int,
        minute: Int,
        repeatCount: Int
    ) {
        Log.d(TAG, "Showing grouped notification for ${medications.size} medications at ${formatTime(hour, minute)}")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Cancel all individual notifications for these medications
        medications.forEach { med ->
            notificationManager.cancel(med.medicationId.toInt())
        }

        // Build medication names list
        val medicationNames = medications.joinToString(", ") { it.medicationName }

        // Intent to open the app to "Take Medications Now" screen
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TAKE_MEDICATIONS", true)
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            (hour * 100) + minute,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full-screen intent to surface the app
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TAKE_MEDICATIONS", true)
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            (hour * 100) + minute + 50000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for "Mark All as Taken" action
        val markAllTakenIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_MARK_ALL_TAKEN
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
        }
        val markAllTakenPendingIntent = PendingIntent.getBroadcast(
            context,
            (hour * 100) + minute + 10000,
            markAllTakenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for "Snooze All" action
        val snoozeAllIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALL
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
        }
        val snoozeAllPendingIntent = PendingIntent.getBroadcast(
            context,
            (hour * 100) + minute + 20000,
            snoozeAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for "Skip All" action
        val skipAllIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_SKIP_ALL
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
        }
        val skipAllPendingIntent = PendingIntent.getBroadcast(
            context,
            (hour * 100) + minute + 30000,
            skipAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showFull = runBlocking { SettingsStore.showFullOnLockscreenFlow(context).first() }
        val channelId = if (showFull) CHANNEL_ID_FULL else CHANNEL_ID_PRIVATE

        // Build the grouped notification
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val isLocked = km.isKeyguardLocked
        val groupTitle = if (!showFull && isLocked) {
            context.getString(R.string.medication_reminder)
        } else {
            context.getString(R.string.time_to_take_multiple, medications.size)
        }

        // Check if device is actively being used (screen on and unlocked)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isScreenOn = powerManager.isInteractive
        val shouldUseFullScreenIntent = !isScreenOn || isLocked

        val groupBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_medication)
            .setContentTitle(groupTitle)
            .setContentText(if (!showFull && isLocked) formatTime(hour, minute) else medicationNames)
            .setStyle(
                if (!showFull && isLocked)
                    NotificationCompat.BigTextStyle().bigText(formatTime(hour, minute))
                else NotificationCompat.BigTextStyle().bigText(medicationNames)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(openAppPendingIntent)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)

        // Only use full-screen intent if screen is off or device is locked
        if (shouldUseFullScreenIntent) {
            groupBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        groupBuilder
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.ive_taken_all),
                markAllTakenPendingIntent
            )
            .addAction(
                R.drawable.ic_snooze,
                context.getString(R.string.snooze_all),
                snoozeAllPendingIntent
            )
            .addAction(
                R.drawable.ic_cancel,
                context.getString(R.string.skip_all),
                skipAllPendingIntent
            )
            .setTimeoutAfter(0)

        if (!showFull) {
            groupBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            val publicVersion = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification_medication)
                .setContentTitle(context.getString(R.string.medication_reminder))
                .setContentText(formatTime(hour, minute))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            groupBuilder.setPublicVersion(publicVersion)
        } else {
            groupBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        val notification = groupBuilder.build()

        // Use a unique notification ID for this time slot
        val notificationId = (hour * 100) + minute
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, "Grouped notification shown for ${medications.size} medications")

        // Schedule the next repeat reminder (limit to 6 repeats = 60 minutes total)
        if (repeatCount < 6) {
            medications.forEach { med ->
                NotificationScheduler.scheduleRepeatingReminder(
                    context,
                    med.medicationId,
                    med.medicationName,
                    med.medicationPhotoUri,
                    hour,
                    minute,
                    repeatCount + 1
                )
            }
        } else {
            Log.d(TAG, "Max repeats reached for grouped medications")
        }
    }

    private fun handleMarkTaken(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra("MEDICATION_ID", -1)
        val medicationName = intent.getStringExtra("MEDICATION_NAME") ?: return
        val hour = intent.getIntExtra("HOUR", 0)
        val minute = intent.getIntExtra("MINUTE", 0)

        Log.d(TAG, "Mark as taken: $medicationName")

        // Cancel the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(medicationId.toInt())

        // Remove from pending tracker
        PendingMedicationTracker.removePendingMedication(context, medicationId)

        // Cancel grouped notification if exists
        val notificationId = (hour * 100) + minute
        notificationManager.cancel(notificationId)

        // Cancel all pending repeat reminders
        NotificationScheduler.cancelRepeatingReminders(context, medicationId, hour, minute)

        // Record in history database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = MedicationDatabase.getDatabase(context)
                val historyDao = database.historyDao()

                // Create scheduled time (the original reminder time)
                val scheduledCalendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }

                // Current time (when actually taken)
                val takenTime = System.currentTimeMillis()
                val scheduledTime = scheduledCalendar.timeInMillis

                // Consider "on time" if taken within 30 minutes of scheduled time
                val timeDiffMinutes = Math.abs(takenTime - scheduledTime) / (1000 * 60)
                val wasOnTime = timeDiffMinutes <= 30

                val history = MedicationHistory(
                    medicationId = medicationId,
                    medicationName = medicationName,
                    scheduledTime = scheduledTime,
                    takenTime = takenTime,
                    wasOnTime = wasOnTime,
                    action = "TAKEN"
                )

                historyDao.insertHistory(history)
                Log.d(TAG, "Saved history for $medicationName at $hour:$minute")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving history", e)
            }
        }

        // Show a brief confirmation
        val channelId = CHANNEL_ID_PRIVATE
        val confirmationNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_medication)
            .setContentTitle(context.getString(R.string.marked_as_taken))
            .setContentText(medicationName)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(3000) // Auto-dismiss after 3 seconds
            .build()

        notificationManager.notify(medicationId.toInt() + 9000, confirmationNotification)
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra("MEDICATION_ID", -1)
        val medicationName = intent.getStringExtra("MEDICATION_NAME") ?: return
        val medicationPhotoUri = intent.getStringExtra("MEDICATION_PHOTO_URI")
        val hour = intent.getIntExtra("HOUR", 0)
        val minute = intent.getIntExtra("MINUTE", 0)

        Log.d(TAG, "Snooze: $medicationName")

        // Cancel current notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(medicationId.toInt())

        // Cancel all pending repeat reminders
        NotificationScheduler.cancelRepeatingReminders(context, medicationId, hour, minute)

        // Reschedule for 10 minutes later (as repeat #0, starting fresh)
        NotificationScheduler.scheduleRepeatingReminder(
            context,
            medicationId,
            medicationName,
            medicationPhotoUri,
            hour,
            minute,
            repeatCount = 0
        )

        // Show a brief confirmation
        val channelId = CHANNEL_ID_PRIVATE
        val confirmationNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_medication)
            .setContentTitle(context.getString(R.string.reminder_snoozed))
            .setContentText(medicationName)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(3000)
            .build()

        notificationManager.notify(medicationId.toInt() + 9000, confirmationNotification)
    }

    private fun handleSkip(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra("MEDICATION_ID", -1)
        val medicationName = intent.getStringExtra("MEDICATION_NAME") ?: return
        val hour = intent.getIntExtra("HOUR", 0)
        val minute = intent.getIntExtra("MINUTE", 0)

        Log.d(TAG, "Skip dose: $medicationName")

        // Cancel current notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(medicationId.toInt())

        // Cancel all pending repeat reminders
        NotificationScheduler.cancelRepeatingReminders(context, medicationId, hour, minute)

        // Remove from pending tracker
        PendingMedicationTracker.removePendingMedication(context, medicationId)

        // Record as skipped in history database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = MedicationDatabase.getDatabase(context)
                val historyDao = database.historyDao()

                // Create scheduled time
                val scheduledCalendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }

                val history = MedicationHistory(
                    medicationId = medicationId,
                    medicationName = medicationName,
                    scheduledTime = scheduledCalendar.timeInMillis,
                    takenTime = System.currentTimeMillis(),
                    wasOnTime = false,
                    action = "SKIPPED"
                )

                historyDao.insertHistory(history)
                Log.d(TAG, "Recorded skipped dose for $medicationName")
            } catch (e: Exception) {
                Log.e(TAG, "Error recording skipped dose", e)
            }
        }

        // Show a brief confirmation
        val channelId = CHANNEL_ID_PRIVATE
        val confirmationNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_medication)
            .setContentTitle(context.getString(R.string.dose_skipped))
            .setContentText(medicationName)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(3000)
            .build()

        notificationManager.notify(medicationId.toInt() + 9000, confirmationNotification)
    }

    private fun handleMarkAllTaken(context: Context, intent: Intent) {
        val hour = intent.getIntExtra("HOUR", 0)
        val minute = intent.getIntExtra("MINUTE", 0)

        Log.d(TAG, "Mark all as taken at ${formatTime(hour, minute)}")

        val medications = PendingMedicationTracker.getPendingMedicationsAtTime(context, hour, minute)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Cancel all notifications and repeats for these medications
        medications.forEach { med ->
            notificationManager.cancel(med.medicationId.toInt())
            NotificationScheduler.cancelRepeatingReminders(context, med.medicationId, hour, minute)
        }

        // Cancel the grouped notification
        val notificationId = (hour * 100) + minute
        notificationManager.cancel(notificationId)

        // Remove all from pending tracker
        PendingMedicationTracker.removePendingMedicationsAtTime(context, hour, minute)

        // Show confirmation
        val medicationNames = medications.joinToString(", ") { it.medicationName }
        val channelId = CHANNEL_ID_PRIVATE
        val confirmationNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_medication)
            .setContentTitle(context.getString(R.string.marked_as_taken))
            .setContentText(medicationNames)
            .setStyle(NotificationCompat.BigTextStyle().bigText(medicationNames))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(3000)
            .build()

        notificationManager.notify(notificationId + 90000, confirmationNotification)
    }

    private fun handleSnoozeAll(context: Context, intent: Intent) {
        val hour = intent.getIntExtra("HOUR", 0)
        val minute = intent.getIntExtra("MINUTE", 0)

        Log.d(TAG, "Snooze all at ${formatTime(hour, minute)}")

        val medications = PendingMedicationTracker.getPendingMedicationsAtTime(context, hour, minute)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Cancel current notifications and repeats
        medications.forEach { med ->
            notificationManager.cancel(med.medicationId.toInt())
            NotificationScheduler.cancelRepeatingReminders(context, med.medicationId, hour, minute)
        }

        // Cancel the grouped notification
        val notificationId = (hour * 100) + minute
        notificationManager.cancel(notificationId)

        // Remove from pending tracker
        PendingMedicationTracker.removePendingMedicationsAtTime(context, hour, minute)

        // Reschedule all for 10 minutes later (as repeat #0, starting fresh)
        medications.forEach { med ->
            NotificationScheduler.scheduleRepeatingReminder(
                context,
                med.medicationId,
                med.medicationName,
                med.medicationPhotoUri,
                hour,
                minute,
                repeatCount = 0
            )
        }

        // Show confirmation
        val medicationNames = medications.joinToString(", ") { it.medicationName }
        val confirmationNotification = NotificationCompat.Builder(context, CHANNEL_ID_PRIVATE)
            .setSmallIcon(R.drawable.ic_notification_medication)
            .setContentTitle(context.getString(R.string.reminder_snoozed))
            .setContentText(medicationNames)
            .setStyle(NotificationCompat.BigTextStyle().bigText(medicationNames))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(3000)
            .build()

        notificationManager.notify(notificationId + 90000, confirmationNotification)
    }

    private fun handleSkipAll(context: Context, intent: Intent) {
        val hour = intent.getIntExtra("HOUR", 0)
        val minute = intent.getIntExtra("MINUTE", 0)

        Log.d(TAG, "Skip all at ${formatTime(hour, minute)}")

        val medications = PendingMedicationTracker.getPendingMedicationsAtTime(context, hour, minute)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Cancel all notifications and repeats
        medications.forEach { med ->
            notificationManager.cancel(med.medicationId.toInt())
            NotificationScheduler.cancelRepeatingReminders(context, med.medicationId, hour, minute)
        }

        // Cancel the grouped notification
        val notificationId = (hour * 100) + minute
        notificationManager.cancel(notificationId)

        // Remove from pending tracker
        PendingMedicationTracker.removePendingMedicationsAtTime(context, hour, minute)

        // Show confirmation
        val medicationNames = medications.joinToString(", ") { it.medicationName }
        val confirmationNotification = NotificationCompat.Builder(context, CHANNEL_ID_PRIVATE)
            .setSmallIcon(R.drawable.ic_notification_medication)
            .setContentTitle(context.getString(R.string.dose_skipped))
            .setContentText(medicationNames)
            .setStyle(NotificationCompat.BigTextStyle().bigText(medicationNames))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(3000)
            .build()

        notificationManager.notify(notificationId + 90000, confirmationNotification)
    }

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)

            val privateChannel = NotificationChannel(
                CHANNEL_ID_PRIVATE,
                CHANNEL_NAME_PRIVATE,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                setBypassDnd(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }

            val fullChannel = NotificationChannel(
                CHANNEL_ID_FULL,
                CHANNEL_NAME_FULL,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                setBypassDnd(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager?.createNotificationChannel(privateChannel)
            notificationManager?.createNotificationChannel(fullChannel)
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%02d:%02d %s", displayHour, minute, amPm)
    }
}
