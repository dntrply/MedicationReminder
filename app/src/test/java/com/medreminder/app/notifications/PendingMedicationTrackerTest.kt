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
}

