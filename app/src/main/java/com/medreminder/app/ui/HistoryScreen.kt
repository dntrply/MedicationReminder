package com.medreminder.app.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.medreminder.app.data.Medication
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.data.MedicationHistory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    currentLanguage: String = "en",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { MedicationDatabase.getDatabase(context) }
    val historyDao = remember { database.historyDao() }
    val medicationDao = remember { database.medicationDao() }

    // State for view toggle
    var viewByDate by remember { mutableStateOf(true) }

    // State for number of days to show
    var daysToShow by remember { mutableStateOf(3) }

    // Get active profile ID
    val activeProfileId by com.medreminder.app.data.SettingsStore.activeProfileIdFlow(context)
        .collectAsState(initial = null)

    // Get medications for photo/name lookup - filtered by active profile
    val medications by medicationDao.getMedicationsByProfile(activeProfileId ?: 1L)
        .collectAsState(initial = emptyList())

    // Calculate date range
    val dateRange = remember(daysToShow) {
        val endCal = Calendar.getInstance()
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        endCal.set(Calendar.SECOND, 59)
        endCal.set(Calendar.MILLISECOND, 999)

        val startCal = Calendar.getInstance()
        startCal.add(Calendar.DAY_OF_YEAR, -(daysToShow - 1))
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        startCal.timeInMillis to endCal.timeInMillis
    }

    // Get history for date range - filtered by active profile
    val historyList by historyDao.getHistoryForDateRangeByProfile(
        profileId = activeProfileId ?: 1L,
        startTime = dateRange.first,
        endTime = dateRange.second
    ).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (currentLanguage) {
                            "hi" -> "इतिहास"
                            "gu" -> "ઇતિહાસ"
                            else -> "History"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF4A90E2)
                )
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // View toggle buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // By Date button
                Button(
                    onClick = { viewByDate = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewByDate) Color(0xFF4A90E2) else Color(0xFFF5F5F5),
                        contentColor = if (viewByDate) Color.White else Color.Gray
                    )
                ) {
                    Text(
                        when (currentLanguage) {
                            "hi" -> "तिथि के अनुसार"
                            "gu" -> "તારીખ દ્વારા"
                            else -> "By Date"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // By Medication button
                Button(
                    onClick = { viewByDate = false },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!viewByDate) Color(0xFF4A90E2) else Color(0xFFF5F5F5),
                        contentColor = if (!viewByDate) Color.White else Color.Gray
                    )
                ) {
                    Text(
                        when (currentLanguage) {
                            "hi" -> "दवा के अनुसार"
                            "gu" -> "દવા દ્વારા"
                            else -> "By Medication"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Date range label
            Text(
                text = when (currentLanguage) {
                    "hi" -> "पिछले $daysToShow दिन"
                    "gu" -> "છેલ્લા $daysToShow દિવસ"
                    else -> "Last $daysToShow Days"
                },
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // History content
            if (viewByDate) {
                HistoryByDateView(
                    historyList = historyList,
                    medications = medications,
                    daysToShow = daysToShow,
                    currentLanguage = currentLanguage,
                    onLoadMore = { daysToShow += 3 }
                )
            } else {
                HistoryByMedicationView(
                    historyList = historyList,
                    medications = medications,
                    currentLanguage = currentLanguage,
                    onLoadMore = { daysToShow += 3 }
                )
            }
        }
    }
}

@Composable
fun HistoryByDateView(
    historyList: List<MedicationHistory>,
    medications: List<Medication>,
    daysToShow: Int,
    currentLanguage: String,
    onLoadMore: () -> Unit
) {
    // Group history by date
    val groupedByDate = remember(historyList) {
        historyList.groupBy { history ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = history.scheduledTime
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.toSortedMap(compareByDescending { it })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        groupedByDate.forEach { (dateTimestamp, entries) ->
            item {
                DateSectionHeader(dateTimestamp, currentLanguage)
            }

            items(entries) { history ->
                val medication = medications.firstOrNull { it.id == history.medicationId }
                if (medication != null) {
                    HistoryEntryCard(
                        history = history,
                        medication = medication,
                        currentLanguage = currentLanguage
                    )
                }
            }
        }

        // Load More button
        item {
            OutlinedButton(
                onClick = onLoadMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF4A90E2)
                )
            ) {
                Icon(Icons.Default.ExpandMore, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when (currentLanguage) {
                        "hi" -> "पिछले 3 दिन लोड करें"
                        "gu" -> "પાછલા 3 દિવસ લોડ કરો"
                        else -> "Load Previous 3 Days"
                    },
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun HistoryByMedicationView(
    historyList: List<MedicationHistory>,
    medications: List<Medication>,
    currentLanguage: String,
    onLoadMore: () -> Unit
) {
    // Group history by medication
    val groupedByMedication = remember(historyList) {
        historyList.groupBy { it.medicationId }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        groupedByMedication.forEach { (medicationId, entries) ->
            val medication = medications.firstOrNull { it.id == medicationId }
            if (medication != null) {
                item {
                    MedicationSectionHeader(medication)
                }

                items(entries.sortedByDescending { it.scheduledTime }) { history ->
                    HistoryEntryCard(
                        history = history,
                        medication = medication,
                        currentLanguage = currentLanguage,
                        compact = true
                    )
                }
            }
        }

        // Load More button
        item {
            OutlinedButton(
                onClick = onLoadMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF4A90E2)
                )
            ) {
                Icon(Icons.Default.ExpandMore, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when (currentLanguage) {
                        "hi" -> "पिछले 3 दिन लोड करें"
                        "gu" -> "પાછલા 3 દિવસ લોડ કરો"
                        else -> "Load Previous 3 Days"
                    },
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun DateSectionHeader(dateTimestamp: Long, currentLanguage: String) {
    val dateStr = remember(dateTimestamp) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dateTimestamp

        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        when {
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> {
                when (currentLanguage) {
                    "hi" -> "आज"
                    "gu" -> "આજે"
                    else -> "Today"
                }
            }
            cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
            cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) -> {
                when (currentLanguage) {
                    "hi" -> "कल"
                    "gu" -> "ગઈકાલે"
                    else -> "Yesterday"
                }
            }
            else -> {
                val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                sdf.format(Date(dateTimestamp))
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        )
    ) {
        Text(
            text = dateStr,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun MedicationSectionHeader(medication: Medication) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Medication photo
            if (medication.photoUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(medication.photoUri)),
                    contentDescription = medication.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF4A90E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = medication.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
        }
    }
}

@Composable
fun HistoryEntryCard(
    history: MedicationHistory,
    medication: Medication,
    currentLanguage: String,
    compact: Boolean = false
) {
    val statusColor = when (history.action) {
        "TAKEN" -> if (history.wasOnTime) Color(0xFF4CAF50) else Color(0xFFFF9800)
        "SKIPPED" -> Color(0xFFFF5722)
        else -> Color(0xFFFF6B6B) // MISSED or unknown
    }

    val statusIcon = when (history.action) {
        "TAKEN" -> if (history.wasOnTime) Icons.Default.CheckCircle else Icons.Default.Schedule
        "SKIPPED" -> Icons.Default.Close
        else -> Icons.Default.Warning
    }

    val statusText = when (history.action) {
        "TAKEN" -> if (history.wasOnTime) {
            when (currentLanguage) {
                "hi" -> "समय पर"
                "gu" -> "સમય પર"
                else -> "On time"
            }
        } else {
            val lateMinutes = ((history.takenTime - history.scheduledTime) / (1000 * 60)).toInt()
            when (currentLanguage) {
                "hi" -> "$lateMinutes मिनट देरी"
                "gu" -> "$lateMinutes મિનિટ મોડું"
                else -> "Late by $lateMinutes min"
            }
        }
        "SKIPPED" -> when (currentLanguage) {
            "hi" -> "छोड़ दिया"
            "gu" -> "છોડી દીધું"
            else -> "Skipped"
        }
        else -> when (currentLanguage) {
            "hi" -> "नहीं लिया"
            "gu" -> "લીધું નથી"
            else -> "Missed"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Medication photo (only if not compact)
            if (!compact) {
                if (medication.photoUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(medication.photoUri)),
                        contentDescription = medication.name,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = null,
                            tint = Color(0xFF4A90E2),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))
            }

            // Medication info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (!compact) {
                    Text(
                        text = medication.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Scheduled time
                val scheduledTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    .format(Date(history.scheduledTime))

                Text(
                    text = when (currentLanguage) {
                        "hi" -> "निर्धारित: $scheduledTimeStr"
                        "gu" -> "સમયસૂચિ: $scheduledTimeStr"
                        else -> "Scheduled: $scheduledTimeStr"
                    },
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                // Taken time (if applicable)
                if (history.action == "TAKEN") {
                    val takenTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        .format(Date(history.takenTime))
                    Text(
                        text = when (currentLanguage) {
                            "hi" -> "लिया: $takenTimeStr"
                            "gu" -> "લીધું: $takenTimeStr"
                            else -> "Taken: $takenTimeStr"
                        },
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Status text
                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }
        }
    }
}
