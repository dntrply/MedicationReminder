package com.medreminder.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medreminder.app.data.Medication
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.data.MedicationHistory
import com.medreminder.app.data.Profile
import com.medreminder.app.data.TranscriptionStats
import com.medreminder.app.notifications.PendingMedicationTracker
import com.medreminder.app.data.SettingsStore
import com.medreminder.app.data.PresetTimes
import com.medreminder.app.data.PresetTimesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugDataScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var medications by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var history by remember { mutableStateOf<List<MedicationHistory>>(emptyList()) }
    var pendingMeds by remember { mutableStateOf<List<PendingMedicationTracker.PendingMedication>>(emptyList()) }
    var transcriptionStats by remember { mutableStateOf<List<TranscriptionStats>>(emptyList()) }
    var activeProfileId by remember { mutableStateOf<Long?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteTranscriptionStatsDialog by remember { mutableStateOf(false) }

    // Get app version
    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    // Preferences (DataStore)
    val currentLanguage by SettingsStore.languageFlow(context).collectAsState(initial = "en")
    val presetTimes by PresetTimesManager.getPresetTimesFlow(context).collectAsState(initial = PresetTimes())
    val repeatMinutes by SettingsStore.repeatIntervalFlow(context).collectAsState(initial = 10)

    // Collapsible section state
    var profilesExpanded by remember { mutableStateOf(true) }
    var prefsExpanded by remember { mutableStateOf(true) }
    var medsExpanded by remember { mutableStateOf(true) }
    var historyExpanded by remember { mutableStateOf(true) }
    var pendingExpanded by remember { mutableStateOf(true) }
    var transcriptionStatsExpanded by remember { mutableStateOf(true) }

    val loadData: () -> Unit = {
        isLoading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = MedicationDatabase.getDatabase(context)
                profiles = db.profileDao().getAllProfiles().first()
                activeProfileId = SettingsStore.activeProfileIdFlow(context).first()
                medications = db.medicationDao().getAllMedicationsSync()
                history = db.historyDao().getAllHistorySync()
                pendingMeds = PendingMedicationTracker.getPendingMedications(context)
                transcriptionStats = db.transcriptionStatsDao().getAllStats().first()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Debug Data Viewer")
                        Text(
                            "v$appVersion",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                                    val timestamp = dateFormat.format(Date())
                                    val exportFile = File(context.getExternalFilesDir(null), "debug_export_$timestamp.json")

                                    val json = buildString {
                                        appendLine("{")
                                        appendLine("  \"medications\": [")
                                        medications.forEachIndexed { index, med ->
                                            appendLine("    {")
                                            appendLine("      \"id\": ${med.id},")
                                            appendLine("      \"name\": \"${med.name}\",")
                                            appendLine("      \"photoUri\": \"${med.photoUri}\",")
                                            appendLine("      \"audioNotePath\": \"${med.audioNotePath}\",")
                                            appendLine("      \"audioTranscription\": \"${med.audioTranscription}\",")
                                            appendLine("      \"audioTranscriptionLanguage\": \"${med.audioTranscriptionLanguage}\",")
                                            appendLine("      \"reminderTimesJson\": ${med.reminderTimesJson ?: "null"}")
                                            append("    }")
                                            if (index < medications.size - 1) appendLine(",")
                                            else appendLine()
                                        }
                                        appendLine("  ],")
                                        appendLine("  \"history\": [")
                                        history.forEachIndexed { index, h ->
                                            appendLine("    {")
                                            appendLine("      \"id\": ${h.id},")
                                            appendLine("      \"medicationId\": ${h.medicationId},")
                                            appendLine("      \"medicationName\": \"${h.medicationName}\",")
                                            appendLine("      \"scheduledTime\": ${h.scheduledTime},")
                                            appendLine("      \"takenTime\": ${h.takenTime},")
                                            appendLine("      \"wasOnTime\": ${h.wasOnTime},")
                                            appendLine("      \"action\": \"${h.action}\"")
                                            append("    }")
                                            if (index < history.size - 1) appendLine(",")
                                            else appendLine()
                                        }
                                        appendLine("  ],")
                                        appendLine("  \"pendingMedications\": [")
                                        pendingMeds.forEachIndexed { index, p ->
                                            appendLine("    {")
                                            appendLine("      \"medicationId\": ${p.medicationId},")
                                            appendLine("      \"medicationName\": \"${p.medicationName}\",")
                                            appendLine("      \"hour\": ${p.hour},")
                                            appendLine("      \"minute\": ${p.minute},")
                                            appendLine("      \"timestamp\": ${p.timestamp}")
                                            append("    }")
                                            if (index < pendingMeds.size - 1) appendLine(",")
                                            else appendLine()
                                        }
                                        appendLine("  ]")
                                        appendLine("}")
                                    }

                                    exportFile.writeText(json)
                                    exportMessage = "Exported to: ${exportFile.absolutePath}"
                                } catch (e: Exception) {
                                    exportMessage = "Export failed: ${e.message}"
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pending (collapsible) — moved to top
                item {
                    CollapsibleSectionHeader(
                        title = "PENDING MEDICATIONS (${pendingMeds.size})",
                        expanded = pendingExpanded,
                        onToggle = { pendingExpanded = !pendingExpanded }
                    )
                }
                if (pendingExpanded) {
                    if (pendingMeds.isEmpty()) {
                        item {
                            Card {
                                Text(
                                    "No pending medications",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(pendingMeds) { p ->
                            PendingMedDebugCard(p)
                        }
                    }
                }

                // Transcription Statistics (collapsible)
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TRANSCRIPTION STATISTICS",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (transcriptionStats.isNotEmpty()) {
                            IconButton(
                                onClick = { showDeleteTranscriptionStatsDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete all transcription stats",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = { transcriptionStatsExpanded = !transcriptionStatsExpanded }) {
                            Icon(
                                imageVector = if (transcriptionStatsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (transcriptionStatsExpanded) "Collapse" else "Expand"
                            )
                        }
                    }
                }
                if (transcriptionStatsExpanded) {
                    item {
                        TranscriptionStatsSummary(stats = transcriptionStats)
                    }
                    if (transcriptionStats.isNotEmpty()) {
                        items(transcriptionStats) { stat ->
                            TranscriptionStatCard(stat)
                        }
                    }
                }

                // Profiles (collapsible)
                item {
                    CollapsibleSectionHeader(
                        title = "PROFILES (${profiles.size})",
                        expanded = profilesExpanded,
                        onToggle = { profilesExpanded = !profilesExpanded }
                    )
                }
                if (profilesExpanded) {
                    if (profiles.isEmpty()) {
                        item {
                            Card {
                                Text(
                                    "No profiles in database",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(profiles) { profile ->
                            ProfileDebugCard(profile, profile.id == activeProfileId)
                        }
                    }
                }

                // Medications (collapsible)
                item {
                    CollapsibleSectionHeader(
                        title = "MEDICATIONS (${medications.size})",
                        expanded = medsExpanded,
                        onToggle = { medsExpanded = !medsExpanded }
                    )
                }
                if (medsExpanded) {
                    if (medications.isEmpty()) {
                        item {
                            Card {
                                Text(
                                    "No medications in database",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(medications) { med ->
                            MedicationDebugCard(med)
                        }
                    }
                }

                // History (collapsible)
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "MEDICATION HISTORY (${history.size})",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (history.isNotEmpty()) {
                            IconButton(
                                onClick = { showDeleteHistoryDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete all history",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = { historyExpanded = !historyExpanded }) {
                            Icon(
                                imageVector = if (historyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (historyExpanded) "Collapse" else "Expand"
                            )
                        }
                    }
                }
                if (historyExpanded) {
                    if (history.isEmpty()) {
                        item {
                            Card {
                                Text(
                                    "No history in database",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(history) { h ->
                            HistoryDebugCard(h)
                        }
                    }
                }

                // Preferences (collapsible) — moved to bottom
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    CollapsibleSectionHeader(
                        title = "PREFERENCES",
                        expanded = prefsExpanded,
                        onToggle = { prefsExpanded = !prefsExpanded }
                    )
                }
                if (prefsExpanded) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                DataRow("Language", currentLanguage)
                                DataRow(
                                    "Morning",
                                    String.format("%02d:%02d", presetTimes.morningHour, presetTimes.morningMinute)
                                )
                                DataRow(
                                    "Lunch",
                                    String.format("%02d:%02d", presetTimes.lunchHour, presetTimes.lunchMinute)
                                )
                                DataRow(
                                    "Evening",
                                    String.format("%02d:%02d", presetTimes.eveningHour, presetTimes.eveningMinute)
                                )
                                DataRow(
                                    "Bedtime",
                                    String.format("%02d:%02d", presetTimes.bedtimeHour, presetTimes.bedtimeMinute)
                                )
                                DataRow("Repeat Interval", "$repeatMinutes min")
                            }
                        }
                    }
                }

                // Export message (keep at bottom)
                exportMessage?.let { message ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(16.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete history confirmation dialog
    if (showDeleteHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteHistoryDialog = false },
            title = { Text("Delete All History?") },
            text = { Text("This will permanently delete all ${history.size} history entries. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val db = MedicationDatabase.getDatabase(context)
                                    db.historyDao().deleteAllHistory()
                                    exportMessage = "Deleted ${history.size} history entries"
                                }  catch (e: Exception) {
                                    exportMessage = "Error deleting history: ${e.message}"
                                }
                            }
                            showDeleteHistoryDialog = false
                            loadData() // Reload data
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete transcription stats confirmation dialog
    if (showDeleteTranscriptionStatsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteTranscriptionStatsDialog = false },
            title = { Text("Delete All Transcription Stats?") },
            text = { Text("This will permanently delete all ${transcriptionStats.size} transcription statistics entries. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val db = MedicationDatabase.getDatabase(context)
                                    db.transcriptionStatsDao().deleteAllStats()
                                    exportMessage = "Deleted ${transcriptionStats.size} transcription stats entries"
                                }  catch (e: Exception) {
                                    exportMessage = "Error deleting transcription stats: ${e.message}"
                                }
                            }
                            showDeleteTranscriptionStatsDialog = false
                            loadData() // Reload data
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTranscriptionStatsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand"
        )
    }
}

@Composable
fun ProfileDebugCard(profile: Profile, isActive: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            DataRow("ID", profile.id.toString())
            DataRow("Name", profile.name + if (isActive) " (ACTIVE)" else "")
            DataRow("Photo URI", profile.photoUri ?: "null")
            DataRow("Notification Sound", profile.notificationSoundUri ?: "null")
            DataRow("Message Template", profile.notificationMessageTemplate ?: Profile.DEFAULT_MESSAGE_TEMPLATE)
            DataRow("Is Default", profile.isDefault.toString())
            DataRow("Created At", Date(profile.createdAt).toString())
        }
    }
}

@Composable
fun MedicationDebugCard(med: Medication) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            DataRow("ID", med.id.toString())
            DataRow("Profile ID", med.profileId.toString())
            DataRow("Name", med.name)
            DataRow("Photo URI", med.photoUri ?: "null")
            DataRow("Audio Path", med.audioNotePath ?: "null")
            // Check if audio file exists
            if (med.audioNotePath != null) {
                val audioFile = File(med.audioNotePath)
                val fileInfo = if (audioFile.exists()) {
                    "EXISTS (${audioFile.length()} bytes)"
                } else {
                    "MISSING"
                }
                DataRow("Audio File", fileInfo)
            }
            // Transcription fields
            DataRow("Transcription", med.audioTranscription ?: "null")
            DataRow("Transcription Lang", med.audioTranscriptionLanguage ?: "null")
            DataRow("Reminder Times", med.reminderTimesJson ?: "null")
            DataRow("Created At", Date(med.createdAt).toString())
        }
    }
}

@Composable
fun HistoryDebugCard(h: MedicationHistory) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            DataRow("ID", h.id.toString())
            DataRow("Profile ID", h.profileId.toString())
            DataRow("Medication ID", h.medicationId.toString())
            DataRow("Name", h.medicationName)
            DataRow("Scheduled", Date(h.scheduledTime).toString())
            DataRow("Taken", Date(h.takenTime).toString())
            DataRow("Was On Time", h.wasOnTime.toString())
            DataRow("Action", h.action)
            DataRow("Notes", h.notes ?: "null")
        }
    }
}

@Composable
fun PendingMedDebugCard(p: PendingMedicationTracker.PendingMedication) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            DataRow("Medication ID", p.medicationId.toString())
            DataRow("Profile ID", p.profileId.toString())
            DataRow("Name", p.medicationName)
            DataRow("Time", "${p.hour}:${String.format("%02d", p.minute)}")
            DataRow("Timestamp", Date(p.timestamp).toString())
            DataRow("Issued So Far", (p.repeatCount + 1).toString())
        }
    }
}

@Composable
fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TranscriptionStatsSummary(stats: List<TranscriptionStats>) {
    val successCount = stats.count { it.status == "success" }
    val failedCount = stats.count { it.status == "failed" }
    val pendingCount = stats.count { it.status == "pending" }

    val longestTranscription = stats
        .filter { it.status == "success" }
        .maxByOrNull { it.transcriptionLength ?: 0 }

    val avgDuration = stats
        .filter { it.status == "success" && it.durationMs != null }
        .mapNotNull { it.durationMs }
        .average()
        .takeIf { !it.isNaN() }?.toLong()

    val avgSpeedRatio = stats
        .filter { it.status == "success" && it.processingSpeedRatio != null }
        .mapNotNull { it.processingSpeedRatio }
        .average()
        .takeIf { !it.isNaN() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Summary stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Total", stats.size.toString(), MaterialTheme.colorScheme.primary)
                StatItem("Success", successCount.toString(), androidx.compose.ui.graphics.Color(0xFF4CAF50))
                StatItem("Failed", failedCount.toString(), androidx.compose.ui.graphics.Color(0xFFF44336))
                StatItem("Pending", pendingCount.toString(), androidx.compose.ui.graphics.Color(0xFFFF9800))
            }

            if (successCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Performance metrics
                Text(
                    text = "Performance Metrics",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (longestTranscription != null) {
                    Text(
                        text = "Longest: ${longestTranscription.medicationName} (${longestTranscription.transcriptionLength} chars)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                if (avgDuration != null) {
                    Text(
                        text = "Avg Duration: ${formatDuration(avgDuration)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                if (avgSpeedRatio != null) {
                    Text(
                        text = "Avg Speed Ratio: ${"%.1f".format(avgSpeedRatio)}x realtime",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun TranscriptionStatCard(stat: TranscriptionStats) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())

    val statusColor = when (stat.status) {
        "success" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        "failed" -> androidx.compose.ui.graphics.Color(0xFFF44336)
        "pending" -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with medication name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stat.medicationName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = stat.status.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timing information
            DataRow("Started", dateFormat.format(Date(stat.startTime)))
            if (stat.endTime != null) {
                DataRow("Completed", dateFormat.format(Date(stat.endTime)))
            }
            if (stat.durationMs != null) {
                DataRow("Duration", formatDuration(stat.durationMs))
            }

            // Audio file info
            if (stat.audioDurationSeconds != null) {
                DataRow("Audio Length", "${"%.1f".format(stat.audioDurationSeconds)}s")
            }
            DataRow("File Size", formatFileSize(stat.audioFileSizeBytes))

            // Transcription result
            if (stat.status == "success") {
                if (stat.transcriptionLength != null) {
                    DataRow("Text Length", "${stat.transcriptionLength} chars")
                }
                if (stat.detectedLanguage != null) {
                    DataRow("Language", stat.detectedLanguage)
                }
                if (stat.processingSpeedRatio != null) {
                    DataRow("Speed Ratio", "${"%.1f".format(stat.processingSpeedRatio)}x")
                }
                if (stat.transcriptionText != null && stat.transcriptionText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${stat.transcriptionText.take(100)}${if (stat.transcriptionText.length > 100) "..." else ""}\"",
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else if (stat.status == "failed" && stat.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Error: ${stat.errorMessage}",
                    fontSize = 10.sp,
                    color = androidx.compose.ui.graphics.Color(0xFFF44336)
                )
            }

            // Engine info
            if (stat.engineId != null) {
                DataRow("Engine", stat.engineId)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        "${minutes}m ${remainingSeconds}s"
    } else {
        "${seconds}s"
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}
