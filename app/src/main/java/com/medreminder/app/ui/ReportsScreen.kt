package com.medreminder.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medreminder.app.R
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.data.SettingsStore
import kotlinx.coroutines.launch

/**
 * Report tabs for different visualization views
 */
enum class ReportTab(
    val icon: ImageVector,
    val titleResId: Int
) {
    SUMMARY(Icons.Default.Assessment, R.string.report_summary),
    CALENDAR(Icons.Default.CalendarMonth, R.string.report_calendar),
    TRENDS(Icons.Default.TrendingUp, R.string.report_trends),
    BY_MEDICATION(Icons.Default.Medication, R.string.report_by_medication)
}

/**
 * Main Reports Screen with bottom tab navigation
 * Provides full-screen views for different report visualizations
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReportsScreen(
    currentLanguage: String = "en",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Get default tab setting
    val defaultReportTab by SettingsStore.defaultReportTabFlow(context)
        .collectAsState(initial = "summary")

    // Map default tab string to index
    val initialPageIndex = remember(defaultReportTab) {
        when (defaultReportTab) {
            "summary" -> 0
            "calendar" -> 1
            "trends" -> 2
            "by_medication" -> 3
            else -> 0
        }
    }

    // Pager state for swipe navigation
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { ReportTab.entries.size }
    )

    // Current tab based on pager position
    val currentTab = ReportTab.entries[pagerState.currentPage]

    // Handle back button press
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(currentTab.titleResId),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF4A90E2),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            ReportBottomNavigationBar(
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    scope.launch {
                        pagerState.animateScrollToPage(tab.ordinal)
                    }
                }
            )
        }
    ) { paddingValues ->
        // Horizontal pager for swipe navigation between tabs
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            when (ReportTab.entries[page]) {
                ReportTab.SUMMARY -> SummaryStatsView(currentLanguage)
                ReportTab.CALENDAR -> CalendarHeatMapView(currentLanguage)
                ReportTab.TRENDS -> TrendsGraphView(currentLanguage)
                ReportTab.BY_MEDICATION -> ByMedicationDetailView(currentLanguage)
            }
        }
    }
}

/**
 * Bottom navigation bar for report tabs
 */
@Composable
fun ReportBottomNavigationBar(
    selectedTab: ReportTab,
    onTabSelected: (ReportTab) -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        contentColor = Color(0xFF4A90E2),
        tonalElevation = 8.dp,
        modifier = Modifier.height(72.dp)
    ) {
        ReportTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = stringResource(tab.titleResId),
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.titleResId),
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF4A90E2),
                    selectedTextColor = Color(0xFF4A90E2),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color(0xFFE3F2FD)
                )
            )
        }
    }
}

/**
 * Summary Stats View - Default tab showing key metrics
 */
@Composable
fun SummaryStatsView(currentLanguage: String) {
    val context = LocalContext.current
    val database = remember { MedicationDatabase.getDatabase(context) }
    val historyDao = remember { database.historyDao() }
    val medicationDao = remember { database.medicationDao() }

    // Get active profile ID
    val activeProfileId by SettingsStore.activeProfileIdFlow(context)
        .collectAsState(initial = null)

    // Get include skipped in adherence setting
    val includeSkipped by SettingsStore.includeSkippedInAdherenceFlow(context)
        .collectAsState(initial = false)

    // Get medications for the active profile
    val medications by medicationDao.getMedicationsByProfile(activeProfileId ?: 1L)
        .collectAsState(initial = emptyList())

    // Calculate date range for this week
    val thisWeekRange = remember {
        val endCal = java.util.Calendar.getInstance()
        endCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        endCal.set(java.util.Calendar.MINUTE, 59)
        endCal.set(java.util.Calendar.SECOND, 59)
        endCal.set(java.util.Calendar.MILLISECOND, 999)

        val startCal = java.util.Calendar.getInstance()
        startCal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
        startCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        startCal.set(java.util.Calendar.MINUTE, 0)
        startCal.set(java.util.Calendar.SECOND, 0)
        startCal.set(java.util.Calendar.MILLISECOND, 0)

        startCal.timeInMillis to endCal.timeInMillis
    }

    // Get history for this week
    val historyList by historyDao.getHistoryForDateRangeByProfile(
        profileId = activeProfileId ?: 1L,
        startTime = thisWeekRange.first,
        endTime = thisWeekRange.second
    ).collectAsState(initial = emptyList())

    // Calculate statistics
    val takenCount = historyList.count { it.action == "TAKEN" }
    val missedCount = historyList.count { it.action == "MISSED" }
    val skippedCount = historyList.count { it.action == "SKIPPED" }
    val totalCount = historyList.size
    val adherencePercentage = calculateAdherencePercentage(takenCount, totalCount, skippedCount, includeSkipped)

    // UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // THIS WEEK card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF8F9FA)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.this_week),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )

                // Large adherence percentage
                Text(
                    text = "$adherencePercentage%",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        adherencePercentage >= 90 -> Color(0xFF4CAF50)
                        adherencePercentage >= 70 -> Color(0xFFFFA726)
                        else -> Color(0xFFEF5350)
                    }
                )

                Text(
                    text = stringResource(R.string.success_score),
                    fontSize = 16.sp,
                    color = Color.Gray
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        statusType = MedicationStatus.Type.TAKEN_ON_TIME,
                        value = takenCount.toString(),
                        label = stringResource(R.string.taken)
                    )
                    StatItem(
                        statusType = MedicationStatus.Type.MISSED,
                        value = missedCount.toString(),
                        label = stringResource(R.string.missed_label)
                    )
                    StatItem(
                        statusType = MedicationStatus.Type.SKIPPED,
                        value = skippedCount.toString(),
                        label = stringResource(R.string.skipped_label)
                    )
                }
            }
        }

        // Placeholder message for future content
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE3F2FD)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF4A90E2),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Keep up the great work! More insights coming soon.",
                    fontSize = 14.sp,
                    color = Color(0xFF424242),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun StatItem(
    statusType: MedicationStatus.Type,
    value: String,
    label: String
) {
    val statusInfo = MedicationStatus.getStatusInfo(statusType)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(statusInfo.color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = statusInfo.icon,
                contentDescription = statusInfo.label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = statusInfo.color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

/**
 * Day adherence state for calendar visualization
 */
sealed class DayAdherenceState {
    object NotScheduled : DayAdherenceState()  // Med didn't exist yet - Gray
    object NoData : DayAdherenceState()        // Med existed but no doses scheduled
    data class Complete(val percentage: Int) : DayAdherenceState()  // 100% - Green
    data class Partial(val percentage: Int) : DayAdherenceState()   // 50-99% - Yellow
    data class Poor(val percentage: Int) : DayAdherenceState()      // <50% - Red
}

/**
 * Calendar Heat Map View - Shows daily adherence in a calendar grid
 */
@Composable
fun CalendarHeatMapView(currentLanguage: String) {
    val context = LocalContext.current
    val database = remember { MedicationDatabase.getDatabase(context) }
    val historyDao = remember { database.historyDao() }
    val medicationDao = remember { database.medicationDao() }

    // Get active profile ID
    val activeProfileId by SettingsStore.activeProfileIdFlow(context)
        .collectAsState(initial = null)

    // Get week start day setting
    val weekStartDay by SettingsStore.weekStartDayFlow(context)
        .collectAsState(initial = "sunday")

    // Get include skipped in adherence setting
    val includeSkipped by SettingsStore.includeSkippedInAdherenceFlow(context)
        .collectAsState(initial = false)

    // Get medications for the active profile
    val medications by medicationDao.getMedicationsByProfile(activeProfileId ?: 1L)
        .collectAsState(initial = emptyList())

    // State for selected month
    var currentMonth by remember { mutableStateOf(java.util.Calendar.getInstance()) }

    // State for showing day detail screen
    var selectedDate by remember { mutableStateOf<java.util.Calendar?>(null) }

    // Calculate date range for current month
    val monthRange = remember(currentMonth) {
        val startCal = currentMonth.clone() as java.util.Calendar
        startCal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        startCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        startCal.set(java.util.Calendar.MINUTE, 0)
        startCal.set(java.util.Calendar.SECOND, 0)
        startCal.set(java.util.Calendar.MILLISECOND, 0)

        val endCal = currentMonth.clone() as java.util.Calendar
        endCal.set(java.util.Calendar.DAY_OF_MONTH, endCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        endCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        endCal.set(java.util.Calendar.MINUTE, 59)
        endCal.set(java.util.Calendar.SECOND, 59)
        endCal.set(java.util.Calendar.MILLISECOND, 999)

        startCal.timeInMillis to endCal.timeInMillis
    }

    // Get history for current month
    val historyList by historyDao.getHistoryForDateRangeByProfile(
        profileId = activeProfileId ?: 1L,
        startTime = monthRange.first,
        endTime = monthRange.second
    ).collectAsState(initial = emptyList())

    // Show detail screen if a date is selected
    if (selectedDate != null) {
        DayDetailScreen(
            selectedDate = selectedDate!!,
            onBack = { selectedDate = null }
        )
    } else {
        // Show calendar view
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month selector
            MonthSelector(
                currentMonth = currentMonth,
                onPreviousMonth = {
                    val newMonth = currentMonth.clone() as java.util.Calendar
                    newMonth.add(java.util.Calendar.MONTH, -1)
                    currentMonth = newMonth
                },
                onNextMonth = {
                    val newMonth = currentMonth.clone() as java.util.Calendar
                    newMonth.add(java.util.Calendar.MONTH, 1)
                    currentMonth = newMonth
                }
            )

            // Calendar grid
            CalendarGrid(
                currentMonth = currentMonth,
                medications = medications,
                historyList = historyList,
                weekStartDay = weekStartDay,
                includeSkipped = includeSkipped,
                onDayClick = { dayCalendar ->
                    selectedDate = dayCalendar
                }
            )

            // Legend
            CalendarLegend()
        }
    }
}

@Composable
fun MonthSelector(
    currentMonth: java.util.Calendar,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val monthFormat = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()) }
    val today = remember { java.util.Calendar.getInstance() }
    val isCurrentMonth = currentMonth.get(java.util.Calendar.MONTH) == today.get(java.util.Calendar.MONTH) &&
            currentMonth.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Previous month",
                modifier = Modifier.size(32.dp),
                tint = Color(0xFF4A90E2)
            )
        }

        Text(
            text = monthFormat.format(currentMonth.time),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF424242)
        )

        IconButton(
            onClick = onNextMonth,
            enabled = !isCurrentMonth
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Next month",
                modifier = Modifier.size(32.dp),
                tint = if (isCurrentMonth) Color.Gray else Color(0xFF4A90E2)
            )
        }
    }
}

@Composable
fun CalendarGrid(
    currentMonth: java.util.Calendar,
    medications: List<com.medreminder.app.data.Medication>,
    historyList: List<com.medreminder.app.data.MedicationHistory>,
    weekStartDay: String = "sunday",
    includeSkipped: Boolean = false,
    onDayClick: ((java.util.Calendar) -> Unit)? = null
) {
    val daysInMonth = currentMonth.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val firstDayOfMonth = (currentMonth.clone() as java.util.Calendar).apply {
        set(java.util.Calendar.DAY_OF_MONTH, 1)
    }

    // Adjust day labels and first day based on week start preference
    val dayLabels = if (weekStartDay == "monday") {
        listOf("M", "T", "W", "T", "F", "S", "S") // Monday-Sunday
    } else {
        listOf("S", "M", "T", "W", "T", "F", "S") // Sunday-Saturday
    }

    // Get first day of week (0-6) and adjust for week start preference
    val rawFirstDay = firstDayOfMonth.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
    val firstDayOfWeek = if (weekStartDay == "monday") {
        // Shift: Sunday (0) becomes 6, Monday (1) becomes 0, etc.
        if (rawFirstDay == 0) 6 else rawFirstDay - 1
    } else {
        rawFirstDay
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Day of week headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dayLabels.forEach { label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray
                    )
                }
            }
        }

        // Calendar days grid
        var dayCounter = 1
        val weeks = (daysInMonth + firstDayOfWeek + 6) / 7 // Calculate number of weeks needed

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(weeks) { weekIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(7) { dayOfWeek ->
                        val dayIndex = weekIndex * 7 + dayOfWeek
                        val shouldShowDay = dayIndex >= firstDayOfWeek && dayCounter <= daysInMonth

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (shouldShowDay) {
                                val dayCal = (currentMonth.clone() as java.util.Calendar).apply {
                                    set(java.util.Calendar.DAY_OF_MONTH, dayCounter)
                                    set(java.util.Calendar.HOUR_OF_DAY, 12)
                                    set(java.util.Calendar.MINUTE, 0)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }

                                val state = calculateDayAdherenceState(
                                    dayTimestamp = dayCal.timeInMillis,
                                    medications = medications,
                                    historyList = historyList,
                                    includeSkipped = includeSkipped
                                )

                                DayCell(
                                    day = dayCounter,
                                    state = state,
                                    isToday = isToday(dayCal),
                                    onClick = {
                                        onDayClick?.invoke(dayCal)
                                    }
                                )
                                dayCounter++
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(
    day: Int,
    state: DayAdherenceState,
    isToday: Boolean,
    onClick: () -> Unit = {}
) {
    val backgroundColor = when (state) {
        is DayAdherenceState.NotScheduled -> Color(0xFFE0E0E0)
        is DayAdherenceState.NoData -> Color(0xFFF5F5F5)
        is DayAdherenceState.Complete -> Color(0xFF4CAF50)
        is DayAdherenceState.Partial -> Color(0xFFFFA726)
        is DayAdherenceState.Poor -> Color(0xFFEF5350)
    }

    val borderColor = if (isToday) Color(0xFF4A90E2) else Color.Transparent

    Card(
        modifier = Modifier
            .size(44.dp)
            .padding(2.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (isToday) BorderStroke(2.dp, borderColor) else null,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.toString(),
                fontSize = 14.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when (state) {
                    is DayAdherenceState.NotScheduled -> Color.Gray
                    is DayAdherenceState.NoData -> Color.Gray
                    else -> Color.White
                }
            )
        }
    }
}

@Composable
fun CalendarLegend() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Legend",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF424242)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendItem(
                    color = Color(0xFF4CAF50),
                    label = "All taken",
                    modifier = Modifier.weight(1f)
                )
                LegendItem(
                    color = Color(0xFFFFA726),
                    label = "Some missed",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendItem(
                    color = Color(0xFFEF5350),
                    label = "Many missed",
                    modifier = Modifier.weight(1f)
                )
                LegendItem(
                    color = Color(0xFFE0E0E0),
                    label = "Not scheduled",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

/**
 * Calculate adherence percentage based on setting
 * @param takenCount Number of doses taken
 * @param totalCount Total number of doses (including skipped if setting is enabled)
 * @param skippedCount Number of doses skipped
 * @param includeSkipped Whether to count skipped doses as missed in adherence calculation
 */
fun calculateAdherencePercentage(
    takenCount: Int,
    totalCount: Int,
    skippedCount: Int,
    includeSkipped: Boolean
): Int {
    if (totalCount == 0) return 0

    return if (includeSkipped) {
        // Skipped counts against adherence - use all entries
        ((takenCount.toFloat() / totalCount) * 100).toInt()
    } else {
        // Skipped doesn't count - exclude from denominator
        val effectiveTotal = totalCount - skippedCount
        if (effectiveTotal == 0) return 0
        ((takenCount.toFloat() / effectiveTotal) * 100).toInt()
    }
}

/**
 * Calculate adherence state for a specific day
 */
fun calculateDayAdherenceState(
    dayTimestamp: Long,
    medications: List<com.medreminder.app.data.Medication>,
    historyList: List<com.medreminder.app.data.MedicationHistory>,
    includeSkipped: Boolean = false
): DayAdherenceState {
    // Filter medications that existed on this day
    val activeMedications = medications.filter { med ->
        med.createdAt <= dayTimestamp
    }

    if (activeMedications.isEmpty()) {
        return DayAdherenceState.NotScheduled
    }

    // Get history entries for this specific day
    val dayHistory = historyList.filter { history ->
        isSameDay(history.scheduledTime, dayTimestamp)
    }

    if (dayHistory.isEmpty()) {
        return DayAdherenceState.NoData
    }

    // Calculate adherence percentage
    val takenCount = dayHistory.count { it.action == "TAKEN" }
    val skippedCount = dayHistory.count { it.action == "SKIPPED" }
    val totalCount = dayHistory.size
    val percentage = calculateAdherencePercentage(takenCount, totalCount, skippedCount, includeSkipped)

    return when {
        percentage == 100 -> DayAdherenceState.Complete(percentage)
        percentage >= 50 -> DayAdherenceState.Partial(percentage)
        else -> DayAdherenceState.Poor(percentage)
    }
}

/**
 * Check if two timestamps are on the same day
 */
fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = timestamp1 }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = timestamp2 }

    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
            cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}

/**
 * Check if a calendar date is today
 */
fun isToday(calendar: java.util.Calendar): Boolean {
    val today = java.util.Calendar.getInstance()
    return calendar.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
            calendar.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
}

/**
 * Time range options for trends view
 */
enum class TrendsTimeRange(val days: Int, val label: String) {
    WEEK_1(7, "7 Days"),
    WEEKS_2(14, "14 Days"),
    MONTH_1(30, "30 Days"),
    MONTHS_3(90, "90 Days")
}

/**
 * Trends Graph View - Shows adherence trends over time
 */
@Composable
fun TrendsGraphView(currentLanguage: String) {
    val context = LocalContext.current
    val database = remember { MedicationDatabase.getDatabase(context) }
    val historyDao = remember { database.historyDao() }
    val medicationDao = remember { database.medicationDao() }

    // Get active profile ID
    val activeProfileId by SettingsStore.activeProfileIdFlow(context)
        .collectAsState(initial = null)

    // Get include skipped in adherence setting
    val includeSkipped by SettingsStore.includeSkippedInAdherenceFlow(context)
        .collectAsState(initial = false)

    // State for time range selection
    var selectedRange by remember { mutableStateOf(TrendsTimeRange.MONTH_1) }

    // Get medications for the active profile
    val medications by medicationDao.getMedicationsByProfile(activeProfileId ?: 1L)
        .collectAsState(initial = emptyList())

    // Calculate date range based on selected time range
    val dateRange = remember(selectedRange) {
        val endCal = java.util.Calendar.getInstance()
        endCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        endCal.set(java.util.Calendar.MINUTE, 59)
        endCal.set(java.util.Calendar.SECOND, 59)
        endCal.set(java.util.Calendar.MILLISECOND, 999)

        val startCal = java.util.Calendar.getInstance()
        startCal.add(java.util.Calendar.DAY_OF_YEAR, -(selectedRange.days - 1))
        startCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        startCal.set(java.util.Calendar.MINUTE, 0)
        startCal.set(java.util.Calendar.SECOND, 0)
        startCal.set(java.util.Calendar.MILLISECOND, 0)

        startCal.timeInMillis to endCal.timeInMillis
    }

    // Get history for selected time range
    val historyList by historyDao.getHistoryForDateRangeByProfile(
        profileId = activeProfileId ?: 1L,
        startTime = dateRange.first,
        endTime = dateRange.second
    ).collectAsState(initial = emptyList())

    // Calculate daily adherence data
    val dailyAdherence = remember(historyList, medications, selectedRange, includeSkipped, dateRange) {
        calculateDailyAdherence(
            historyList = historyList,
            medications = medications,
            days = selectedRange.days,
            startTime = dateRange.first,
            includeSkipped = includeSkipped
        )
    }

    // Calculate insights
    val insights = remember(dailyAdherence) {
        calculateTrendInsights(dailyAdherence)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Time range selector
        TimeRangeSelector(
            selectedRange = selectedRange,
            onRangeSelected = { selectedRange = it }
        )

        // Main chart
        if (dailyAdherence.isNotEmpty()) {
            AdherenceTrendChart(
                data = dailyAdherence,
                timeRange = selectedRange
            )

            // Insights card
            TrendInsightsCard(insights = insights)
        } else {
            // Empty state
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Text(
                            text = "Your journey starts here!",
                            fontSize = 16.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Track your medications to see your progress",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangeSelector(
    selectedRange: TrendsTimeRange,
    onRangeSelected: (TrendsTimeRange) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TrendsTimeRange.entries.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.label, fontSize = 14.sp) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AdherenceTrendChart(
    data: List<DailyAdherenceData>,
    timeRange: TrendsTimeRange
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Your Progress Over Time",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF424242)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Calculate average
            val avgAdherence = data.mapNotNull { it.percentage }.average()

            Text(
                text = "You're doing ${avgAdherence.toInt()}% great!",
                fontSize = 14.sp,
                color = when {
                    avgAdherence >= 90 -> Color(0xFF4CAF50)
                    avgAdherence >= 70 -> Color(0xFFFFA726)
                    else -> Color(0xFF4A90E2)
                },
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Chart canvas
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val width = size.width
                val height = size.height
                val padding = 40f
                val bottomPadding = 60f // Extra padding for X-axis labels

                // Draw grid lines
                val gridColor = Color(0xFFE0E0E0)
                val gridLines = 5
                for (i in 0..gridLines) {
                    val y = padding + (height - padding - bottomPadding) * i / gridLines
                    drawLine(
                        color = gridColor,
                        start = androidx.compose.ui.geometry.Offset(padding, y),
                        end = androidx.compose.ui.geometry.Offset(width - padding, y),
                        strokeWidth = 1f
                    )
                }

                // Draw Y-axis labels (percentages)
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.RIGHT
                }

                for (i in 0..gridLines) {
                    val percentage = 100 - (i * 20)
                    val y = padding + (height - padding - bottomPadding) * i / gridLines
                    drawContext.canvas.nativeCanvas.drawText(
                        "$percentage%",
                        padding - 10f,
                        y + 8f,
                        textPaint
                    )
                }

                // Draw X-axis labels (dates)
                val xAxisTextPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                // Determine how many labels to show based on data size
                val labelCount = when {
                    data.size <= 7 -> data.size // Show all for 7 days or less
                    data.size <= 14 -> 7 // Show 7 labels for 14 days
                    data.size <= 30 -> 10 // Show 10 labels for 30 days
                    else -> 12 // Show 12 labels for 90 days
                }

                val labelInterval = if (labelCount > 0) (data.size - 1) / (labelCount - 1).coerceAtLeast(1) else 1

                data.forEachIndexed { index, dataPoint ->
                    if (index % labelInterval == 0 || index == data.size - 1) {
                        val x = padding + (width - 2 * padding) * index / (data.size - 1).coerceAtLeast(1)
                        val chartBottom = height - bottomPadding

                        // Format date based on time range
                        val dateFormatter = java.text.SimpleDateFormat(
                            when (timeRange) {
                                TrendsTimeRange.WEEK_1 -> "EEE" // Mon, Tue, etc.
                                TrendsTimeRange.WEEKS_2 -> "M/d" // 1/15
                                TrendsTimeRange.MONTH_1 -> "M/d" // 1/15
                                TrendsTimeRange.MONTHS_3 -> "M/d" // 1/15
                            },
                            java.util.Locale.getDefault()
                        )
                        val dateLabel = dateFormatter.format(java.util.Date(dataPoint.date))

                        // Draw X-axis tick mark
                        drawLine(
                            color = Color.Gray,
                            start = androidx.compose.ui.geometry.Offset(x, chartBottom),
                            end = androidx.compose.ui.geometry.Offset(x, chartBottom + 8f),
                            strokeWidth = 2f
                        )

                        // Draw date label
                        drawContext.canvas.nativeCanvas.drawText(
                            dateLabel,
                            x,
                            chartBottom + 30f,
                            xAxisTextPaint
                        )
                    }
                }

                // Draw line chart
                if (data.size > 1) {
                    val points = data.mapIndexedNotNull { index, dataPoint ->
                        dataPoint.percentage?.let { percentage ->
                            val x = padding + (width - 2 * padding) * index / (data.size - 1)
                            val y = padding + (height - padding - bottomPadding) * (100 - percentage) / 100
                            androidx.compose.ui.geometry.Offset(x, y)
                        }
                    }

                    // Draw line connecting points
                    if (points.size > 1) {
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            path.lineTo(points[i].x, points[i].y)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF4A90E2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                        )
                    }

                    // Draw points
                    points.forEach { point ->
                        drawCircle(
                            color = Color(0xFF4A90E2),
                            radius = 6f,
                            center = point
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3f,
                            center = point
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrendInsightsCard(insights: TrendInsights) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "What We Noticed",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF424242)
            )

            // Trend direction
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (insights.trend) {
                        Trend.IMPROVING -> Icons.Default.TrendingUp
                        Trend.DECLINING -> Icons.Default.TrendingDown
                        Trend.STABLE -> Icons.Default.TrendingFlat
                    },
                    contentDescription = null,
                    tint = when (insights.trend) {
                        Trend.IMPROVING -> Color(0xFF4CAF50)
                        Trend.DECLINING -> Color(0xFFEF5350)
                        Trend.STABLE -> Color(0xFFFFA726)
                    },
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = when (insights.trend) {
                        Trend.IMPROVING -> "You're getting better!"
                        Trend.DECLINING -> "Let's get back on track"
                        Trend.STABLE -> "You're staying consistent!"
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF424242)
                )
            }

            // Best/worst day
            if (insights.bestDayOfWeek != null) {
                Text(
                    text = "⭐ You rock on ${insights.bestDayOfWeek}s!",
                    fontSize = 14.sp,
                    color = Color(0xFF4CAF50)
                )
            }

            if (insights.worstDayOfWeek != null) {
                Text(
                    text = "💡 ${insights.worstDayOfWeek}s need a little attention",
                    fontSize = 14.sp,
                    color = Color(0xFFFFA726)
                )
            }

            // Current streak
            if (insights.currentStreak > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🔥",
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${insights.currentStreak}-day streak!",
                        fontSize = 14.sp,
                        color = Color(0xFF424242),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Data class for daily adherence
 */
data class DailyAdherenceData(
    val date: Long,
    val percentage: Int?  // Null if no data for that day
)

/**
 * Trend direction enum
 */
enum class Trend {
    IMPROVING,
    DECLINING,
    STABLE
}

/**
 * Insights data class
 */
data class TrendInsights(
    val trend: Trend,
    val bestDayOfWeek: String?,
    val worstDayOfWeek: String?,
    val currentStreak: Int
)

/**
 * Calculate daily adherence percentages
 */
fun calculateDailyAdherence(
    historyList: List<com.medreminder.app.data.MedicationHistory>,
    medications: List<com.medreminder.app.data.Medication>,
    days: Int,
    startTime: Long,
    includeSkipped: Boolean = false
): List<DailyAdherenceData> {
    val dailyData = mutableListOf<DailyAdherenceData>()

    for (i in 0 until days) {
        val dayCal = java.util.Calendar.getInstance()
        dayCal.timeInMillis = startTime
        dayCal.add(java.util.Calendar.DAY_OF_YEAR, i)
        dayCal.set(java.util.Calendar.HOUR_OF_DAY, 12)
        dayCal.set(java.util.Calendar.MINUTE, 0)
        dayCal.set(java.util.Calendar.SECOND, 0)
        dayCal.set(java.util.Calendar.MILLISECOND, 0)

        val dayTimestamp = dayCal.timeInMillis

        // Filter medications that existed on this day
        val activeMedications = medications.filter { med ->
            med.createdAt <= dayTimestamp
        }

        if (activeMedications.isEmpty()) {
            dailyData.add(DailyAdherenceData(dayTimestamp, null))
            continue
        }

        // Get history for this day
        val dayHistory = historyList.filter { history ->
            isSameDay(history.scheduledTime, dayTimestamp)
        }

        if (dayHistory.isEmpty()) {
            dailyData.add(DailyAdherenceData(dayTimestamp, null))
            continue
        }

        // Calculate percentage using centralized function
        val takenCount = dayHistory.count { it.action == "TAKEN" }
        val skippedCount = dayHistory.count { it.action == "SKIPPED" }
        val totalCount = dayHistory.size
        val percentage = calculateAdherencePercentage(takenCount, totalCount, skippedCount, includeSkipped)

        dailyData.add(DailyAdherenceData(dayTimestamp, percentage))
    }

    return dailyData
}

/**
 * Calculate trend insights from daily adherence data
 */
fun calculateTrendInsights(data: List<DailyAdherenceData>): TrendInsights {
    val validData = data.mapNotNull { it.percentage }

    if (validData.isEmpty()) {
        return TrendInsights(Trend.STABLE, null, null, 0)
    }

    // Calculate trend (compare first half to second half)
    val trend = if (validData.size >= 4) {
        val midpoint = validData.size / 2
        val firstHalf = validData.take(midpoint).average()
        val secondHalf = validData.takeLast(midpoint).average()

        when {
            secondHalf > firstHalf + 5 -> Trend.IMPROVING
            secondHalf < firstHalf - 5 -> Trend.DECLINING
            else -> Trend.STABLE
        }
    } else {
        Trend.STABLE
    }

    // Calculate best/worst day of week
    val dayOfWeekData = data.filter { it.percentage != null }.groupBy {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = it.date
        cal.get(java.util.Calendar.DAY_OF_WEEK)
    }

    val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    val bestDay = dayOfWeekData.maxByOrNull { (_, list) ->
        list.mapNotNull { it.percentage }.average()
    }?.let { dayNames[it.key - 1] }

    val worstDay = dayOfWeekData.minByOrNull { (_, list) ->
        list.mapNotNull { it.percentage }.average()
    }?.let { dayNames[it.key - 1] }

    // Calculate current streak (100% days from the end)
    var streak = 0
    for (i in data.size - 1 downTo 0) {
        if (data[i].percentage == 100) {
            streak++
        } else {
            break
        }
    }

    return TrendInsights(
        trend = trend,
        bestDayOfWeek = if (dayOfWeekData.size > 1) bestDay else null,
        worstDayOfWeek = if (dayOfWeekData.size > 1 && bestDay != worstDay) worstDay else null,
        currentStreak = streak
    )
}

/**
 * By Medication Detail View - Shows per-medication statistics
 */
@Composable
fun ByMedicationDetailView(currentLanguage: String) {
    val context = LocalContext.current
    val database = remember { MedicationDatabase.getDatabase(context) }
    val historyDao = remember { database.historyDao() }
    val medicationDao = remember { database.medicationDao() }

    // Get active profile ID
    val activeProfileId by SettingsStore.activeProfileIdFlow(context)
        .collectAsState(initial = null)

    // Get all medications
    val medications by medicationDao.getMedicationsByProfile(activeProfileId ?: 1L)
        .collectAsState(initial = emptyList())

    // State for selected medication
    var selectedMedication by remember { mutableStateOf<com.medreminder.app.data.Medication?>(null) }

    // Initialize with first medication if available
    LaunchedEffect(medications) {
        if (selectedMedication == null && medications.isNotEmpty()) {
            selectedMedication = medications.first()
        }
    }

    // Calculate date range (last 30 days)
    val dateRange = remember {
        val endCal = java.util.Calendar.getInstance()
        endCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        endCal.set(java.util.Calendar.MINUTE, 59)
        endCal.set(java.util.Calendar.SECOND, 59)
        endCal.set(java.util.Calendar.MILLISECOND, 999)

        val startCal = java.util.Calendar.getInstance()
        startCal.add(java.util.Calendar.DAY_OF_YEAR, -29)
        startCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        startCal.set(java.util.Calendar.MINUTE, 0)
        startCal.set(java.util.Calendar.SECOND, 0)
        startCal.set(java.util.Calendar.MILLISECOND, 0)

        startCal.timeInMillis to endCal.timeInMillis
    }

    // Get history for all medications in date range
    val allHistory by historyDao.getHistoryForDateRangeByProfile(
        profileId = activeProfileId ?: 1L,
        startTime = dateRange.first,
        endTime = dateRange.second
    ).collectAsState(initial = emptyList())

    // Filter history for selected medication
    val medicationHistory = remember(selectedMedication, allHistory) {
        selectedMedication?.let { med ->
            allHistory.filter { it.medicationId == med.id }
        } ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (medications.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Text(
                        text = "No medications yet",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "Add your first medication to start tracking!",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Medication selector dropdown
            MedicationDropdownSelector(
                medications = medications,
                selectedMedication = selectedMedication,
                onMedicationSelected = { selectedMedication = it }
            )

            selectedMedication?.let { medication ->
                // Medication details card
                MedicationDetailsCard(
                    medication = medication,
                    history = medicationHistory
                )

                // Statistics card
                MedicationStatisticsCard(
                    medication = medication,
                    history = medicationHistory
                )

                // Recent history list
                RecentHistoryList(
                    history = medicationHistory.sortedByDescending { it.scheduledTime }.take(10)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDropdownSelector(
    medications: List<com.medreminder.app.data.Medication>,
    selectedMedication: com.medreminder.app.data.Medication?,
    onMedicationSelected: (com.medreminder.app.data.Medication) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedMedication?.name ?: "Select Medication",
            onValueChange = {},
            readOnly = true,
            label = { Text("Medication") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            medications.forEach { medication ->
                DropdownMenuItem(
                    text = { Text(medication.name) },
                    onClick = {
                        onMedicationSelected(medication)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun MedicationDetailsCard(
    medication: com.medreminder.app.data.Medication,
    history: List<com.medreminder.app.data.MedicationHistory>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Medication photo
            if (medication.photoUri != null) {
                Card(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(android.net.Uri.parse(medication.photoUri)),
                        contentDescription = medication.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFE3F2FD), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color(0xFF4A90E2)
                    )
                }
            }

            // Medication info
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = medication.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF424242)
                )

                // Added date
                val dateFormat = remember { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()) }
                Text(
                    text = "Added: ${dateFormat.format(java.util.Date(medication.createdAt))}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                // Schedule
                medication.reminderTimesJson?.let { timesJson ->
                    if (timesJson.isNotBlank() && timesJson != "[]") {
                        Text(
                            text = parseReminderTimesForDisplay(timesJson),
                            fontSize = 14.sp,
                            color = Color(0xFF4A90E2)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MedicationStatisticsCard(
    medication: com.medreminder.app.data.Medication,
    history: List<com.medreminder.app.data.MedicationHistory>
) {
    // Calculate statistics
    val takenCount = history.count { it.action == "TAKEN" }
    val missedCount = history.count { it.action == "MISSED" }
    val skippedCount = history.count { it.action == "SKIPPED" }
    val totalCount = history.size
    val adherencePercentage = if (totalCount > 0) {
        ((takenCount.toFloat() / totalCount) * 100).toInt()
    } else 0

    // Calculate streak
    val currentStreak = calculateMedicationStreak(history)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Your Progress (Last 30 Days)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF424242)
            )

            // Large adherence percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$adherencePercentage%",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        adherencePercentage >= 90 -> Color(0xFF4CAF50)
                        adherencePercentage >= 70 -> Color(0xFFFFA726)
                        else -> Color(0xFFEF5350)
                    }
                )
            }

            Text(
                text = "Success Score",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(
                    statusType = MedicationStatus.Type.TAKEN_ON_TIME,
                    value = takenCount.toString(),
                    label = "Taken"
                )
                StatColumn(
                    statusType = MedicationStatus.Type.MISSED,
                    value = missedCount.toString(),
                    label = "Missed"
                )
                StatColumn(
                    statusType = MedicationStatus.Type.SKIPPED,
                    value = skippedCount.toString(),
                    label = "Skipped"
                )
            }

            // Streak
            if (currentStreak > 0) {
                Divider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$currentStreak-day streak!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF424242)
                    )
                }
            }
        }
    }
}

@Composable
fun StatColumn(
    statusType: MedicationStatus.Type,
    value: String,
    label: String
) {
    val statusInfo = MedicationStatus.getStatusInfo(statusType)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(statusInfo.color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = statusInfo.icon,
                contentDescription = statusInfo.label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = statusInfo.color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun RecentHistoryList(
    history: List<com.medreminder.app.data.MedicationHistory>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Recent History",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF424242),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (history.isEmpty()) {
                Text(
                    text = "No history yet",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEach { historyEntry ->
                        HistoryEntryRow(historyEntry)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryEntryRow(entry: com.medreminder.app.data.MedicationHistory) {
    val dateFormat = remember { java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = dateFormat.format(java.util.Date(entry.scheduledTime)),
                fontSize = 14.sp,
                color = Color(0xFF424242)
            )
            if (entry.action == "TAKEN" && !entry.wasOnTime) {
                val latenessInfo = com.medreminder.app.utils.TimeUtils.formatLateness(
                    scheduledTime = entry.scheduledTime,
                    takenTime = entry.takenTime,
                    context = androidx.compose.ui.platform.LocalContext.current,
                    onTimeThresholdMinutes = 5
                )
                latenessInfo?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = Color(0xFFFFA726)
                    )
                }
            }
        }

        // Action badge using centralized status
        val statusInfo = MedicationStatus.getStatusInfo(entry.action, entry.wasOnTime)

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusInfo.icon,
                contentDescription = statusInfo.label,
                tint = statusInfo.color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = statusInfo.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusInfo.color
            )
        }
    }
}

/**
 * Calculate current streak for a medication (consecutive 100% days)
 */
fun calculateMedicationStreak(history: List<com.medreminder.app.data.MedicationHistory>): Int {
    // Group history by day
    val historyByDay = history.groupBy { entry ->
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = entry.scheduledTime
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }.toSortedMap(compareByDescending { it })

    var streak = 0
    for ((_, dayEntries) in historyByDay) {
        val allTaken = dayEntries.all { it.action == "TAKEN" }
        if (allTaken && dayEntries.isNotEmpty()) {
            streak++
        } else {
            break
        }
    }

    return streak
}

/**
 * Day Detail Screen - Shows all medication instances for a specific date
 * Displays detailed information about each scheduled medication and its status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    selectedDate: java.util.Calendar,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { MedicationDatabase.getDatabase(context) }
    val medicationDao = remember { db.medicationDao() }
    val historyDao = remember { db.historyDao() }

    // Get active profile ID
    val activeProfileId by SettingsStore.activeProfileIdFlow(context)
        .collectAsState(initial = null)

    // Format date
    val dateFormat = remember { java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()) }
    val dateString = dateFormat.format(selectedDate.time)

    // Get start and end of selected day
    val dayStart = remember(selectedDate) {
        (selectedDate.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val dayEnd = remember(selectedDate) {
        (selectedDate.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    // Get all medications for the profile
    val medications by medicationDao.getMedicationsByProfile(activeProfileId ?: 1L)
        .collectAsState(initial = emptyList())

    // Get history for the selected day
    val historyList by historyDao.getHistoryForDateRangeByProfile(
        profileId = activeProfileId ?: 1L,
        startTime = dayStart,
        endTime = dayEnd
    ).collectAsState(initial = emptyList())

    // Group history by medication
    val historyByMedication = remember(historyList, medications) {
        historyList.groupBy { history ->
            medications.find { it.id == history.medicationId }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medication Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF4A90E2),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Date header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Text(
                    text = dateString,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF424242),
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Show message if no data
            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No medication history for this day",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Display history grouped by medication
                historyByMedication.entries.forEach { (medication, entries) ->
                    if (medication != null) {
                        MedicationHistorySection(
                            medication = medication,
                            historyEntries = entries.sortedBy { it.scheduledTime }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Display a single medication's history entries for the day
 */
@Composable
fun MedicationHistorySection(
    medication: com.medreminder.app.data.Medication,
    historyEntries: List<com.medreminder.app.data.MedicationHistory>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Medication name header
            Text(
                text = medication.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF424242)
            )

            // Dosage info if available
            if (!medication.dosage.isNullOrEmpty()) {
                Text(
                    text = medication.dosage ?: "",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Divider(color = Color(0xFFE0E0E0))

            // Display each history entry
            historyEntries.forEach { entry ->
                HistoryEntryItem(entry = entry)
            }
        }
    }
}

/**
 * Display a single history entry with detailed information
 */
@Composable
fun HistoryEntryItem(entry: com.medreminder.app.data.MedicationHistory) {
    val timeFormat = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
    val scheduledTimeStr = timeFormat.format(java.util.Date(entry.scheduledTime))
    val takenTimeStr = if (entry.takenTime > 0) {
        timeFormat.format(java.util.Date(entry.takenTime))
    } else {
        "-"
    }

    // Calculate time difference
    val timeDifference = if (entry.action == "TAKEN" && entry.takenTime > 0) {
        val diffMinutes = ((entry.takenTime - entry.scheduledTime) / (1000 * 60)).toInt()
        when {
            diffMinutes == 0 -> "On time"
            diffMinutes > 0 -> {
                val hours = diffMinutes / 60
                val minutes = diffMinutes % 60
                when {
                    hours > 0 && minutes > 0 -> "$hours hr $minutes min late"
                    hours > 0 -> "$hours hr late"
                    else -> "$minutes min late"
                }
            }
            else -> {
                val absMinutes = kotlin.math.abs(diffMinutes)
                val hours = absMinutes / 60
                val minutes = absMinutes % 60
                when {
                    hours > 0 && minutes > 0 -> "$hours hr $minutes min early"
                    hours > 0 -> "$hours hr early"
                    else -> "$minutes min early"
                }
            }
        }
    } else {
        null
    }

    // Status badge using centralized status
    val statusInfo = MedicationStatus.getStatusInfo(entry.action, entry.wasOnTime)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Scheduled time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0xFF4A90E2),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Scheduled: $scheduledTimeStr",
                    fontSize = 14.sp,
                    color = Color(0xFF424242)
                )
            }

            // Taken time if applicable
            if (entry.action == "TAKEN") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Taken: $takenTimeStr",
                        fontSize = 14.sp,
                        color = Color(0xFF424242)
                    )
                }

                // Time difference
                if (timeDifference != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = timeDifference,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Status badge
        Surface(
            color = statusInfo.color,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = statusInfo.label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
