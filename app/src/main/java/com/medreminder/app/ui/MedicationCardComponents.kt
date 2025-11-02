package com.medreminder.app.ui

import android.content.Context
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.medreminder.app.R
import com.medreminder.app.data.Medication
import com.medreminder.app.utils.AudioPlayer
import com.medreminder.app.utils.AudioTranscriptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Displays a medication card with photo, name, schedule, audio playback, and edit/delete actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationCard(
    medication: Medication,
    currentLanguage: String,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayer(context) }
    var isPlaying by remember { mutableStateOf(false) }

    // State for translated transcription text
    var displayTranscription by remember(medication.audioTranscription, currentLanguage) {
        mutableStateOf(medication.audioTranscription)
    }

    // Translate transcription on-demand when language changes
    LaunchedEffect(medication.audioTranscription, medication.audioTranscriptionLanguage, currentLanguage) {
        if (medication.audioTranscription != null &&
            medication.audioTranscriptionLanguage != null &&
            medication.audioTranscriptionLanguage != currentLanguage) {
            // Need to translate
            withContext(Dispatchers.IO) {
                try {
                    val transcriptionService = AudioTranscriptionService(context)
                    val translated = transcriptionService.translateText(
                        medication.audioTranscription!!,
                        medication.audioTranscriptionLanguage!!,
                        currentLanguage
                    )
                    displayTranscription = translated ?: medication.audioTranscription
                } catch (e: Exception) {
                    // Silent failure - just show original transcription
                    displayTranscription = medication.audioTranscription
                }
            }
        } else {
            displayTranscription = medication.audioTranscription
        }
    }

    DisposableEffect(medication.audioNotePath) {
        onDispose {
            // Ensure we stop playback when card leaves composition
            if (isPlaying) audioPlayer.stop()
            audioPlayer.release()
            isPlaying = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo or placeholder
            if (medication.photoUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(medication.photoUri)),
                    contentDescription = stringResource(R.string.medication_photo),
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFFE3F2FD)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                    )
                }
            }

            Spacer(modifier = Modifier.width(1.dp))

            // Middle section: optional slim audio column + name/schedule column
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                // Always reserve a slim column for audio to keep name alignment consistent
                Column(
                    modifier = Modifier
                        .width(30.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (medication.audioNotePath != null) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    audioPlayer.stop()
                                    isPlaying = false
                                } else {
                                    audioPlayer.play(
                                        medication.audioNotePath,
                                        onCompletion = { isPlaying = false },
                                        onError = { isPlaying = false }
                                    )
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = if (isPlaying) "Stop audio" else "Play audio",
                                tint = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(1.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.Black,
                        maxLines = 2
                    )

                    val jsonString = medication.reminderTimesJson
                    val hasTimes = !jsonString.isNullOrBlank() && jsonString.trim() != "[]" && jsonString!!.contains("\"hour\":")
                    if (hasTimes) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                            )
                            Text(
                                text = parseReminderTimesForDisplay(jsonString!!),
                                fontSize = 14.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                                maxLines = 2
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = androidx.compose.ui.graphics.Color.Gray
                            )
                            Text(
                                text = stringResource(R.string.no_schedule),
                                fontSize = 14.sp,
                                color = androidx.compose.ui.graphics.Color.Gray
                            )
                        }
                    }

                    // Display transcription text if available
                    if (!displayTranscription.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayTranscription!!,
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF757575),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Edit and Delete buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Edit button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Delete button
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = androidx.compose.ui.graphics.Color(0xFFE53935),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.delete_medication_title),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_medication_message, medication.name),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        stringResource(R.string.delete_confirm),
                        fontSize = 18.sp
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        stringResource(R.string.cancel),
                        fontSize = 18.sp
                    )
                }
            }
        )
    }
}

/**
 * Wrapper dialog component that delegates to MedicationActionPalette for timeline interactions.
 */
@Composable
fun MedicationActionDialog(
    medication: Medication,
    scheduledTime: Long,
    hour: Int,
    minute: Int,
    currentLanguage: String,
    onDismiss: () -> Unit,
    context: Context,
    isOverdue: Boolean = false
) {
    // Reuse the existing action palette
    MedicationActionPalette(
        medication = medication,
        hour = hour,
        minute = minute,
        onDismiss = onDismiss,
        currentLanguage = currentLanguage
    )
}

/**
 * Parses reminder times JSON and formats them for display.
 *
 * @param jsonString JSON string containing reminder times
 * @return Formatted string like "08:00 AM, 02:30 PM, 09:00 PM"
 */
fun parseReminderTimesForDisplay(jsonString: String): String {
    try {
        // Simple JSON parsing - extract hours and minutes
        data class TimeEntry(val hour: Int, val minute: Int, val displayString: String)
        val times = mutableListOf<TimeEntry>()
        val timePattern = """"hour":(\d+),"minute":(\d+)""".toRegex()

        timePattern.findAll(jsonString).forEach { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()

            val amPm = if (hour >= 12) "PM" else "AM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            val displayString = String.format("%02d:%02d %s", displayHour, minute, amPm)
            times.add(TimeEntry(hour, minute, displayString))
        }

        // Sort by hour and minute in chronological order
        return times.sortedWith(compareBy({ it.hour }, { it.minute }))
            .joinToString(", ") { it.displayString }
    } catch (e: Exception) {
        return ""
    }
}
