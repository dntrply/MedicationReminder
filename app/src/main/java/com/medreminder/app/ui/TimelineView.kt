package com.medreminder.app.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.medreminder.app.R
import com.medreminder.app.data.Medication
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.notifications.PendingMedicationTracker
import com.medreminder.app.utils.TimeUtils
import com.medreminder.app.utils.AudioPlayer

// Data class for timeline slots
data class MedicationTimeSlot(
    val medication: Medication,
    val hour: Int,
    val minute: Int
)

// Helper data class for quadruple values
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun TimelineView(
    medications: List<Medication>,
    currentLanguage: String,
    viewCounter: Int = 0,
    modifier: Modifier = Modifier
) {
    println("===== TimelineView START =====")
    println("TimelineView called with ${medications.size} medications")
    Log.d("Timeline", "TimelineView called with ${medications.size} medications")

    // State for showing medication action palette
    var selectedMedication by remember { mutableStateOf<MedicationTimeSlot?>(null) }

    // Parse all medications and their reminder times
    val timeSlots = remember(medications) {
        println("===== REMEMBER BLOCK START =====")
        println("Parsing ${medications.size} medications")
        Log.d("Timeline", "Parsing ${medications.size} medications")

        medications.forEachIndexed { index, med ->
            println("Medication $index: ${med.name}, hasJson: ${med.reminderTimesJson != null}, json=${med.reminderTimesJson}")
            Log.d("Timeline", "Medication $index: ${med.name}, hasJson: ${med.reminderTimesJson != null}")

            // Write JSON to file for debugging
            med.reminderTimesJson?.let { json ->
                try {
                    val file = java.io.File("/sdcard/Download/med_${med.name}_json.txt")
                    file.writeText(json)
                    println("Wrote JSON to: ${file.absolutePath}")
                } catch (e: Exception) {
                    println("Error writing JSON file: ${e.message}")
                }
            }
        }

        val slots = medications.flatMap { medication ->
            println("Processing medication: ${medication.name}")
            medication.reminderTimesJson?.let { jsonString ->
                println("Calling parseReminderTimesForTimeline with JSON: $jsonString")
                parseReminderTimesForTimeline(jsonString, medication)
            } ?: run {
                println("No reminderTimesJson for ${medication.name}")
                emptyList()
            }
        }.sortedBy { it.hour * 60 + it.minute }

        println("Total timeSlots after parsing: ${slots.size}")
        Log.d("Timeline", "Total timeSlots after parsing: ${slots.size}")
        println("===== REMEMBER BLOCK END =====")
        slots
    }

    // Get today's medication history to check what's been taken
    val context = LocalContext.current
    val historyDao = remember(context) {
        MedicationDatabase.getDatabase(context).historyDao()
    }

    // Observe pending medications reactively from DataStore
    val allPendingMeds by PendingMedicationTracker.pendingMedicationsFlow(context)
        .collectAsState(initial = emptyList())

    // Filter to only TODAY's pending medications (exclude overdue from previous days)
    val pendingMeds = remember(allPendingMeds) {
        val currentCal = java.util.Calendar.getInstance()
        val today = currentCal.get(java.util.Calendar.DAY_OF_YEAR)
        val thisYear = currentCal.get(java.util.Calendar.YEAR)

        allPendingMeds.filter { pending ->
            val scheduledCal = java.util.Calendar.getInstance()
            scheduledCal.timeInMillis = pending.timestamp
            scheduledCal.set(java.util.Calendar.HOUR_OF_DAY, pending.hour)
            scheduledCal.set(java.util.Calendar.MINUTE, pending.minute)

            val pendingDay = scheduledCal.get(java.util.Calendar.DAY_OF_YEAR)
            val pendingYear = scheduledCal.get(java.util.Calendar.YEAR)

            // Only include today's pending medications
            (pendingYear == thisYear && pendingDay == today)
        }
    }

    // Calculate start and end of day
    val (startOfDay, endOfDay) = remember {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val end = calendar.timeInMillis

        start to end
    }

    // Use collectAsState for more reactive updates
    val todayHistory by historyDao.getHistoryForDay(startOfDay, endOfDay)
        .collectAsState(initial = emptyList())

    // Log history changes for debugging
    LaunchedEffect(todayHistory) {
        Log.d("Timeline", "Today history updated: ${todayHistory.size} entries")
        todayHistory.forEach { history ->
            val takenCal = java.util.Calendar.getInstance()
            takenCal.timeInMillis = history.scheduledTime
            Log.d("Timeline", "History entry: ${history.medicationName} at ${takenCal.get(java.util.Calendar.HOUR_OF_DAY)}:${takenCal.get(java.util.Calendar.MINUTE)}")
        }
    }

    // Find the upcoming medication hour (prioritizing actual pending notifications)
    // Recalculate when timeSlots, todayHistory, or pendingMeds change
    val upcomingHour = remember(timeSlots, todayHistory, pendingMeds) {
        val currentCalendar = java.util.Calendar.getInstance()
        val currentHour = currentCalendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = currentCalendar.get(java.util.Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute

        Log.d("Timeline", "Current time is ${currentHour}:${currentMinute} (${currentTimeInMinutes} minutes)")
        Log.d("Timeline", "Total timeSlots: ${timeSlots.size}")
        timeSlots.forEach { slot ->
            Log.d("Timeline", "Slot ${slot.hour}:${slot.minute}")
        }

        // Check which scheduled times have been taken today
        val takenTimes = todayHistory.map { history ->
            val takenCal = java.util.Calendar.getInstance()
            takenCal.timeInMillis = history.scheduledTime
            "${takenCal.get(java.util.Calendar.HOUR_OF_DAY)}:${takenCal.get(java.util.Calendar.MINUTE)}"
        }.toSet()

        Log.d("Timeline", "Taken times today: $takenTimes")
        Log.d("Timeline", "History count: ${todayHistory.size}")

        // Outstanding = those with an active pending notification (i.e., notification was sent)
        val outstandingSlots = timeSlots.filter { slot ->
            pendingMeds.any {
                it.medicationId == slot.medication.id &&
                it.hour == slot.hour &&
                it.minute == slot.minute
            }
        }

        Log.d("Timeline", "Outstanding slots: ${outstandingSlots.size}")
        outstandingSlots.forEach { slot ->
            Log.d("Timeline", "Outstanding ${slot.hour}:${slot.minute}")
        }

        // Priority 1: If there are outstanding medications, find the closest one to current time (most recent outstanding)
        val closestOutstanding = outstandingSlots.maxByOrNull { it.hour * 60 + it.minute }

        val resultHour = if (closestOutstanding != null) {
            Log.d("Timeline", "Using closest outstanding: ${closestOutstanding.hour}")
            // Show the most recent outstanding medication (closest to current time)
            closestOutstanding.hour
        } else {
            // Priority 2: No outstanding meds, find the next upcoming one
            val upcomingSlot = timeSlots.firstOrNull { slot ->
                (slot.hour * 60 + slot.minute) >= currentTimeInMinutes
            }
            val result = upcomingSlot?.hour ?: timeSlots.firstOrNull()?.hour ?: currentHour
            Log.d("Timeline", "Using upcoming/first: $result (upcoming=${upcomingSlot?.hour}, first=${timeSlots.firstOrNull()?.hour})")
            result
        }

        Log.d("Timeline", "Final upcomingHour = $resultHour")
        resultHour
    }

    // Scroll state for horizontal scrolling
    val scrollState = rememberScrollState()

    // Get density for dp to px conversion
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Auto-scroll to upcoming medication hour when view is shown, history changes, or upcoming hour changes
    LaunchedEffect(upcomingHour, density, viewCounter) {
        // Each 2-hour block is 104dp (100dp width + 4dp spacing)
        // Calculate which 2-hour block the upcoming hour belongs to
        val hourBlock = upcomingHour / 2
        // Convert dp to pixels for scrolling
        val scrollPosition = with(density) {
            (hourBlock * 104).dp.toPx().toInt()
        }
        Log.d("Timeline", ">>> BEFORE SCROLL: current=${scrollState.value}, target=$scrollPosition, hour=$upcomingHour, hourBlock=$hourBlock, historyCount=${todayHistory.size}, viewCounter=$viewCounter")
        // Use scrollTo for instant scroll, avoiding animation conflicts
        scrollState.scrollTo(scrollPosition)
        Log.d("Timeline", ">>> AFTER SCROLL: current=${scrollState.value}")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {

        if (timeSlots.isEmpty()) {
            // No reminders set
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = androidx.compose.ui.graphics.Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_times_set),
                        fontSize = 20.sp,
                        color = androidx.compose.ui.graphics.Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Timeline with horizontal and vertical scrolling
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // 24-hour axis with horizontal scroll
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Create 12 two-hour blocks (0-1, 2-3, 4-5, ... 22-23)
                    for (hourBlock in 0..11) {
                        val startHour = hourBlock * 2
                        val endHour = startHour + 1
                        Column(
                            modifier = Modifier.width(100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Hour range label (e.g., "12-2 PM", "2-4 PM")
                            val startAmPm = if (startHour >= 12) "PM" else "AM"
                            val endAmPm = if (endHour >= 12) "PM" else "AM"
                            val startDisplay = when {
                                startHour == 0 -> 12
                                startHour > 12 -> startHour - 12
                                else -> startHour
                            }
                            val endDisplay = when {
                                endHour == 0 -> 12
                                endHour > 12 -> endHour - 12
                                else -> endHour
                            }
                            val rangeLabel = if (startAmPm == endAmPm) {
                                "$startDisplay-$endDisplay $endAmPm"
                            } else {
                                "$startDisplay $startAmPm-$endDisplay $endAmPm"
                            }
                            Text(
                                text = rangeLabel,
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Container for timeline and medications
                            Box(
                                modifier = Modifier
                                    .width(100.dp)  // Fixed width for 2-hour block
                                    .height(360.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                // Hour marker line at the start (left edge)
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(360.dp)
                                        .background(androidx.compose.ui.graphics.Color(0xFFE0E0E0))
                                )

                                // Find medications for this 2-hour block
                                val medicationsIn2HourBlock = timeSlots.filter {
                                    it.hour == startHour || it.hour == endHour
                                }.sortedBy { it.hour * 60 + it.minute }

                                val blockWidthDp = 100f  // Total width of 2-hour block
                                val hourHeightDp = 360f
                                val totalMinutesInBlock = 120  // 2 hours = 120 minutes

                                // Group medications that overlap in time (within same 15-min window)
                                // This allows us to vertically stagger them
                                val bucketCount = 8  // 2 hours * 4 buckets/hour = 8 buckets
                                val buckets: Map<Int, List<MedicationTimeSlot>> = (0 until bucketCount).associateWith { idx ->
                                    medicationsIn2HourBlock.filter { slot ->
                                        val minutesFromBlockStart = (slot.hour - startHour) * 60 + slot.minute
                                        minutesFromBlockStart / 15 == idx
                                    }
                                }

                                buckets.forEach { (bucketIndex, group) ->
                                    if (group.isEmpty()) return@forEach

                                    // Process each medication in this bucket
                                    // Reverse the order so later medications are drawn first (behind)
                                    // and earlier medications are drawn last (on top)
                                    group.reversed().forEachIndexed { reverseIdx, slot ->
                                        val idx = group.size - 1 - reverseIdx  // Original index for stagger calculation
                                        // Calculate horizontal position based on HOUR only within 2-hour block
                                        // First hour (startHour) -> left half (0-49dp), Second hour (endHour) -> right half (50-99dp)
                                        val isSecondHour = slot.hour == endHour
                                        val xOffset = if (isSecondHour) blockWidthDp / 2 else 0f

                                        // Calculate vertical position based on MINUTES within the hour (0-59)
                                        // Each hour has 4 slots: 0-14min, 15-29min, 30-44min, 45-59min
                                        val minuteSlot = slot.minute / 15  // 0, 1, 2, or 3
                                        val slotHeightDp = hourHeightDp / 4  // 360dp / 4 = 90dp per 15-min slot
                                        val baseYPosition = minuteSlot * slotHeightDp

                                        // Calculate vertical offset for overlapping medications
                                        val yOffset = if (group.size > 1 && idx > 0) {
                                            // Check if this medication is at the exact same time as the previous one
                                            val prevSlot = group[idx - 1]
                                            val isSameExactTime = slot.hour == prevSlot.hour && slot.minute == prevSlot.minute

                                            if (isSameExactTime) {
                                                // Same exact time: small offset just to show both exist
                                                idx * 8f
                                            } else {
                                                // Different times in same bucket: larger offset to show time difference
                                                idx * 20f
                                            }
                                        } else {
                                            0f
                                        }
                                        val position = baseYPosition + yOffset

                                        Column(
                                            modifier = Modifier
                                                .offset(x = xOffset.dp, y = position.dp)
                                                .wrapContentWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Check if this medication has been taken
                                            val currentCalendar = java.util.Calendar.getInstance()
                                            val currentHour = currentCalendar.get(java.util.Calendar.HOUR_OF_DAY)
                                            val currentMinute = currentCalendar.get(java.util.Calendar.MINUTE)
                                            val currentTimeInMinutes = currentHour * 60 + currentMinute
                                            val slotTimeInMinutes = slot.hour * 60 + slot.minute

                                            val isTaken = todayHistory.any { history ->
                                                val takenCal = java.util.Calendar.getInstance()
                                                takenCal.timeInMillis = history.scheduledTime
                                                val matches = history.medicationId == slot.medication.id &&
                                                    takenCal.get(java.util.Calendar.HOUR_OF_DAY) == slot.hour &&
                                                    takenCal.get(java.util.Calendar.MINUTE) == slot.minute &&
                                                    history.action == "TAKEN"  // Only count as taken if action is TAKEN
                                                if (matches) {
                                                    Log.d("Timeline", "Found match for ${slot.medication.name} at ${slot.hour}:${slot.minute}")
                                                }
                                                matches
                                            }

                                            // A medication should only be marked as "outstanding" if:
                                            // 1. It's in the pending notification tracker (meaning a notification was actually sent)
                                            // OR
                                            // 2. It has history (meaning it was taken/skipped, so we know notification was sent)
                                            val hasPendingNotification = pendingMeds.any {
                                                it.medicationId == slot.medication.id &&
                                                it.hour == slot.hour &&
                                                it.minute == slot.minute
                                            }

                                            val hasHistoryForThisTime = todayHistory.any { history ->
                                                val historyCal = java.util.Calendar.getInstance()
                                                historyCal.timeInMillis = history.scheduledTime
                                                history.medicationId == slot.medication.id &&
                                                    historyCal.get(java.util.Calendar.HOUR_OF_DAY) == slot.hour &&
                                                    historyCal.get(java.util.Calendar.MINUTE) == slot.minute
                                            }

                                            // Only mark as outstanding if there's a pending notification that hasn't been taken
                                            val isOutstanding = hasPendingNotification && !isTaken

                                            // Always log for debugging
                                            Log.d("Timeline", "=== MEDICATION CHECK ===")
                                            Log.d("Timeline", "Name: ${slot.medication.name} at ${slot.hour}:${slot.minute}")
                                            Log.d("Timeline", "Has pending notification? $hasPendingNotification")
                                            Log.d("Timeline", "Has history for this time? $hasHistoryForThisTime")
                                            Log.d("Timeline", "Is taken? $isTaken")
                                            Log.d("Timeline", "Final isOutstanding: $isOutstanding")
                                            Log.d("Timeline", "========================")

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                // Medication image with border for outstanding meds
                                                // Earlier medications (idx=0) drawn last = on top, should have higher shadow elevation
                                                // Later medications (idx>0) drawn first = in back, should have lower shadow elevation
                                                val elevation = if (group.size > 1) {
                                                    (2 + (group.size - idx - 1) * 2).dp  // Earlier (idx 0) gets higher elevation
                                                } else {
                                                    2.dp
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .shadow(
                                                            elevation = elevation,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
                                                            selectedMedication = slot
                                                        }
                                                ) {
                                                    if (slot.medication.photoUri != null) {
                                                        Image(
                                                            painter = rememberAsyncImagePainter(
                                                                Uri.parse(slot.medication.photoUri)
                                                            ),
                                                            contentDescription = slot.medication.name,
                                                            modifier = Modifier
                                                                .size(56.dp)
                                                                .then(
                                                                    if (isOutstanding) {
                                                                        Modifier.border(
                                                                            width = 3.dp,
                                                                            color = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
                                                                            shape = RoundedCornerShape(8.dp)
                                                                        )
                                                                    } else Modifier
                                                                )
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(androidx.compose.ui.graphics.Color.White),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(56.dp)
                                                                .then(
                                                                    if (isOutstanding) {
                                                                        Modifier.border(
                                                                            width = 3.dp,
                                                                            color = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
                                                                            shape = RoundedCornerShape(8.dp)
                                                                        )
                                                                    } else Modifier
                                                                )
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(
                                                                    if (isOutstanding)
                                                                        androidx.compose.ui.graphics.Color(0xFFFFEBEE)
                                                                    else
                                                                        androidx.compose.ui.graphics.Color(0xFFE3F2FD)
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Medication,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(32.dp),
                                                                tint = if (isOutstanding)
                                                                    androidx.compose.ui.graphics.Color(0xFFFF6B6B)
                                                                else
                                                                    androidx.compose.ui.graphics.Color(0xFF4A90E2)
                                                            )
                                                        }
                                                    }

                                                    // Alert badge for outstanding medications
                                                    if (isOutstanding) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .offset(x = 4.dp, y = (-4).dp)
                                                                .size(16.dp)
                                                                .clip(CircleShape)
                                                                .background(androidx.compose.ui.graphics.Color(0xFFFF6B6B))
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Warning,
                                                                contentDescription = "Outstanding",
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(2.dp),
                                                                tint = androidx.compose.ui.graphics.Color.White
                                                            )
                                                        }
                                                    }

                                                    // Green checkmark badge for taken medications
                                                    if (isTaken) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .offset(x = 4.dp, y = (-4).dp)
                                                                .size(20.dp)
                                                                .clip(CircleShape)
                                                                .background(androidx.compose.ui.graphics.Color(0xFF4CAF50))
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = "Taken",
                                                                modifier = Modifier.fillMaxSize(),
                                                                tint = androidx.compose.ui.graphics.Color.White
                                                            )
                                                        }
                                                    }
                                                }

                                                // Time label
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = String.format("%02d:%02d", slot.hour, slot.minute),
                                                    fontSize = 10.sp,
                                                    color = androidx.compose.ui.graphics.Color.Black,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier
                                                        .background(
                                                            androidx.compose.ui.graphics.Color.White,
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Add spacer at the end so later hours can scroll into center view
                    Spacer(modifier = Modifier.width(300.dp))
                }
            }
        }
    }

    // Show medication action palette when a medication is selected
    selectedMedication?.let { slot ->
        MedicationActionPalette(
            medication = slot.medication,
            hour = slot.hour,
            minute = slot.minute,
            onDismiss = { selectedMedication = null },
            currentLanguage = currentLanguage
        )
    }
}

// Helper function to parse reminder times for timeline display
fun parseReminderTimesForTimeline(jsonString: String, medication: Medication): List<MedicationTimeSlot> {
    try {
        val slots = mutableListOf<MedicationTimeSlot>()

        // Get today's day of week
        // Calendar uses: 1=Sunday, 2=Monday, ..., 7=Saturday
        // ReminderTime uses: 1=Monday, 2=Tuesday, ..., 7=Sunday
        // We need to convert Calendar format to ReminderTime format
        val calendar = java.util.Calendar.getInstance()
        val calendarDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)

        // Convert Calendar day (1=Sun, 2=Mon, ..., 7=Sat) to ReminderTime format (1=Mon, 2=Tue, ..., 7=Sun)
        val todayInReminderFormat = when (calendarDayOfWeek) {
            java.util.Calendar.SUNDAY -> 7      // Sunday: Calendar=1, Reminder=7
            java.util.Calendar.MONDAY -> 1      // Monday: Calendar=2, Reminder=1
            java.util.Calendar.TUESDAY -> 2     // Tuesday: Calendar=3, Reminder=2
            java.util.Calendar.WEDNESDAY -> 3   // Wednesday: Calendar=4, Reminder=3
            java.util.Calendar.THURSDAY -> 4    // Thursday: Calendar=5, Reminder=4
            java.util.Calendar.FRIDAY -> 5      // Friday: Calendar=6, Reminder=5
            java.util.Calendar.SATURDAY -> 6    // Saturday: Calendar=7, Reminder=6
            else -> 1
        }

        println("=== TIMELINE DEBUG ===")
        println("Parsing medication: ${medication.name}")
        println("JSON: $jsonString")
        println("Today (Calendar format): $calendarDayOfWeek, (Reminder format): $todayInReminderFormat")
        Log.d("Timeline", "Parsing medication: ${medication.name}")
        Log.d("Timeline", "JSON: $jsonString")
        Log.d("Timeline", "Today (Calendar format): $calendarDayOfWeek, (Reminder format): $todayInReminderFormat")

        // Parse the JSON to extract times and their associated days
        // JSON format: [{"hour":18,"minute":0,"days":[1,2,3,4,5,6,7]}]
        val reminderTimePattern = """\{"hour":(\d+),"minute":(\d+),"days":\[([\d,]*)\]\}""".toRegex()

        reminderTimePattern.findAll(jsonString).forEach { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val daysString = match.groupValues[3]

            Log.d("Timeline", "Found time: ${hour}:${minute}, days: [$daysString]")

            // Parse the days array (in ReminderTime format: 1=Mon, 7=Sun)
            val days = if (daysString.isNotEmpty()) {
                daysString.split(",").map { it.toInt() }.toSet()
            } else {
                emptySet()
            }

            Log.d("Timeline", "Parsed days set: $days, contains today? ${days.contains(todayInReminderFormat)}")

            // Only include this time if it's scheduled for today
            if (days.contains(todayInReminderFormat)) {
                slots.add(MedicationTimeSlot(medication, hour, minute))
                Log.d("Timeline", "Added slot: ${hour}:${minute}")
            } else {
                Log.d("Timeline", "Skipped slot (not scheduled for today): ${hour}:${minute}")
            }
        }

        Log.d("Timeline", "Total slots for ${medication.name}: ${slots.size}")
        return slots
    } catch (e: Exception) {
        Log.e("Timeline", "Error parsing reminder times: ${e.message}", e)
        return emptyList()
    }
}

// Helper function to format hour labels for timeline
fun formatHourLabel(hour: Int): String {
    val amPm = if (hour >= 12) "PM" else "AM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format("%02d:00 %s", displayHour, amPm)
}

// Reaction palette popup for timeline medications
@Composable
fun MedicationActionPalette(
    medication: Medication,
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    currentLanguage: String = "en"
) {
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayer(context) }
    var isPlayingAudio by remember { mutableStateOf(false) }

    // Clean up audio player when dialog is dismissed
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.release()
        }
    }

    // Get today's medication history to check if this dose was already skipped
    val historyDao = remember(context) {
        MedicationDatabase.getDatabase(context).historyDao()
    }

    // Calculate start and end of day
    val (startOfDay, endOfDay) = remember {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val end = calendar.timeInMillis

        start to end
    }

    val todayHistory by historyDao.getHistoryForDay(startOfDay, endOfDay)
        .collectAsState(initial = emptyList())

    // Check if this specific dose (medication + scheduled time) was already acted upon
    val wasSkipped = remember(todayHistory, medication.id, hour, minute) {
        todayHistory.any { history ->
            val historyCal = java.util.Calendar.getInstance()
            historyCal.timeInMillis = history.scheduledTime
            history.medicationId == medication.id &&
                historyCal.get(java.util.Calendar.HOUR_OF_DAY) == hour &&
                historyCal.get(java.util.Calendar.MINUTE) == minute &&
                history.action == "SKIPPED"
        }
    }

    // Find the history entry if this dose was already taken
    val takenHistoryEntry = remember(todayHistory, medication.id, hour, minute) {
        todayHistory.firstOrNull { history ->
            val historyCal = java.util.Calendar.getInstance()
            historyCal.timeInMillis = history.scheduledTime
            history.medicationId == medication.id &&
                historyCal.get(java.util.Calendar.HOUR_OF_DAY) == hour &&
                historyCal.get(java.util.Calendar.MINUTE) == minute &&
                history.action == "TAKEN"
        }
    }

    val wasTaken = takenHistoryEntry != null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = null, // No icon for both states - large image will be in content
        title = null, // No title - medication name will be in content
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (wasTaken && takenHistoryEntry != null) {
                    // Calculate lateness for determining emoji/message
                    val latenessInfo = TimeUtils.formatLateness(
                        scheduledTime = takenHistoryEntry.scheduledTime,
                        takenTime = takenHistoryEntry.takenTime,
                        context = context,
                        onTimeThresholdMinutes = 5
                    )
                    val diffMillis = takenHistoryEntry.takenTime - takenHistoryEntry.scheduledTime
                    val wasOnTime = latenessInfo == null

                    // Choose emoji and message based on timing
                    val (emoji, message, backgroundColor, textColor) = when {
                        wasOnTime -> {
                            // On time - celebratory!
                            Quadruple("🎉", stringResource(R.string.taken_on_time_celebration),
                                androidx.compose.ui.graphics.Color(0xFFE8F5E9),
                                androidx.compose.ui.graphics.Color(0xFF2E7D32))
                        }
                        diffMillis > 0 -> {
                            // Late - encouraging
                            Quadruple("✅", stringResource(R.string.taken_better_late),
                                androidx.compose.ui.graphics.Color(0xFFFFF3E0),
                                androidx.compose.ui.graphics.Color(0xFFF57C00))
                        }
                        else -> {
                            // Early - positive
                            Quadruple("⭐", stringResource(R.string.taken_early_great),
                                androidx.compose.ui.graphics.Color(0xFFE3F2FD),
                                androidx.compose.ui.graphics.Color(0xFF1976D2))
                        }
                    }

                    // Visual-first design: Large medication image + emoji + simple message
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Close button at top-right
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = androidx.compose.ui.graphics.Color(0xFF666666),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                        // Medication name at top (since we removed title)
                        Text(
                            text = medication.name,
                            fontSize = 24.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = androidx.compose.ui.graphics.Color(0xFF333333)
                        )

                        // Extra large medication image (using space from removed thumbnail)
                        Box(
                            modifier = Modifier.size(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (medication.photoUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(Uri.parse(medication.photoUri)),
                                    contentDescription = medication.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(
                                            width = 4.dp,
                                            color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                            shape = RoundedCornerShape(20.dp)
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(androidx.compose.ui.graphics.Color(0xFFE8F5E9))
                                        .border(
                                            width = 4.dp,
                                            color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                            shape = RoundedCornerShape(20.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Medication,
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }

                            // Checkmark badge overlay at top-right (consistent with timeline view)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-8).dp)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color(0xFF4CAF50))
                                    .border(
                                        width = 3.dp,
                                        color = androidx.compose.ui.graphics.Color.White,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Taken",
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Emoji + Encouraging message in a card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = backgroundColor
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Large emoji
                                Text(
                                    text = emoji,
                                    fontSize = 48.sp,
                                    textAlign = TextAlign.Center
                                )

                                // Encouraging message
                                Text(
                                    text = message,
                                    fontSize = 20.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = textColor,
                                    textAlign = TextAlign.Center
                                )

                                // Small subtitle with timing info (if not on time)
                                if (!wasOnTime && latenessInfo != null) {
                                    Text(
                                        text = latenessInfo,
                                        fontSize = 14.sp,
                                        color = textColor.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        } // End Column
                    } // End Box
                } else {
                    // Show action buttons - same large image layout as "Already Taken"
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Close button at top-right
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = androidx.compose.ui.graphics.Color(0xFF666666),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Medication name at top
                            Text(
                                text = medication.name,
                                fontSize = 24.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = androidx.compose.ui.graphics.Color(0xFF333333)
                            )

                            // Scheduled time with audio button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = String.format("%02d:%02d %s",
                                        if (hour == 0) 12 else if (hour > 12) hour - 12 else hour,
                                        minute,
                                        if (hour >= 12) "PM" else "AM"
                                    ),
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )

                                // Audio playback icon button (if medication has audio note)
                                if (!medication.audioNotePath.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    IconButton(
                                        onClick = {
                                            if (isPlayingAudio) {
                                                audioPlayer.stop()
                                                isPlayingAudio = false
                                            } else {
                                                val success = audioPlayer.play(
                                                    audioPath = medication.audioNotePath,
                                                    onCompletion = {
                                                        isPlayingAudio = false
                                                    },
                                                    onError = {
                                                        isPlayingAudio = false
                                                    }
                                                )
                                                isPlayingAudio = success
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.Default.VolumeUp,
                                            contentDescription = if (isPlayingAudio) "Stop audio" else "Play audio",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            // Extra large medication image (more space since audio button moved)
                            Box(
                                modifier = Modifier.size(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (medication.photoUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(Uri.parse(medication.photoUri)),
                                        contentDescription = medication.name,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(20.dp))
                                            .border(
                                                width = 3.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(20.dp)
                                            ),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .border(
                                                width = 3.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(20.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Medication,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(80.dp)
                                        )
                                    }
                                }
                            }

                            // Action buttons
                            // Mark as Taken button
                            Button(
                                onClick = {
                                    val intent = Intent(context, com.medreminder.app.notifications.ReminderBroadcastReceiver::class.java).apply {
                                        action = com.medreminder.app.notifications.ReminderBroadcastReceiver.ACTION_MARK_TAKEN
                                        putExtra("MEDICATION_ID", medication.id)
                                        putExtra("MEDICATION_NAME", medication.name)
                                        putExtra("HOUR", hour)
                                        putExtra("MINUTE", minute)
                                    }
                                    context.sendBroadcast(intent)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.taken),
                                        fontSize = 18.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                }
                            }

                            // Snooze and Skip buttons in a row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(context, com.medreminder.app.notifications.ReminderBroadcastReceiver::class.java).apply {
                                            action = com.medreminder.app.notifications.ReminderBroadcastReceiver.ACTION_SNOOZE
                                            putExtra("MEDICATION_ID", medication.id)
                                            putExtra("MEDICATION_NAME", medication.name)
                                            putExtra("MEDICATION_PHOTO_URI", medication.photoUri)
                                            putExtra("HOUR", hour)
                                            putExtra("MINUTE", minute)
                                        }
                                        context.sendBroadcast(intent)
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    enabled = !wasSkipped
                                ) {
                                    Text(
                                        stringResource(R.string.snooze),
                                        fontSize = 16.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(context, com.medreminder.app.notifications.ReminderBroadcastReceiver::class.java).apply {
                                            action = com.medreminder.app.notifications.ReminderBroadcastReceiver.ACTION_SKIP
                                            putExtra("MEDICATION_ID", medication.id)
                                            putExtra("MEDICATION_NAME", medication.name)
                                            putExtra("HOUR", hour)
                                            putExtra("MINUTE", minute)
                                        }
                                        context.sendBroadcast(intent)
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    enabled = !wasSkipped,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Text(
                                        stringResource(R.string.skip),
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        } // End Column
                    } // End Box
                }
            }
        },
        confirmButton = {},
        dismissButton = {} // No dismiss button - X button is in content for both states
    )
}
