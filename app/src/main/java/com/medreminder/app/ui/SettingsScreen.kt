package com.medreminder.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medreminder.app.R
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.data.PresetTimes
import com.medreminder.app.data.PresetTimesManager
import com.medreminder.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String,
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current

    val presetsFromStore by PresetTimesManager.getPresetTimesFlow(context)
        .collectAsState(initial = PresetTimes())
    var presets by remember { mutableStateOf(presetsFromStore) }
    LaunchedEffect(presetsFromStore) { presets = presetsFromStore }

    val scope = rememberCoroutineScope()
    fun updatePresets(builder: (PresetTimes) -> PresetTimes) {
        val updated = builder(presets)
        presets = updated
        scope.launch { PresetTimesManager.savePresetTimes(context, updated) }
    }

    // Transcription consent dialog state
    var showTranscriptionConsentDialog by remember { mutableStateOf(false) }

    // Handle back button press
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        color = androidx.compose.ui.graphics.Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                    titleContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language section
            Text(
                text = stringResource(R.string.language_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageOptionCard(
                    label = "English",
                    selected = currentLanguage == "en",
                    onClick = { onLanguageChange("en") }
                )
                LanguageOptionCard(
                    label = "हिन्दी",
                    selected = currentLanguage == "hi",
                    onClick = { onLanguageChange("hi") }
                )
                LanguageOptionCard(
                    label = "ગુજરાતી",
                    selected = currentLanguage == "gu",
                    onClick = { onLanguageChange("gu") }
                )
                LanguageOptionCard(
                    label = "मराठी",
                    selected = currentLanguage == "mr",
                    onClick = { onLanguageChange("mr") }
                )
            }

            Divider(Modifier.padding(vertical = 8.dp))

            // Preset times section (collapsible, default collapsed)
            var presetsExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { presetsExpanded = !presetsExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.preset_times),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                )
                Icon(
                    imageVector = if (presetsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                )
            }

            if (presetsExpanded) {
                // Reuse the existing TimePresetRow from SetReminderTimesScreen
                TimePresetRow(
                    label = stringResource(R.string.morning),
                    hour = presets.morningHour,
                    minute = presets.morningMinute,
                    onHourChange = { newHour -> updatePresets { p -> p.copy(morningHour = newHour) } },
                    onMinuteChange = { newMinute -> updatePresets { p -> p.copy(morningMinute = newMinute) } }
                )

                TimePresetRow(
                    label = stringResource(R.string.lunch),
                    hour = presets.lunchHour,
                    minute = presets.lunchMinute,
                    onHourChange = { newHour -> updatePresets { p -> p.copy(lunchHour = newHour) } },
                    onMinuteChange = { newMinute -> updatePresets { p -> p.copy(lunchMinute = newMinute) } }
                )

                TimePresetRow(
                    label = stringResource(R.string.evening),
                    hour = presets.eveningHour,
                    minute = presets.eveningMinute,
                    onHourChange = { newHour -> updatePresets { p -> p.copy(eveningHour = newHour) } },
                    onMinuteChange = { newMinute -> updatePresets { p -> p.copy(eveningMinute = newMinute) } }
                )

                TimePresetRow(
                    label = stringResource(R.string.bedtime),
                    hour = presets.bedtimeHour,
                    minute = presets.bedtimeMinute,
                    onHourChange = { newHour -> updatePresets { p -> p.copy(bedtimeHour = newHour) } },
                    onMinuteChange = { newMinute -> updatePresets { p -> p.copy(bedtimeMinute = newMinute) } }
                )
            }

            Divider(Modifier.padding(vertical = 8.dp))

            // Notifications: Repeat interval
            Text(
                text = stringResource(R.string.notifications_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )

            val repeatMinutes by SettingsStore.repeatIntervalFlow(context).collectAsState(initial = 10)
            var sliderValue by remember { mutableStateOf(repeatMinutes.toFloat()) }
            LaunchedEffect(repeatMinutes) { sliderValue = repeatMinutes.toFloat() }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.repeat_interval, sliderValue.toInt()),
                    fontSize = 16.sp
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        scope.launch { SettingsStore.setRepeatInterval(context, sliderValue.toInt()) }
                    },
                    valueRange = 2f..120f,
                    steps = 118,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Privacy (single toggle for lock-screen details)
            Text(
                text = stringResource(R.string.privacy_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )

            val showFullOnLock by SettingsStore.showFullOnLockscreenFlow(context)
                .collectAsState(initial = false)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.show_full_details_lock_screen),
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                    Text(
                        text = stringResource(R.string.hidden_by_default),
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
                Switch(
                    checked = showFullOnLock,
                    onCheckedChange = { checked ->
                        scope.launch { SettingsStore.setShowFullOnLockscreen(context, checked) }
                    }
                )
            }

            Divider(Modifier.padding(vertical = 8.dp))

            // Features section
            Text(
                text = stringResource(R.string.features_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )

            val transcriptionEnabled by SettingsStore.transcriptionEnabledFlow(context)
                .collectAsState(initial = false)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audio_transcription),
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                    Text(
                        text = stringResource(R.string.audio_transcription_description),
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
                Switch(
                    checked = transcriptionEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            // User is turning ON - show consent dialog
                            showTranscriptionConsentDialog = true
                        } else {
                            // User is turning OFF - just disable it
                            scope.launch { SettingsStore.setTranscriptionEnabled(context, false) }
                        }
                    }
                )
            }

            Divider(Modifier.padding(vertical = 8.dp))

            // Reports section
            Text(
                text = stringResource(R.string.reports_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )

            // Week start day
            val weekStartDay by SettingsStore.weekStartDayFlow(context).collectAsState(initial = "sunday")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.week_start_day),
                    fontSize = 16.sp,
                    color = androidx.compose.ui.graphics.Color.Black
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WeekStartDayCard(
                        label = stringResource(R.string.week_start_sunday),
                        selected = weekStartDay == "sunday",
                        onClick = { scope.launch { SettingsStore.setWeekStartDay(context, "sunday") } },
                        modifier = Modifier.weight(1f)
                    )
                    WeekStartDayCard(
                        label = stringResource(R.string.week_start_monday),
                        selected = weekStartDay == "monday",
                        onClick = { scope.launch { SettingsStore.setWeekStartDay(context, "monday") } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Default report tab
            val defaultReportTab by SettingsStore.defaultReportTabFlow(context).collectAsState(initial = "summary")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.default_report_tab),
                    fontSize = 16.sp,
                    color = androidx.compose.ui.graphics.Color.Black
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefaultTabCard(
                        label = stringResource(R.string.report_summary),
                        selected = defaultReportTab == "summary",
                        onClick = { scope.launch { SettingsStore.setDefaultReportTab(context, "summary") } }
                    )
                    DefaultTabCard(
                        label = stringResource(R.string.report_calendar),
                        selected = defaultReportTab == "calendar",
                        onClick = { scope.launch { SettingsStore.setDefaultReportTab(context, "calendar") } }
                    )
                    DefaultTabCard(
                        label = stringResource(R.string.report_trends),
                        selected = defaultReportTab == "trends",
                        onClick = { scope.launch { SettingsStore.setDefaultReportTab(context, "trends") } }
                    )
                    DefaultTabCard(
                        label = stringResource(R.string.report_by_medication),
                        selected = defaultReportTab == "by_medication",
                        onClick = { scope.launch { SettingsStore.setDefaultReportTab(context, "by_medication") } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Include skipped in adherence
            val includeSkippedInAdherence by SettingsStore.includeSkippedInAdherenceFlow(context)
                .collectAsState(initial = false)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.include_skipped_in_adherence),
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                    Text(
                        text = stringResource(R.string.include_skipped_description),
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
                Switch(
                    checked = includeSkippedInAdherence,
                    onCheckedChange = { checked ->
                        scope.launch { SettingsStore.setIncludeSkippedInAdherence(context, checked) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Export data button
            var isExporting by remember { mutableStateOf(false) }
            var exportMessage by remember { mutableStateOf<String?>(null) }

            Button(
                onClick = {
                    isExporting = true
                    exportMessage = null
                    scope.launch {
                        try {
                            val fileName = exportHistoryData(context)
                            exportMessage = "Exported to Downloads/$fileName"
                            // Show toast
                            android.widget.Toast.makeText(
                                context,
                                "History exported to Downloads folder",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            exportMessage = "Export failed: ${e.message}"
                            android.util.Log.e("ExportHistory", "Export failed", e)
                            // Show error toast
                            android.widget.Toast.makeText(
                                context,
                                "Export failed: ${e.message}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        isExporting = false
                    }
                },
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                )
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.exporting))
                } else {
                    Text(stringResource(R.string.export_history_csv))
                }
            }

            exportMessage?.let { message ->
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = if (message.contains("success"))
                        androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    else
                        androidx.compose.ui.graphics.Color(0xFFEF5350),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    // Transcription consent dialog
    if (showTranscriptionConsentDialog) {
        TranscriptionConsentDialog(
            currentLanguage = currentLanguage,
            onDismiss = { showTranscriptionConsentDialog = false },
            onAccept = {
                scope.launch {
                    // Save both the feature enabled and consent granted
                    SettingsStore.setTranscriptionEnabled(context, true)
                    SettingsStore.setTranscriptionConsent(context, granted = true)
                }
                showTranscriptionConsentDialog = false
            },
            onDecline = {
                scope.launch {
                    // Keep feature disabled and save that consent was declined
                    SettingsStore.setTranscriptionEnabled(context, false)
                    SettingsStore.setTranscriptionConsent(context, granted = false)
                }
                showTranscriptionConsentDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageOptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                androidx.compose.ui.graphics.Color(0xFF4A90E2)
            else
                androidx.compose.ui.graphics.Color(0xFFF5F5F5)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = if (selected)
                    androidx.compose.ui.graphics.Color.White
                else
                    androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 18.sp,
                color = if (selected)
                    androidx.compose.ui.graphics.Color.White
                else
                    androidx.compose.ui.graphics.Color.Black
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekStartDayCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(50.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                androidx.compose.ui.graphics.Color(0xFF4A90E2)
            else
                androidx.compose.ui.graphics.Color(0xFFF5F5F5)
        ),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected)
                    androidx.compose.ui.graphics.Color.White
                else
                    androidx.compose.ui.graphics.Color.Black
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTabCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                androidx.compose.ui.graphics.Color(0xFF4A90E2)
            else
                androidx.compose.ui.graphics.Color(0xFFF5F5F5)
        ),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected)
                    androidx.compose.ui.graphics.Color.White
                else
                    androidx.compose.ui.graphics.Color.Black
            )
        }
    }
}

/**
 * Export history data to CSV file
 * @return The filename of the exported CSV
 */
private suspend fun exportHistoryData(context: Context): String = withContext(Dispatchers.IO) {
    android.util.Log.d("ExportHistory", "Starting export...")

    val db = MedicationDatabase.getDatabase(context)
    val historyDao = db.historyDao()
    val medicationDao = db.medicationDao()

    // Get active profile ID
    val activeProfileId = SettingsStore.getActiveProfileId(context)
    if (activeProfileId == null) {
        android.util.Log.e("ExportHistory", "No active profile ID")
        throw IllegalStateException("No active profile")
    }

    android.util.Log.d("ExportHistory", "Active profile ID: $activeProfileId")

    // Get all history for active profile (using sync version on IO dispatcher)
    val allHistory: List<com.medreminder.app.data.MedicationHistory> = historyDao.getAllHistorySync()
        .filter { it.profileId == activeProfileId }

    android.util.Log.d("ExportHistory", "Found ${allHistory.size} history entries")

    // Create CSV content
    val csvBuilder = StringBuilder()
    csvBuilder.appendLine("Date,Time,Medication,Scheduled Time,Taken Time,Action,Was On Time")

    allHistory.forEach { entry: com.medreminder.app.data.MedicationHistory ->
        val medicationName = entry.medicationName
        val dateTime = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(entry.takenTime))
        val scheduledTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(entry.scheduledTime))
        val takenTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(entry.takenTime))

        csvBuilder.appendLine("$dateTime,$takenTime,\"$medicationName\",$scheduledTime,$takenTime,${entry.action},${entry.wasOnTime}")
    }

    android.util.Log.d("ExportHistory", "CSV content created, ${csvBuilder.length} chars")

    // Save to Downloads folder
    val contentResolver = context.contentResolver
    val fileName = "medication_history_${System.currentTimeMillis()}.csv"

    android.util.Log.d("ExportHistory", "Attempting to save file: $fileName")

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        // Use MediaStore for Android 10+
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            android.util.Log.d("ExportHistory", "Created URI: $uri")
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(csvBuilder.toString().toByteArray())
                android.util.Log.d("ExportHistory", "File written successfully (Android 10+)")
            } ?: throw java.io.IOException("Failed to open output stream")
        } else {
            throw java.io.IOException("Failed to create MediaStore entry")
        }
    } else {
        // Use legacy approach for older Android versions
        android.util.Log.d("ExportHistory", "Using legacy file approach")
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
            android.util.Log.d("ExportHistory", "Created downloads directory")
        }
        val file = java.io.File(downloadsDir, fileName)
        file.writeText(csvBuilder.toString())
        android.util.Log.d("ExportHistory", "File written successfully (legacy): ${file.absolutePath}")
    }

    android.util.Log.d("ExportHistory", "Export completed successfully")
    return@withContext fileName
}
