package com.medreminder.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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
import com.medreminder.app.notifications.PendingMedicationTracker
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
    var medications by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var history by remember { mutableStateOf<List<MedicationHistory>>(emptyList()) }
    var pendingMeds by remember { mutableStateOf<List<PendingMedicationTracker.PendingMedication>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val loadData: () -> Unit = {
        isLoading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = MedicationDatabase.getDatabase(context)
                medications = db.medicationDao().getAllMedicationsSync()
                history = db.historyDao().getAllHistorySync()
                pendingMeds = PendingMedicationTracker.getPendingMedications(context)
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
                title = { Text("Debug Data Viewer") },
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
                                            appendLine("      \"wasOnTime\": ${h.wasOnTime}")
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

                item {
                    Text(
                        "MEDICATIONS (${medications.size})",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

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

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "MEDICATION HISTORY (${history.size})",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

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

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "PENDING MEDICATIONS (${pendingMeds.size})",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

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
            DataRow("Medication ID", h.medicationId.toString())
            DataRow("Name", h.medicationName)
            DataRow("Scheduled", Date(h.scheduledTime).toString())
            DataRow("Taken", Date(h.takenTime).toString())
            DataRow("Was On Time", h.wasOnTime.toString())
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
            DataRow("Name", p.medicationName)
            DataRow("Time", "${p.hour}:${String.format("%02d", p.minute)}")
            DataRow("Timestamp", Date(p.timestamp).toString())
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
