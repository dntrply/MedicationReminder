package com.medreminder.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.medreminder.app.notifications.PendingMedicationTracker
import com.medreminder.app.notifications.ReminderBroadcastReceiver
import android.content.Intent

/**
 * Outstanding Medications Screen - Part of the hybrid approach for medication management
 *
 * This dedicated full-screen view shows all pending medications that need acknowledgement.
 * Provides large, elderly-friendly action buttons for taking, snoozing, or skipping medications.
 *
 * Features:
 * - Groups medications by scheduled time
 * - Shows "All caught up!" when no pending medications
 * - Instant UI updates when actions are taken (removes from pending tracker immediately)
 * - Multi-language support
 * - Accessible via main menu: Menu → Medications to Take
 *
 * Complements the timeline quick actions (reaction palette) to provide multiple ways for users
 * to manage their medications based on their preference and context.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutstandingMedicationsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var allPendingMeds by remember { mutableStateOf(PendingMedicationTracker.getPendingMedications(context)) }

    // Group medications by time
    val medicationsByTime = remember(allPendingMeds) {
        allPendingMeds.groupBy { "${it.hour}:${String.format("%02d", it.minute)}" }
            .toList()
            .sortedBy { (time, _) ->
                val parts = time.split(":")
                parts[0].toInt() * 60 + parts[1].toInt()
            }
    }

    // Refresh on composition
    LaunchedEffect(Unit) {
        allPendingMeds = PendingMedicationTracker.getPendingMedications(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Medications to Take",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (medicationsByTime.isEmpty()) {
            // No pending medications
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "All caught up!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "No medications pending",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onBack) {
                        Text("Back to Home", fontSize = 18.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Text(
                        text = "You have ${allPendingMeds.size} medication${if (allPendingMeds.size != 1) "s" else ""} to take",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Show medications grouped by time
                medicationsByTime.forEach { (timeStr, meds) ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Time header
                            Text(
                                text = timeStr,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Medications at this time
                            meds.forEach { med ->
                                OutstandingMedicationCard(
                                    medication = med,
                                    onMarkTaken = {
                                        // Remove from pending tracker immediately for instant UI update
                                        PendingMedicationTracker.removePendingMedication(context, med.medicationId)
                                        // Refresh list immediately
                                        allPendingMeds = PendingMedicationTracker.getPendingMedications(context)
                                        // Send broadcast for additional actions (notifications, history)
                                        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                                            action = ReminderBroadcastReceiver.ACTION_MARK_TAKEN
                                            putExtra("MEDICATION_ID", med.medicationId)
                                            putExtra("MEDICATION_NAME", med.medicationName)
                                            putExtra("HOUR", med.hour)
                                            putExtra("MINUTE", med.minute)
                                        }
                                        context.sendBroadcast(intent)
                                    },
                                    onSnooze = {
                                        // Remove from pending tracker immediately
                                        PendingMedicationTracker.removePendingMedication(context, med.medicationId)
                                        // Refresh list immediately
                                        allPendingMeds = PendingMedicationTracker.getPendingMedications(context)
                                        // Send broadcast for snooze action
                                        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                                            action = ReminderBroadcastReceiver.ACTION_SNOOZE
                                            putExtra("MEDICATION_ID", med.medicationId)
                                            putExtra("MEDICATION_NAME", med.medicationName)
                                            putExtra("MEDICATION_PHOTO_URI", med.medicationPhotoUri)
                                            putExtra("HOUR", med.hour)
                                            putExtra("MINUTE", med.minute)
                                        }
                                        context.sendBroadcast(intent)
                                    },
                                    onSkip = {
                                        // Remove from pending tracker immediately
                                        PendingMedicationTracker.removePendingMedication(context, med.medicationId)
                                        // Refresh list immediately
                                        allPendingMeds = PendingMedicationTracker.getPendingMedications(context)
                                        // Send broadcast for skip action
                                        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                                            action = ReminderBroadcastReceiver.ACTION_SKIP
                                            putExtra("MEDICATION_ID", med.medicationId)
                                            putExtra("MEDICATION_NAME", med.medicationName)
                                            putExtra("HOUR", med.hour)
                                            putExtra("MINUTE", med.minute)
                                        }
                                        context.sendBroadcast(intent)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OutstandingMedicationCard(
    medication: PendingMedicationTracker.PendingMedication,
    onMarkTaken: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Medication photo
                if (medication.medicationPhotoUri != null) {
                    Card(
                        modifier = Modifier.size(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(medication.medicationPhotoUri),
                            contentDescription = medication.medicationName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = medication.medicationName.take(2).uppercase(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Medication name
                Text(
                    text = medication.medicationName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons - extra large for elderly users
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Taken button - primary, full width
                Button(
                    onClick = onMarkTaken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text(" I've Taken This", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // Snooze and Skip in a row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Text("Snooze", fontSize = 16.sp)
                    }

                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text("Skip", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
