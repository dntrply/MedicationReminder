package com.medreminder.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.medreminder.app.R
import com.medreminder.app.notifications.PendingMedicationTracker
import com.medreminder.app.notifications.ReminderBroadcastReceiver
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeMedicationsScreen(
    hour: Int,
    minute: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var medications by remember {
        mutableStateOf(PendingMedicationTracker.getPendingMedicationsAtTime(context, hour, minute))
    }

    // Refresh medications list when this screen is shown
    LaunchedEffect(hour, minute) {
        medications = PendingMedicationTracker.getPendingMedicationsAtTime(context, hour, minute)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.take_medications_now),
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
        if (medications.isEmpty()) {
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
                    Text(
                        text = stringResource(R.string.no_pending_medications),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onBack) {
                        Text(stringResource(R.string.back_to_home))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = formatTime(hour, minute),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(medications) { medication ->
                    MedicationActionCard(
                        medication = medication,
                        onMarkTaken = {
                            // Send mark taken intent
                            val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                                action = ReminderBroadcastReceiver.ACTION_MARK_TAKEN
                                putExtra("MEDICATION_ID", medication.medicationId)
                                putExtra("MEDICATION_NAME", medication.medicationName)
                                putExtra("HOUR", hour)
                                putExtra("MINUTE", minute)
                            }
                            context.sendBroadcast(intent)

                            // Refresh list
                            medications = PendingMedicationTracker.getPendingMedicationsAtTime(context, hour, minute)

                            // Go back if no more medications
                            if (medications.isEmpty()) {
                                onBack()
                            }
                        },
                        onSnooze = {
                            // Send snooze intent
                            val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                                action = ReminderBroadcastReceiver.ACTION_SNOOZE
                                putExtra("MEDICATION_ID", medication.medicationId)
                                putExtra("MEDICATION_NAME", medication.medicationName)
                                putExtra("MEDICATION_PHOTO_URI", medication.medicationPhotoUri)
                                putExtra("HOUR", hour)
                                putExtra("MINUTE", minute)
                            }
                            context.sendBroadcast(intent)

                            // Refresh list
                            medications = PendingMedicationTracker.getPendingMedicationsAtTime(context, hour, minute)

                            // Go back if no more medications
                            if (medications.isEmpty()) {
                                onBack()
                            }
                        },
                        onSkip = {
                            // Send skip intent
                            val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                                action = ReminderBroadcastReceiver.ACTION_SKIP
                                putExtra("MEDICATION_ID", medication.medicationId)
                                putExtra("MEDICATION_NAME", medication.medicationName)
                                putExtra("HOUR", hour)
                                putExtra("MINUTE", minute)
                            }
                            context.sendBroadcast(intent)

                            // Refresh list
                            medications = PendingMedicationTracker.getPendingMedicationsAtTime(context, hour, minute)

                            // Go back if no more medications
                            if (medications.isEmpty()) {
                                onBack()
                            }
                        }
                    )
                }

                // Batch actions at the bottom
                if (medications.size > 1) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.or_take_action_on_all),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    // Send mark all taken intent
                                    val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                                        action = ReminderBroadcastReceiver.ACTION_MARK_ALL_TAKEN
                                        putExtra("HOUR", hour)
                                        putExtra("MINUTE", minute)
                                    }
                                    context.sendBroadcast(intent)
                                    onBack()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(stringResource(R.string.ive_taken_all))
                            }

                            OutlinedButton(
                                onClick = {
                                    // Send snooze all intent
                                    val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                                        action = ReminderBroadcastReceiver.ACTION_SNOOZE_ALL
                                        putExtra("HOUR", hour)
                                        putExtra("MINUTE", minute)
                                    }
                                    context.sendBroadcast(intent)
                                    onBack()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.snooze_all))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MedicationActionCard(
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onMarkTaken,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(R.string.taken), fontSize = 14.sp)
                }

                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.snooze), fontSize = 14.sp)
                }

                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.skip), fontSize = 14.sp)
                }
            }
        }
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
