package com.medreminder.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.medreminder.app.R
import com.medreminder.app.data.Medication
import com.medreminder.app.notifications.PendingMedicationTracker
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Section displaying all overdue medications with a warning header.
 */
@Composable
fun OverdueMedicationsSection(
    overdueMedications: List<PendingMedicationTracker.PendingMedication>,
    medications: List<Medication>,
    currentLanguage: String,
    context: Context
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFFFFEBEE) // Light red background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with alert icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFFD32F2F), // Dark red
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.overdue_medications),
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // List of overdue medications
            overdueMedications.forEach { pending ->
                val medication = medications.firstOrNull { it.id == pending.medicationId }
                if (medication != null) {
                    OverdueMedicationCard(
                        medication = medication,
                        pending = pending,
                        currentLanguage = currentLanguage,
                        context = context
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

/**
 * Card displaying a single overdue medication with a "LATE" badge and clickable action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverdueMedicationCard(
    medication: Medication,
    pending: PendingMedicationTracker.PendingMedication,
    currentLanguage: String,
    context: Context
) {
    var showActionDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.White
        ),
        border = BorderStroke(3.dp, androidx.compose.ui.graphics.Color(0xFFD32F2F)), // Dark red border
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = { showActionDialog = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Medication image with LATE badge
            Box {
                if (medication.photoUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(medication.photoUri)),
                        contentDescription = medication.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Color.White),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFFFFEBEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                        )
                    }
                }

                // LATE badge with clock icon
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .background(
                            androidx.compose.ui.graphics.Color(0xFFD32F2F),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                        Text(
                            text = stringResource(R.string.late_label),
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Medication info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = medication.name,
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Format time with context (Yesterday or date)
                val scheduledCal = java.util.Calendar.getInstance()
                scheduledCal.timeInMillis = pending.timestamp
                scheduledCal.set(java.util.Calendar.HOUR_OF_DAY, pending.hour)
                scheduledCal.set(java.util.Calendar.MINUTE, pending.minute)

                val currentCal = java.util.Calendar.getInstance()
                val yesterday = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, -1)
                }

                val timeStr = String.format("%02d:%02d", pending.hour, pending.minute)
                val dateStr = when {
                    scheduledCal.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR) &&
                    scheduledCal.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) -> {
                        stringResource(R.string.yesterday, timeStr)
                    }
                    else -> {
                        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                        sdf.format(scheduledCal.time)
                    }
                }

                Text(
                    text = dateStr,
                    fontSize = 14.sp,
                    color = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }

            // Arrow icon to indicate clickable
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // Action dialog
    if (showActionDialog) {
        MedicationActionDialog(
            medication = medication,
            scheduledTime = pending.timestamp,
            hour = pending.hour,
            minute = pending.minute,
            currentLanguage = currentLanguage,
            onDismiss = { showActionDialog = false },
            context = context,
            isOverdue = true
        )
    }
}
