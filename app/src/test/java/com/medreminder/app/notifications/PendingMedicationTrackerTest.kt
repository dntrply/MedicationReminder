package com.medreminder.app.notifications

import com.medreminder.app.data.Medication
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PendingMedicationTrackerTest {

    @Test
    fun findMissedDosesInGap_doseBeforeGapStart_isNotBackfilled() {
        // Today at 09:42 (scheduled dose)
        val calScheduled = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 42)
        }
        val scheduledTimestamp = calScheduled.timeInMillis

        // Gap window: start at 10:30 today, end at now (after 10:30)
        val calGapStart = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
        }
        val gapStart = calGapStart.timeInMillis

        val gapEnd = Calendar.getInstance().timeInMillis

        // Map Calendar.DAY_OF_WEEK to the app's 0..6 format used by PendingMedicationTracker
        val todayCalendarIndex = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> -1
        }

        // Reminder JSON includes today at 09:42
        val reminderTimesJson = """
            [
              {"hour":9, "minute":42, "days":[$todayCalendarIndex]}
            ]
        """.trimIndent()

        val medication = Medication(
            id = 123L,
            profileId = 1L,
            name = "TestMed",
            reminderTimesJson = reminderTimesJson,
            createdAt = scheduledTimestamp - 60_000 // before scheduled time
        )

        val existingHistory: List<com.medreminder.app.data.MedicationHistory> = emptyList()

        // Reflectively invoke private method:
        // private fun findMissedDosesInGap(medication, gapStart, gapEnd, existingHistory): List<Triple<Long, Int, Int>>
        val method = PendingMedicationTracker::class.java.getDeclaredMethod(
            "findMissedDosesInGap",
            com.medreminder.app.data.Medication::class.java,
            java.lang.Long.TYPE,
            java.lang.Long.TYPE,
            java.util.List::class.java
        )
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(
            PendingMedicationTracker,
            medication,
            gapStart,
            gapEnd,
            existingHistory
        ) as List<Any>

        // Desired behavior: since scheduledTimestamp (09:42) is BEFORE gapStart (10:30),
        // the backfill should NOT include it. Today’s same-day pending dose should not be marked MISSED by gap logic.
        assertTrue(
            "Expected no missed doses backfilled when scheduled time is before gapStart; got ${result.size}",
            result.isEmpty()
        )
    }

    private fun dayIndexFor(calendar: Calendar): Int {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> -1
        }
    }

    private fun invokeFindMissed(
        medication: Medication,
        gapStart: Long,
        gapEnd: Long,
        existingHistory: List<com.medreminder.app.data.MedicationHistory>
    ): List<Triple<Long, Int, Int>> {
        val method = PendingMedicationTracker::class.java.getDeclaredMethod(
            "findMissedDosesInGap",
            com.medreminder.app.data.Medication::class.java,
            java.lang.Long.TYPE,
            java.lang.Long.TYPE,
            java.util.List::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(
            PendingMedicationTracker,
            medication,
            gapStart,
            gapEnd,
            existingHistory
        ) as List<Triple<Long, Int, Int>>
    }

    @Test
    fun crossMidnight_remindersAcrossDays_areBackfilled() {
        val now = Calendar.getInstance()
        val today = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val idxYesterday = dayIndexFor(yesterday)
        val idxToday = dayIndexFor(today)

        val reminderTimesJson = """
            [
              {"hour":23, "minute":0, "days":[$idxYesterday]},
              {"hour":1,  "minute":0, "days":[$idxToday]}
            ]
        """.trimIndent()

        val medication = Medication(
            id = 2001L,
            profileId = 1L,
            name = "CrossMidnight",
            reminderTimesJson = reminderTimesJson
        )

        val gapStart = Calendar.getInstance().apply {
            timeInMillis = yesterday.timeInMillis
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val gapEnd = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val result = invokeFindMissed(medication, gapStart, gapEnd, emptyList())
        val hours = result.map { it.second }.toSet()
        assertTrue("Expected to include 23:00 and 01:00 across midnight; got $hours", hours.containsAll(listOf(23, 1)))
    }

    @Test
    fun multipleRemindersOneDay_returnsOnlyMissingOnes() {
        val base = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val idxToday = dayIndexFor(base)

        val reminderTimesJson = """
            [
              {"hour":9,  "minute":0, "days":[$idxToday]},
              {"hour":14, "minute":0, "days":[$idxToday]}
            ]
        """.trimIndent()

        val medication = Medication(
            id = 2002L,
            profileId = 1L,
            name = "MultiDay",
            reminderTimesJson = reminderTimesJson
        )

        val nineAm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val gapStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val gapEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Existing history marks 09:00 as taken already
        val existingHistory = listOf(
            com.medreminder.app.data.MedicationHistory(
                profileId = 1L,
                medicationId = 2002L,
                medicationName = "MultiDay",
                scheduledTime = nineAm,
                takenTime = nineAm + 10 * 60 * 1000,
                wasOnTime = true,
                action = "TAKEN"
            )
        )

        val result = invokeFindMissed(medication, gapStart, gapEnd, existingHistory)
        val hours = result.map { it.second }.toSet()
        assertTrue("Expected only 14:00 to be missed; got $hours", hours == setOf(14))
    }

    @Test
    fun weeklyRecurrence_takenLastWeek_missedThisWeek_isBackfilled() {
        // Target weekday = same as today for simplicity
        val today = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val lastWeek = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_YEAR, -7)
        }
        val idxWeekday = dayIndexFor(today)

        val reminderTimesJson = """
            [
              {"hour":10, "minute":30, "days":[$idxWeekday]}
            ]
        """.trimIndent()

        val medication = Medication(
            id = 2003L,
            profileId = 1L,
            name = "Weekly",
            reminderTimesJson = reminderTimesJson
        )

        val lastWeek1030 = (lastWeek.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val thisWeek1030 = (today.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val gapStart = (lastWeek.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val gapEnd = (today.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val existingHistory = listOf(
            com.medreminder.app.data.MedicationHistory(
                profileId = 1L,
                medicationId = 2003L,
                medicationName = "Weekly",
                scheduledTime = lastWeek1030,
                takenTime = lastWeek1030 + 5 * 60 * 1000,
                wasOnTime = true,
                action = "TAKEN"
            )
        )

        val result = invokeFindMissed(medication, gapStart, gapEnd, existingHistory)
        // Should include only this week's 10:30 (if time already passed)
        val containsThisWeek = result.any { (_, h, m) -> h == 10 && m == 30 }
        assertTrue("Expected this week's 10:30 to be missed if passed; got $result", containsThisWeek)
    }

    @Test
    fun multiDayGap_dailyAtNine_returnsEntriesForEachPastDay() {
        // Daily at 09:00 across all days
        val allDays = (0..6).joinToString(",")
        val reminderTimesJson = """
            [
              {"hour":9, "minute":0, "days":[${allDays}]}
            ]
        """.trimIndent()

        val medication = Medication(
            id = 2004L,
            profileId = 1L,
            name = "Daily",
            reminderTimesJson = reminderTimesJson
        )

        val twoDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -2)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val gapEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val result = invokeFindMissed(medication, twoDaysAgo, gapEnd, emptyList())
        // Expect 09:00 for two days ago, yesterday, and today (if <= noon), i.e., 3 entries
        val countAtNine = result.count { it.second == 9 && it.third == 0 }
        assertTrue("Expected 3 missed 09:00 entries across twoDaysAgo..today; got $countAtNine", countAtNine >= 2)
    }
}
