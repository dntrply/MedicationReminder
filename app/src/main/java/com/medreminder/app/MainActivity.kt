package com.medreminder.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.medreminder.app.data.Medication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.data.MedicationHistory
import com.medreminder.app.data.ReminderTime
import com.medreminder.app.notifications.NotificationScheduler
import com.medreminder.app.notifications.PendingMedicationTracker
import com.medreminder.app.ui.MedicationViewModel
import com.medreminder.app.ui.SetReminderTimesScreen
import com.medreminder.app.ui.theme.MedicationReminderTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Main entry point for the application
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Clean up stale pending medications on app start (in background thread)
        lifecycleScope.launch(Dispatchers.IO) {
            PendingMedicationTracker.cleanupStaleEntries(this@MainActivity)
        }

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        setContent {
            MedicationReminderTheme {
                val viewModel: MedicationViewModel = viewModel()

                var currentLanguage by remember {
                    mutableStateOf(getCurrentLanguage(this))
                }

                // Check if we should show Take Medications screen from notification
                val showTakeMedications = intent?.getBooleanExtra("SHOW_TAKE_MEDICATIONS", false) ?: false
                val notificationHour = intent?.getIntExtra("HOUR", -1) ?: -1
                val notificationMinute = intent?.getIntExtra("MINUTE", -1) ?: -1

                var currentScreen by remember {
                    mutableStateOf(
                        if (showTakeMedications && notificationHour != -1 && notificationMinute != -1) {
                            "take_medications"
                        } else {
                            "home"
                        }
                    )
                }

                var takeMedicationsTime by remember {
                    mutableStateOf(Pair(notificationHour, notificationMinute))
                }

                var medicationToSave by remember {
                    mutableStateOf<Medication?>(null)
                }

                var tempMedication by remember {
                    mutableStateOf<Pair<String, String?>?>(null) // name, photoUri
                }

                var medicationToEdit by remember {
                    mutableStateOf<Medication?>(null)
                }

                // Save medication when medicationToSave changes
                LaunchedEffect(medicationToSave) {
                    medicationToSave?.let { medication ->
                        viewModel.addMedication(medication)
                        // Schedule notifications for this medication
                        NotificationScheduler.scheduleMedicationNotifications(this@MainActivity, medication)
                        medicationToSave = null
                    }
                }

                // Update locale when language changes
                LaunchedEffect(currentLanguage) {
                    setAppLocale(this@MainActivity, currentLanguage)
                }

                when (currentScreen) {
                    "home" -> HomeScreen(
                        viewModel = viewModel,
                        currentLanguage = currentLanguage,
                        onLanguageChange = { newLang ->
                            currentLanguage = newLang
                            saveLanguagePreference(this@MainActivity, newLang)
                            recreate() // Recreate activity to apply new locale
                        },
                        onAddMedication = {
                            medicationToEdit = null
                            tempMedication = null
                            currentScreen = "add_medication"
                        },
                        onEditMedication = { medication ->
                            medicationToEdit = medication
                            tempMedication = Pair(medication.name, medication.photoUri)
                            currentScreen = "edit_reminder"
                        },
                        onDebugData = {
                            currentScreen = "debug_data"
                        },
                        onOutstandingMedications = {
                            currentScreen = "outstanding_medications"
                        }
                    )
                    "add_medication" -> AddMedicationScreen(
                        currentLanguage = currentLanguage,
                        initialName = tempMedication?.first ?: "",
                        initialPhotoUri = tempMedication?.second,
                        onBack = {
                            tempMedication = null
                            currentScreen = "home"
                        },
                        onNext = { name, photoUri ->
                            tempMedication = Pair(name, photoUri)
                            currentScreen = "set_reminder"
                        }
                    )
                    "edit_medication" -> medicationToEdit?.let { medication ->
                        AddMedicationScreen(
                            currentLanguage = currentLanguage,
                            initialName = tempMedication?.first ?: medication.name,
                            initialPhotoUri = tempMedication?.second ?: medication.photoUri,
                            onBack = {
                                tempMedication = null
                                medicationToEdit = null
                                currentScreen = "home"
                            },
                            onNext = { name, photoUri ->
                                tempMedication = Pair(name, photoUri)
                                currentScreen = "edit_reminder"
                            }
                        )
                    }
                    "set_reminder" -> tempMedication?.let { (name, photoUri) ->
                        SetReminderTimesScreen(
                            currentLanguage = currentLanguage,
                            medicationName = name,
                            medicationPhotoUri = photoUri,
                            onBack = {
                                // Save and navigate home when back is pressed
                                tempMedication = null
                                currentScreen = "home"
                            },
                            onSave = { reminderTimes ->
                                // Auto-save: just update the medication, don't navigate
                                val reminderJson = reminderTimes.joinToString(",") { rt ->
                                    """{"hour":${rt.hour},"minute":${rt.minute},"days":[${rt.daysOfWeek.joinToString(",")}]}"""
                                }
                                val jsonArray = "[$reminderJson]"

                                medicationToSave = Medication(
                                    name = name,
                                    photoUri = photoUri,
                                    reminderTimesJson = jsonArray
                                )
                            }
                        )
                    }
                    "edit_reminder" -> medicationToEdit?.let { medication ->
                        tempMedication?.let { (name, photoUri) ->
                            SetReminderTimesScreen(
                                currentLanguage = currentLanguage,
                                medicationName = name,
                                medicationPhotoUri = photoUri,
                                initialReminderTimes = medication.reminderTimesJson,
                                onBack = {
                                    // Navigate home when back is pressed
                                    tempMedication = null
                                    medicationToEdit = null
                                    currentScreen = "home"
                                },
                                onSave = { reminderTimes ->
                                    // Auto-save: update the medication, don't navigate
                                    val reminderJson = reminderTimes.joinToString(",") { rt ->
                                        """{"hour":${rt.hour},"minute":${rt.minute},"days":[${rt.daysOfWeek.joinToString(",")}]}"""
                                    }
                                    val jsonArray = "[$reminderJson]"

                                    // Update existing medication
                                    val updatedMedication = medication.copy(
                                        name = name,
                                        photoUri = photoUri,
                                        reminderTimesJson = jsonArray
                                    )
                                    viewModel.updateMedication(updatedMedication)

                                    // Cancel old notifications and schedule new ones
                                    NotificationScheduler.cancelMedicationNotifications(this@MainActivity, medication)
                                    NotificationScheduler.scheduleMedicationNotifications(this@MainActivity, updatedMedication)
                                },
                                onEditDetails = {
                                    currentScreen = "edit_medication"
                                }
                            )
                        }
                    }
                    "take_medications" -> {
                        if (takeMedicationsTime.first != -1 && takeMedicationsTime.second != -1) {
                            com.medreminder.app.ui.TakeMedicationsScreen(
                                hour = takeMedicationsTime.first,
                                minute = takeMedicationsTime.second,
                                onBack = {
                                    currentScreen = "home"
                                }
                            )
                        }
                    }
                    "debug_data" -> {
                        com.medreminder.app.ui.DebugDataScreen(
                            onBack = {
                                currentScreen = "home"
                            }
                        )
                    }
                    "outstanding_medications" -> {
                        com.medreminder.app.ui.OutstandingMedicationsScreen(
                            onBack = {
                                currentScreen = "home"
                            }
                        )
                    }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = getLanguagePreference(newBase)
        super.attachBaseContext(updateContextLocale(newBase, lang))
    }
}

// Language preference helpers
private fun saveLanguagePreference(context: Context, lang: String) {
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("language", lang)
        .apply()
}

private fun getLanguagePreference(context: Context): String {
    return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getString("language", "en") ?: "en"
}

private fun getCurrentLanguage(context: Context): String {
    return getLanguagePreference(context)
}

private fun updateContextLocale(context: Context, language: String): Context {
    val locale = Locale(language)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    return context.createConfigurationContext(config)
}

private fun setAppLocale(context: Context, language: String) {
    val locale = Locale(language)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MedicationViewModel,
    currentLanguage: String = "en",
    onLanguageChange: (String) -> Unit = {},
    onAddMedication: () -> Unit = {},
    onEditMedication: (Medication) -> Unit = {},
    onDebugData: () -> Unit = {},
    onOutstandingMedications: () -> Unit = {}
) {
    val medications by viewModel.medications.collectAsState(initial = emptyList())
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTimelineView by remember { mutableStateOf(false) }
    var timelineViewCounter by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // Handle back button press - show exit confirmation
    // Only enable when dialogs are not open to avoid conflicts
    BackHandler(enabled = !showLanguageDialog && !showMenu) {
        showExitDialog = true
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.White,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                },
                actions = {
                    // Menu button
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Dropdown menu
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                                    Text(stringResource(R.string.feature_schedule), fontSize = 18.sp)
                                }
                            },
                            onClick = {
                                showMenu = false
                                // TODO: Navigate to schedule
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null)
                                    Text(stringResource(R.string.feature_history), fontSize = 18.sp)
                                }
                            },
                            onClick = {
                                showMenu = false
                                // TODO: Navigate to history
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null)
                                    Text(
                                        when (currentLanguage) {
                                            "hi" -> "भाषा बदलें"
                                            "gu" -> "ભાષા બદલો"
                                            else -> "Change Language"
                                        },
                                        fontSize = 18.sp
                                    )
                                }
                            },
                            onClick = {
                                showMenu = false
                                showLanguageDialog = true
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Medication, contentDescription = null)
                                    Text(
                                        when (currentLanguage) {
                                            "hi" -> "दवाएं लें"
                                            "gu" -> "દવા લો"
                                            else -> "Medications to Take"
                                        },
                                        fontSize = 18.sp
                                    )
                                }
                            },
                            onClick = {
                                showMenu = false
                                onOutstandingMedications()
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.BugReport, contentDescription = null)
                                    Text("Debug Data", fontSize = 18.sp)
                                }
                            },
                            onClick = {
                                showMenu = false
                                onDebugData()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                    titleContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { paddingValues ->
        if (medications.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large visual icon
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFFE3F2FD)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Empty state message
                Text(
                    text = stringResource(R.string.no_medications),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    color = androidx.compose.ui.graphics.Color.Black,
                    fontSize = 28.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.add_first_medication),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = androidx.compose.ui.graphics.Color.Gray,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Large + Button (shows text on hover)
                var isHovered by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    onClick = onAddMedication
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Always show the + icon
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_medication),
                            modifier = Modifier.size(80.dp),
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Subtle hint text
                Text(
                    text = when (currentLanguage) {
                        "hi" -> "अपनी दवाओं को ट्रैक करना शुरू करें"
                        "gu" -> "તમારી દવાઓને ટ્રેક કરવાનું શરૂ કરો"
                        else -> "Start tracking your medications"
                    },
                    fontSize = 16.sp,
                    color = androidx.compose.ui.graphics.Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Medication list or timeline view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // View toggle button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // List View Button
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!showTimelineView)
                                androidx.compose.ui.graphics.Color(0xFF4A90E2)
                            else
                                androidx.compose.ui.graphics.Color(0xFFF5F5F5)
                        ),
                        onClick = { showTimelineView = false }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = if (!showTimelineView)
                                    androidx.compose.ui.graphics.Color.White
                                else
                                    androidx.compose.ui.graphics.Color.Gray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (currentLanguage) {
                                    "hi" -> "सूची"
                                    "gu" -> "યાદી"
                                    else -> "List"
                                },
                                fontSize = 18.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = if (!showTimelineView)
                                    androidx.compose.ui.graphics.Color.White
                                else
                                    androidx.compose.ui.graphics.Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Timeline View Button
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (showTimelineView)
                                androidx.compose.ui.graphics.Color(0xFF4A90E2)
                            else
                                androidx.compose.ui.graphics.Color(0xFFF5F5F5)
                        ),
                        onClick = {
                            showTimelineView = true
                            timelineViewCounter++
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = if (showTimelineView)
                                    androidx.compose.ui.graphics.Color.White
                                else
                                    androidx.compose.ui.graphics.Color.Gray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (currentLanguage) {
                                    "hi" -> "समयरेखा"
                                    "gu" -> "સમયરેખા"
                                    else -> "Timeline"
                                },
                                fontSize = 18.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = if (showTimelineView)
                                    androidx.compose.ui.graphics.Color.White
                                else
                                    androidx.compose.ui.graphics.Color.Gray
                            )
                        }
                    }
                }

                if (!showTimelineView) {
                    // List View
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(medications.size) { index ->
                            val medication = medications[index]
                            MedicationCard(
                                medication = medication,
                                currentLanguage = currentLanguage,
                                onEdit = { onEditMedication(medication) },
                                onDelete = { viewModel.deleteMedication(medication) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                } else {
                    // Timeline View
                    TimelineView(
                        medications = medications,
                        currentLanguage = currentLanguage,
                        viewCounter = timelineViewCounter,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Floating Add Button at bottom
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(80.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    onClick = onAddMedication
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.add_medication),
                            fontSize = 22.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }
        }

        // Language selection dialog
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Select Language / ભાષા પસંદ કરો / भाषा चुनें")
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // English option
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentLanguage == "en")
                                    androidx.compose.ui.graphics.Color(0xFF4A90E2)
                                else
                                    androidx.compose.ui.graphics.Color(0xFFF5F5F5)
                            ),
                            onClick = {
                                onLanguageChange("en")
                                showLanguageDialog = false
                            }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "English",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontSize = 20.sp,
                                    color = if (currentLanguage == "en")
                                        androidx.compose.ui.graphics.Color.White
                                    else
                                        androidx.compose.ui.graphics.Color.Black
                                )
                            }
                        }

                        // Hindi option
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentLanguage == "hi")
                                    androidx.compose.ui.graphics.Color(0xFF4A90E2)
                                else
                                    androidx.compose.ui.graphics.Color(0xFFF5F5F5)
                            ),
                            onClick = {
                                onLanguageChange("hi")
                                showLanguageDialog = false
                            }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "हिन्दी",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontSize = 20.sp,
                                    color = if (currentLanguage == "hi")
                                        androidx.compose.ui.graphics.Color.White
                                    else
                                        androidx.compose.ui.graphics.Color.Black
                                )
                            }
                        }

                        // Gujarati option
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentLanguage == "gu")
                                    androidx.compose.ui.graphics.Color(0xFF4A90E2)
                                else
                                    androidx.compose.ui.graphics.Color(0xFFF5F5F5)
                            ),
                            onClick = {
                                onLanguageChange("gu")
                                showLanguageDialog = false
                            }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ગુજરાતી",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontSize = 20.sp,
                                    color = if (currentLanguage == "gu")
                                        androidx.compose.ui.graphics.Color.White
                                    else
                                        androidx.compose.ui.graphics.Color.Black
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text("Close / बंद करें")
                    }
                }
            )
        }

        // Exit confirmation dialog
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        text = when (currentLanguage) {
                            "hi" -> "ऐप से बाहर निकलें?"
                            "gu" -> "એપ બંધ કરીએ?"
                            else -> "Exit App?"
                        },
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = when (currentLanguage) {
                            "hi" -> "क्या आप वाकई ऐप से बाहर निकलना चाहते हैं?"
                            "gu" -> "શું તમે ખરેખર એપ બંધ કરવા માંગો છો?"
                            else -> "Do you really want to exit the app?"
                        },
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            (context as? ComponentActivity)?.finish()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            when (currentLanguage) {
                                "hi" -> "हाँ, बाहर निकलें"
                                "gu" -> "હા, બંધ કરો"
                                else -> "Yes, Exit"
                            },
                            fontSize = 18.sp
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showExitDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            when (currentLanguage) {
                                "hi" -> "रद्द करें"
                                "gu" -> "રદ કરો"
                                else -> "Cancel"
                            },
                            fontSize = 18.sp
                        )
                    }
                }
            )
        }
    }
}

// Helper function to parse reminder times from JSON and format for display
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationCard(
    medication: Medication,
    currentLanguage: String,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

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

            Spacer(modifier = Modifier.width(16.dp))

            // Medication info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = medication.name,
                    fontSize = 24.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Black,
                    maxLines = 2
                )

                // Show reminder times
                medication.reminderTimesJson?.let { jsonString ->
                    if (jsonString.isNotEmpty() && jsonString != "[]") {
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
                                text = parseReminderTimesForDisplay(jsonString),
                                fontSize = 14.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                                maxLines = 2
                            )
                        }
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
                        contentDescription = when (currentLanguage) {
                            "hi" -> "संपादित करें"
                            "gu" -> "સંપાદિત કરો"
                            else -> "Edit"
                        },
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

// Data class to hold medication times for timeline display
data class MedicationTimeSlot(
    val medication: Medication,
    val hour: Int,
    val minute: Int
)

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

    // Find the upcoming medication hour (prioritizing outstanding medications)
    // Recalculate whenever timeSlots or todayHistory changes
    val upcomingHour = remember(timeSlots, todayHistory) {
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

        // Find outstanding medications (scheduled but not taken, and time has passed)
        val outstandingSlots = timeSlots
            .filter { slot ->
                val slotTimeInMinutes = slot.hour * 60 + slot.minute
                slotTimeInMinutes < currentTimeInMinutes && // Time has passed
                !takenTimes.contains("${slot.hour}:${slot.minute}") // Not taken
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
        // Each hour block is 104dp (100dp width + 4dp spacing)
        // Convert dp to pixels for scrolling
        val scrollPosition = with(density) {
            (upcomingHour * 104).dp.toPx().toInt()
        }
        Log.d("Timeline", ">>> BEFORE SCROLL: current=${scrollState.value}, target=$scrollPosition, hour=$upcomingHour, historyCount=${todayHistory.size}, viewCounter=$viewCounter")
        // Use scrollTo for instant scroll, avoiding animation conflicts
        scrollState.scrollTo(scrollPosition)
        Log.d("Timeline", ">>> AFTER SCROLL: current=${scrollState.value}")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = when (currentLanguage) {
                "hi" -> "दैनिक समयरेखा"
                "gu" -> "દૈનિક સમયરેખા"
                else -> "Daily Timeline"
            },
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.Black,
            modifier = Modifier.padding(bottom = 16.dp)
        )

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
                        text = when (currentLanguage) {
                            "hi" -> "कोई समय सेट नहीं है"
                            "gu" -> "કોઈ સમય સેટ નથી"
                            else -> "No times set"
                        },
                        fontSize = 20.sp,
                        color = androidx.compose.ui.graphics.Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Timeline with horizontal scrolling
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 24-hour axis with horizontal scroll
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Create 24 hour blocks
                    for (hour in 0..23) {
                        Column(
                            modifier = Modifier.width(100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Hour label
                            Text(
                                text = formatHourLabel(hour),
                                fontSize = 16.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Container for timeline and medications
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(400.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                // Hour marker line (centered)
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(400.dp)
                                        .background(androidx.compose.ui.graphics.Color(0xFFE0E0E0))
                                )

                                // Find medications for this hour
                                val medicationsInHour = timeSlots.filter { slot ->
                                    slot.hour == hour
                                }.sortedBy { it.minute }

                                // Group medications within 30-minute intervals
                                val intervalGroups = mutableListOf<List<MedicationTimeSlot>>()
                                var currentGroup = mutableListOf<MedicationTimeSlot>()

                                medicationsInHour.forEachIndexed { index, slot ->
                                    if (currentGroup.isEmpty()) {
                                        currentGroup.add(slot)
                                    } else {
                                        val firstInGroup = currentGroup.first()
                                        val timeDiff = (slot.hour * 60 + slot.minute) - (firstInGroup.hour * 60 + firstInGroup.minute)

                                        if (timeDiff <= 30) {
                                            // Within 30 minutes, add to current group
                                            currentGroup.add(slot)
                                        } else {
                                            // Start new group
                                            intervalGroups.add(currentGroup.toList())
                                            currentGroup.clear()
                                            currentGroup.add(slot)
                                        }
                                    }
                                }
                                if (currentGroup.isNotEmpty()) {
                                    intervalGroups.add(currentGroup)
                                }

                                // Display each interval group
                                intervalGroups.forEach { group ->
                                    val firstSlot = group.first()
                                    val position = (firstSlot.minute / 60f) * 400f

                                    // Stack medications vertically within the group
                                    Column(
                                        modifier = Modifier
                                            .offset(
                                                x = 10.dp,
                                                y = position.dp
                                            )
                                            .width(80.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        group.forEach { slot ->
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
                                                    takenCal.get(java.util.Calendar.MINUTE) == slot.minute
                                                if (matches) {
                                                    Log.d("Timeline", "Found match for ${slot.medication.name} at ${slot.hour}:${slot.minute}")
                                                }
                                                matches
                                            }

                                            // A medication should only be marked as "outstanding" if:
                                            // 1. It's in the pending notification tracker (meaning a notification was actually sent)
                                            // OR
                                            // 2. It has history (meaning it was taken/skipped, so we know notification was sent)
                                            val hasPendingNotification = PendingMedicationTracker.getPendingMedications(context)
                                                .any { it.medicationId == slot.medication.id && it.hour == slot.hour && it.minute == slot.minute }

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
                                                Box(
                                                    modifier = Modifier.clickable {
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
    return String.format("%02d:00\n%s", displayHour, amPm)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            if (medication.photoUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(medication.photoUri)),
                    contentDescription = medication.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = medication.name,
                    fontSize = 22.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format("%02d:%02d %s",
                        if (hour == 0) 12 else if (hour > 12) hour - 12 else hour,
                        minute,
                        if (hour >= 12) "PM" else "AM"
                    ),
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                            when (currentLanguage) {
                                "hi" -> "लिया"
                                "gu" -> "લીધું"
                                else -> "Taken"
                            },
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
                            .height(52.dp)
                    ) {
                        Text(
                            when (currentLanguage) {
                                "hi" -> "स्नूज़"
                                "gu" -> "સ્નૂઝ"
                                else -> "Snooze"
                            },
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
                            .height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            when (currentLanguage) {
                                "hi" -> "छोड़ें"
                                "gu" -> "છોડો"
                                else -> "Skip"
                            },
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (currentLanguage) {
                        "hi" -> "रद्द करें"
                        "gu" -> "રદ કરો"
                        else -> "Cancel"
                    },
                    fontSize = 16.sp
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationScreen(
    currentLanguage: String = "en",
    initialName: String = "",
    initialPhotoUri: String? = null,
    onBack: () -> Unit,
    onNext: (String, String?) -> Unit // name, photoUri
) {
    var medicationName by remember { mutableStateOf(initialName) }
    var showPhotoOptions by remember { mutableStateOf(false) }
    var selectedImageUri by remember {
        mutableStateOf<Uri?>(initialPhotoUri?.let { Uri.parse(it) })
    }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current

    // Create a temporary file for camera photos
    fun createImageFile(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "MEDICATION_$timeStamp"
        val storageDir = File(context.getExternalFilesDir(null), "Pictures")
        storageDir.mkdirs()

        val imageFile = File(storageDir, "$imageFileName.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedImageUri = cameraImageUri
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraImageUri = createImageFile()
            cameraLauncher.launch(cameraImageUri!!)
        }
    }

    // Gallery permission launcher (for Android 13+)
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            galleryLauncher.launch("image/*")
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.add_medication),
                        color = androidx.compose.ui.graphics.Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                    titleContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Large instruction text
            Text(
                text = when (currentLanguage) {
                    "hi" -> "अपनी दवा जोड़ें"
                    "gu" -> "તમારી દવા ઉમેરો"
                    else -> "Add Your Medication"
                },
                fontSize = 28.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Photo Card - shows image if selected, otherwise camera icon
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFF5F5F5)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                onClick = { showPhotoOptions = true }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        // Show the selected image
                        Image(
                            painter = rememberAsyncImagePainter(selectedImageUri),
                            contentDescription = stringResource(R.string.medication_photo),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Small camera icon overlay to indicate it can be changed
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(32.dp),
                                    tint = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    } else {
                        // Show camera icon placeholder
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = androidx.compose.ui.graphics.Color.Gray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.add_photo),
                                fontSize = 20.sp,
                                color = androidx.compose.ui.graphics.Color.Gray
                            )
                            Text(
                                text = when (currentLanguage) {
                                    "hi" -> "दवा की फोटो लें"
                                    "gu" -> "દવાનો ફોટો લો"
                                    else -> "Take a photo of medicine"
                                },
                                fontSize = 16.sp,
                                color = androidx.compose.ui.graphics.Color.Gray
                            )
                        }
                    }
                }
            }

            // Medication Name Input
            OutlinedTextField(
                value = medicationName,
                onValueChange = { medicationName = it },
                label = {
                    Text(
                        stringResource(R.string.medication_name),
                        fontSize = 18.sp
                    )
                },
                placeholder = {
                    Text(
                        stringResource(R.string.medication_name_hint),
                        fontSize = 18.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                    focusedLabelColor = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Large Next Button
            Button(
                onClick = {
                    if (medicationName.isNotEmpty()) {
                        onNext(medicationName, selectedImageUri?.toString())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                ),
                enabled = medicationName.isNotEmpty()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.next),
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }

        // Photo options bottom sheet
        if (showPhotoOptions) {
            AlertDialog(
                onDismissRequest = { showPhotoOptions = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.photo),
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Take Photo button
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                            ),
                            onClick = {
                                showPhotoOptions = false
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = androidx.compose.ui.graphics.Color.White
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.take_photo),
                                    fontSize = 20.sp,
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }

                        // Choose from Gallery button
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                            ),
                            onClick = {
                                showPhotoOptions = false
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    galleryPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                                } else {
                                    galleryLauncher.launch("image/*")
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = androidx.compose.ui.graphics.Color.White
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.choose_from_gallery),
                                    fontSize = 20.sp,
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPhotoOptions = false }) {
                        Text(stringResource(R.string.cancel), fontSize = 18.sp)
                    }
                }
            )
        }
    }
}
