package com.medreminder.app.notifications

import android.content.Context
import com.medreminder.app.data.MedicationDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Provides gamified, encouraging messages for medication notifications.
 * Uses Hinglish (Hindi + English) liberally for a friendly, motivating experience.
 */
object NotificationEncouragement {

    /**
     * Get an encouraging notification message based on streak and time of day
     */
    suspend fun getEncouragingMessage(
        context: Context,
        medicationName: String,
        profileName: String,
        repeatCount: Int = 0
    ): String {
        val streak = getCurrentStreak(context)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // If this is a repeat notification, be more urgent but still friendly
        if (repeatCount > 0) {
            return getRepeatMessage(medicationName, repeatCount)
        }

        // First notification - use encouraging streak-based message
        return when {
            streak >= 30 -> getChampionMessage(medicationName, profileName, streak)
            streak >= 14 -> getSuperstarMessage(medicationName, profileName, streak)
            streak >= 7 -> getRockstarMessage(medicationName, profileName, streak)
            streak >= 3 -> getGreatMessage(medicationName, profileName, streak)
            else -> getStarterMessage(medicationName, profileName, hour)
        }
    }

    /**
     * Get confirmation message when medication is marked as taken
     */
    suspend fun getConfirmationMessage(
        context: Context,
        medicationName: String,
        wasOnTime: Boolean,
        timeDiffMinutes: Long
    ): String {
        val streak = getCurrentStreak(context) + 1 // Include this dose

        return when {
            // Perfect timing
            wasOnTime && timeDiffMinutes <= 5 -> {
                listOf(
                    "Shabash! 🎯 Perfect timing! Streak: $streak days! 🔥",
                    "Waah! 👏 Bilkul on time! Keep it up! Streak: $streak days! 💪",
                    "Kamaal hai! ⭐ Super on time! $streak din ka streak! 🎉",
                    "Bahut badhiya! 🌟 Right on time! $streak day streak! 🚀",
                    "Ekdum perfect! ⏰ On the dot! Streak continues: $streak days! 💯"
                ).random()
            }

            // On time but not perfect
            wasOnTime -> {
                listOf(
                    "Bahut achha! ✅ On time! Streak: $streak days! Keep going! 💪",
                    "Great job! 👍 Taken on time! $streak din solid! 🔥",
                    "Mast! ⭐ Right on schedule! Streak: $streak days! 🎯",
                    "Awesome! 🌟 Time pe liya! Keep it up! 💯",
                    "Zabardast! 🎉 On track! $streak day streak! 🚀"
                ).random()
            }

            // Late but taken
            timeDiffMinutes < 60 -> {
                listOf(
                    "Chalo, ho gaya! 💪 Better late than never! Streak: $streak days!",
                    "No problem! ✅ Liya toh sahi! Keep going! 🔥",
                    "Good! 👍 Thoda late but taken! $streak days going strong! 💯",
                    "Sahi hai! 😊 Koi baat nahi, streak alive: $streak days! 🎯",
                    "Done! ✨ Late ho gaya but taken! $streak din continues! 🚀"
                ).random()
            }

            // Very late but still taken
            else -> {
                listOf(
                    "Good job! ✅ Late but done! Streak continues: $streak days! 💪",
                    "Koi baat nahi! 👍 Important hai lena! $streak days! 🔥",
                    "Well done! ⭐ Better late than miss! Streak: $streak days! 💯",
                    "Achha kiya! 😊 Taken is what matters! $streak days strong! 🎯",
                    "Great! ✨ Yaad raha, that's good! Streak: $streak days! 🚀"
                ).random()
            }
        }
    }

    /**
     * Get skip confirmation message (gentle reminder, not scolding)
     */
    fun getSkipMessage(medicationName: String): String {
        return listOf(
            "Okay, skipped. Agli baar zaroor lena! 💊",
            "Skip ho gaya. Next time pakka lena! 🎯",
            "Alright. Agli dose mat bhoolna! ⏰",
            "Skipped. Yaad rakhna next time! 💪",
            "Okay. Next reminder pe zaroor lena! ✨"
        ).random()
    }

    /**
     * Get snooze confirmation message
     */
    suspend fun getSnoozeMessage(context: Context, minutes: Int): String {
        val streak = getCurrentStreak(context)
        return listOf(
            "Thodi der rest! 😴 Reminder in $minutes min. Streak: $streak days!",
            "Okay, $minutes min baad yaad karenge! ⏰ Keep that streak alive!",
            "Snooze kar diya! 😊 $minutes min mein wapas aayenge!",
            "Chalo, $minutes minute ka break! ⏰ Mat bhoolna!",
            "Sure! 👍 $minutes min baad reminder aayega. Streak hai $streak days!"
        ).random()
    }

    // ========== Private Helper Methods ==========

    private fun getChampionMessage(medicationName: String, profileName: String, streak: Int): String {
        return listOf(
            "🏆 Champion $profileName! $streak days ka amazing streak! Time for $medicationName! 💪",
            "⭐ Superstar $profileName! $streak din perfect! Ab $medicationName lene ka time! 🔥",
            "🎖️ Legend mode! $streak days! Chalo $medicationName le lo! 💯",
            "👑 King/Queen of consistency! $streak days strong! Time for $medicationName! 🚀",
            "🌟 Incredible $profileName! $streak din ka record! $medicationName ka time hai! ✨"
        ).random()
    }

    private fun getSuperstarMessage(medicationName: String, profileName: String, streak: Int): String {
        return listOf(
            "🌟 Superstar $profileName! $streak days going great! Time for $medicationName! 🔥",
            "💪 Zabardast consistency! $streak days! Ab $medicationName lene ka time! ⭐",
            "🎯 On fire $profileName! $streak din strong! $medicationName ready hai! 💯",
            "⚡ Amazing streak! $streak days! Chalo $medicationName le lo! 🚀",
            "✨ Kamaal ka discipline! $streak days! Time for $medicationName! 🎉"
        ).random()
    }

    private fun getRockstarMessage(medicationName: String, profileName: String, streak: Int): String {
        return listOf(
            "🎸 Rockstar $profileName! $streak din perfect! $medicationName ka time! 💪",
            "⭐ Bahut achha chal raha hai! $streak days! Ab $medicationName lo! 🔥",
            "🔥 Mast consistency! $streak days! Time for $medicationName! ⭐",
            "💯 Great going $profileName! $streak din complete! $medicationName lo! 🎯",
            "✨ Ekdum on track! $streak days! $medicationName ka time aa gaya! 🚀"
        ).random()
    }

    private fun getGreatMessage(medicationName: String, profileName: String, streak: Int): String {
        return listOf(
            "👍 Good going $profileName! $streak days! Time for $medicationName! 💪",
            "⭐ Achha chal raha hai! $streak din ho gaye! $medicationName lo! 🔥",
            "💪 Keep it up! $streak days done! Ab $medicationName ka time! ⭐",
            "🎯 Nice streak starting! $streak days! Time for $medicationName! ✨",
            "😊 Bahut achha! $streak din! Chalo $medicationName le lo! 🚀"
        ).random()
    }

    private fun getStarterMessage(medicationName: String, profileName: String, hour: Int): String {
        val timeGreeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Namaste"
            in 17..20 -> "Good evening"
            else -> "Hey"
        }

        return listOf(
            "$timeGreeting $profileName! 💊 Time to take $medicationName! Let's start a streak! 🔥",
            "Hi $profileName! ⏰ $medicationName ka time hai! Ek nayi streak shuru karein! 💪",
            "$timeGreeting! 😊 Time for $medicationName. Consistency is key! ⭐",
            "Hey $profileName! 💊 $medicationName lo! Aaj se regular rahein! 🎯",
            "$timeGreeting! ✨ Time to take $medicationName! Let's build a streak! 🚀"
        ).random()
    }

    private fun getRepeatMessage(medicationName: String, repeatCount: Int): String {
        return when (repeatCount) {
            1 -> listOf(
                "Reminder again! ⏰ $medicationName abhi bhi pending hai! Please lo! 💊",
                "Yaad hai? 😊 $medicationName lena hai! Don't forget! ⏰",
                "Phir se reminder! 💊 $medicationName ka time ho gaya tha! 🔔",
                "Hey! ⏰ $medicationName still pending! Jaldi lo! 💪"
            ).random()

            2, 3 -> listOf(
                "Important! ⚠️ $medicationName abhi tak nahi liya! Please lo! 💊",
                "Yaad karo! 🔔 $medicationName bahut important hai! Lo please! ⏰",
                "Reminder phir se! ⚠️ $medicationName pending hai! Mat bhoolna! 💪",
                "Please! 💊 $medicationName lo! Health first! 🏥"
            ).random()

            else -> listOf(
                "🚨 Urgent! $medicationName abhi tak pending! Please jaldi lo! 💊",
                "⚠️ Mat bhoolna! $medicationName bohot zaruri hai! Please lo! 🏥",
                "🔴 Important reminder! $medicationName lo please! Health matters! 💪",
                "❗ Last reminder! $medicationName lena hai! Don't miss! ⏰"
            ).random()
        }
    }

    /**
     * Calculate current streak of consecutive days with all medications taken
     */
    suspend fun getCurrentStreak(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val database = MedicationDatabase.getDatabase(context)
            val historyDao = database.historyDao()

            // Get all history entries, ordered by date descending
            val allHistory = historyDao.getAllHistorySync()

            if (allHistory.isEmpty()) return@withContext 0

            // Group by date and check if all scheduled doses were taken
            val calendar = Calendar.getInstance()
            var streak = 0
            var currentDate = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Go backwards day by day
            for (dayOffset in 0..90) { // Check up to 90 days back
                val dayStart = currentDate - (dayOffset * 24 * 60 * 60 * 1000)
                val dayEnd = dayStart + (24 * 60 * 60 * 1000)

                val dayHistory = allHistory.filter {
                    it.scheduledTime in dayStart until dayEnd
                }

                if (dayHistory.isEmpty()) {
                    // No medications scheduled for this day, continue
                    continue
                }

                // Check if all medications were taken
                val allTaken = dayHistory.all { it.action == "TAKEN" }

                if (allTaken) {
                    streak++
                } else {
                    // Streak broken
                    break
                }
            }

            return@withContext streak
        } catch (e: Exception) {
            return@withContext 0
        }
    }
}
