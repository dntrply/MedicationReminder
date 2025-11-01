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
import androidx.compose.ui.draw.shadow
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
import com.medreminder.app.ui.AddMedicationScreen
import com.medreminder.app.ui.AddMedicationStep1
import com.medreminder.app.ui.AddMedicationStep2
import com.medreminder.app.ui.MedicationCard
import com.medreminder.app.ui.MedicationViewModel
import com.medreminder.app.ui.OverdueMedicationCard
import com.medreminder.app.ui.OverdueMedicationsSection
import com.medreminder.app.ui.TimelineView
import com.medreminder.app.ui.ProfileIndicator
import com.medreminder.app.ui.ProfileViewModel
import com.medreminder.app.ui.SetReminderTimesScreen
import com.medreminder.app.ui.SettingsScreen
import com.medreminder.app.ui.TranscriptionConsentDialog
import com.medreminder.app.ui.components.PhotoPickerDialog
import com.medreminder.app.ui.components.rememberPhotoPickerState
import com.medreminder.app.ui.theme.MedicationReminderTheme
import com.medreminder.app.utils.AudioTranscriptionService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.medreminder.app.data.SettingsStore
import com.medreminder.app.data.userPrefs
import kotlin.system.exitProcess

/**
 * Main entry point for the application
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set app locale based on saved preference
        val savedLanguage = getCurrentLanguage(this)
        setAppLocale(this, savedLanguage)

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
                // Draft medication state for add flow (Option B extended)
                var draftMedicationId by remember { mutableStateOf<Long?>(null) }
                var draftMedication by remember { mutableStateOf<Medication?>(null) }
                var draftHasTimes by remember { mutableStateOf(false) }

                // Data class for temporary medication data during add/edit flow
                data class TempMedicationData(
                    val name: String,
                    val photoUri: String?,
                    val audioPath: String?
                )

                var tempMedication by remember {
                    mutableStateOf<TempMedicationData?>(null)
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
                            // Reset draft state at the start of a new add flow
                            draftMedicationId = null
                            draftMedication = null
                            draftHasTimes = false
                            currentScreen = "add_medication"
                        },
                        onEditMedication = { medication ->
                            medicationToEdit = medication
                            tempMedication = TempMedicationData(
                                medication.name,
                                medication.photoUri,
                                medication.audioNotePath
                            )
                            currentScreen = "edit_reminder"
                        },
                        onDebugData = {
                            currentScreen = "debug_data"
                        },
                        onOpenSettings = {
                            currentScreen = "settings"
                        },
                        onManageProfiles = {
                            currentScreen = "profiles"
                        },
                        onViewHistory = {
                            currentScreen = "history"
                        }
                    )
                    "add_medication" -> AddMedicationScreen(
                        currentLanguage = currentLanguage,
                        initialName = tempMedication?.name ?: "",
                        initialPhotoUri = tempMedication?.photoUri,
                        initialAudioPath = tempMedication?.audioPath,
                        onBack = {
                            // Determine if any saved details exist using draft (more reliable than temp state)
                            val hasDetails = draftMedication?.let { d ->
                                d.name.isNotBlank() || d.photoUri != null || d.audioNotePath != null
                            } ?: false
                            tempMedication = null
                            // If a draft exists with no times and no details, clean it up
                            if (draftMedicationId != null && !draftHasTimes && !hasDetails) {
                                draftMedication?.let { draft ->
                                    viewModel.deleteMedication(draft)
                                }
                                draftMedicationId = null
                                draftMedication = null
                            }
                            currentScreen = "home"
                        },
                        onNext = { name, photoUri, audioPath ->
                            tempMedication = TempMedicationData(name, photoUri, audioPath)
                            // Ensure draft exists/updates immediately when moving to time selection
                            lifecycleScope.launch(Dispatchers.IO) {
                                if (draftMedicationId == null) {
                                    val activeProfileId = viewModel.getActiveProfileId()
                                    val draft = Medication(
                                        profileId = activeProfileId,
                                        name = name,
                                        photoUri = photoUri,
                                        audioNotePath = audioPath,
                                        reminderTimesJson = null
                                    )
                                    val newId = viewModel.insertMedicationReturnId(draft)
                                    withContext(Dispatchers.Main) {
                                        draftMedicationId = newId
                                        draftMedication = draft.copy(id = newId)
                                    }
                                } else {
                                    val activeProfileId = viewModel.getActiveProfileId()
                                    val updated = (draftMedication ?: Medication(
                                        id = draftMedicationId!!,
                                        profileId = activeProfileId,
                                        name = name,
                                        photoUri = photoUri,
                                        audioNotePath = audioPath
                                    )).copy(
                                        name = name,
                                        photoUri = photoUri,
                                        audioNotePath = audioPath
                                    )
                                    viewModel.updateMedication(updated)
                                    withContext(Dispatchers.Main) {
                                        draftMedication = updated
                                    }
                                }
                            }
                            currentScreen = "set_reminder"
                        },
                        onAutosaveDetails = { name, photoUri, audioPath ->
                            // Create or update draft medication record
                            lifecycleScope.launch(Dispatchers.IO) {
                                if (draftMedicationId == null) {
                                    val activeProfileId = viewModel.getActiveProfileId()
                                    val draft = Medication(
                                        profileId = activeProfileId,
                                        name = name,
                                        photoUri = photoUri,
                                        audioNotePath = audioPath,
                                        reminderTimesJson = null
                                    )
                                    val newId = viewModel.insertMedicationReturnId(draft)
                                    withContext(Dispatchers.Main) {
                                        draftMedicationId = newId
                                        draftMedication = draft.copy(id = newId)
                                    }
                                } else {
                                    val activeProfileId = viewModel.getActiveProfileId()
                                    val updated = (draftMedication ?: Medication(
                                        id = draftMedicationId!!,
                                        profileId = activeProfileId,
                                        name = name,
                                        photoUri = photoUri,
                                        audioNotePath = audioPath
                                    )).copy(
                                        name = name,
                                        photoUri = photoUri,
                                        audioNotePath = audioPath
                                    )
                                    viewModel.updateMedication(updated)
                                    withContext(Dispatchers.Main) {
                                        draftMedication = updated
                                    }
                                }
                            }
                        }
                    )
                    "edit_medication" -> medicationToEdit?.let { medication ->
                        AddMedicationScreen(
                            currentLanguage = currentLanguage,
                            initialName = tempMedication?.name ?: medication.name,
                            initialPhotoUri = tempMedication?.photoUri ?: medication.photoUri,
                            initialAudioPath = tempMedication?.audioPath ?: medication.audioNotePath,
                            onBack = {
                                tempMedication = null
                                medicationToEdit = null
                                currentScreen = "home"
                            },
                            onNext = { name, photoUri, audioPath ->
                                tempMedication = TempMedicationData(name, photoUri, audioPath)
                                currentScreen = "edit_reminder"
                            }
                        )
                    }
                    "set_reminder" -> tempMedication?.let { tempData ->
                        SetReminderTimesScreen(
                            currentLanguage = currentLanguage,
                            medicationName = tempData.name,
                            medicationPhotoUri = tempData.photoUri,
                            onBack = {
                                // Navigate home; clean up only if no times and truly no saved details
                                val hasDetails = draftMedication?.let { d ->
                                    d.name.isNotBlank() || d.photoUri != null || d.audioNotePath != null
                                } ?: false
                                tempMedication = null
                                if (draftMedicationId != null && !draftHasTimes && !hasDetails) {
                                    draftMedication?.let { draft ->
                                        viewModel.deleteMedication(draft)
                                    }
                                    draftMedicationId = null
                                    draftMedication = null
                                }
                                currentScreen = "home"
                            },
                            onSave = { reminderTimes ->
                                // Upsert times into the draft medication and schedule notifications
                                val reminderJson = reminderTimes.joinToString(",") { rt ->
                                    """{"hour":${rt.hour},"minute":${rt.minute},"days":[${rt.daysOfWeek.joinToString(",")}]}"""
                                }
                                val jsonArray = "[$reminderJson]"

                                lifecycleScope.launch(Dispatchers.IO) {
                                    if (draftMedicationId == null) {
                                        // Edge case: if no draft yet, create one now then proceed
                                        val activeProfileId = viewModel.getActiveProfileId()
                                        val draft = Medication(
                                            profileId = activeProfileId,
                                            name = tempData.name,
                                            photoUri = tempData.photoUri,
                                            audioNotePath = tempData.audioPath,
                                            reminderTimesJson = jsonArray
                                        )
                                        val newId = viewModel.insertMedicationReturnId(draft)
                                        val created = draft.copy(id = newId)
                                        withContext(Dispatchers.Main) {
                                            draftMedicationId = newId
                                            draftMedication = created
                                            draftHasTimes = true
                                        }
                                        NotificationScheduler.scheduleMedicationNotifications(this@MainActivity, created)
                                    } else {
                                        val activeProfileId = viewModel.getActiveProfileId()
                                        val updated = (draftMedication ?: Medication(
                                            id = draftMedicationId!!,
                                            profileId = activeProfileId,
                                            name = tempData.name,
                                            photoUri = tempData.photoUri,
                                            audioNotePath = tempData.audioPath
                                        )).copy(
                                            name = tempData.name,
                                            photoUri = tempData.photoUri,
                                            audioNotePath = tempData.audioPath,
                                            reminderTimesJson = jsonArray
                                        )
                                        viewModel.updateMedication(updated)
                                        // Re-schedule notifications for updates
                                        NotificationScheduler.cancelMedicationNotifications(this@MainActivity, updated)
                                        NotificationScheduler.scheduleMedicationNotifications(this@MainActivity, updated)
                                        withContext(Dispatchers.Main) {
                                            draftMedication = updated
                                            draftHasTimes = true
                                        }
                                    }
                                }
                            }
                        )
                    }
                    "edit_reminder" -> medicationToEdit?.let { medication ->
                        tempMedication?.let { tempData ->
                            SetReminderTimesScreen(
                                currentLanguage = currentLanguage,
                                medicationName = tempData.name,
                                medicationPhotoUri = tempData.photoUri,
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
                                        name = tempData.name,
                                        photoUri = tempData.photoUri,
                                        audioNotePath = tempData.audioPath,
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
                    "settings" -> {
                        SettingsScreen(
                            currentLanguage = currentLanguage,
                            onBack = { currentScreen = "home" },
                            onLanguageChange = { newLang ->
                                currentLanguage = newLang
                                saveLanguagePreference(this@MainActivity, newLang)
                                recreate()
                            }
                        )
                    }
                    "history" -> {
                        com.medreminder.app.ui.HistoryScreen(
                            currentLanguage = currentLanguage,
                            onBack = { currentScreen = "home" }
                        )
                    }
                    "profiles" -> {
                        com.medreminder.app.ui.ProfileManagementScreen(
                            onBack = { currentScreen = "home" }
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

// Language preference helpers (DataStore)
private fun saveLanguagePreference(context: Context, lang: String) {
    runBlocking { SettingsStore.setLanguage(context, lang) }
}

private fun getLanguagePreference(context: Context): String {
    return runBlocking { SettingsStore.languageFlow(context).first() }
}

private fun getCurrentLanguage(context: Context): String = getLanguagePreference(context)

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
    onOpenSettings: () -> Unit = {},
    onManageProfiles: () -> Unit = {},
    onViewHistory: () -> Unit = {}
) {
    val medications by viewModel.medications.collectAsState(initial = emptyList())
    var showExitDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTimelineView by remember { mutableStateOf(true) } // Default to Timeline view
    var timelineViewCounter by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // Get active profile for display
    val profileViewModel: ProfileViewModel = viewModel()
    val activeProfile by profileViewModel.activeProfile.collectAsState()
    val profiles by profileViewModel.profiles.collectAsState(initial = emptyList())

    // Debug logging
    android.util.Log.d("HomeScreen", "Active profile: ${activeProfile?.name ?: "null"}")

    // State for profile switcher dropdown
    var showProfileMenu by remember { mutableStateOf(false) }

    // State for collapsible lower section
    var isLowerSectionExpanded by remember { mutableStateOf(true) }

    // Observe pending medications for overdue detection
    val pendingMeds by PendingMedicationTracker.pendingMedicationsFlow(context)
        .collectAsState(initial = emptyList())

    // Filter overdue medications (from previous days)
    val currentCalendar = java.util.Calendar.getInstance()
    val today = currentCalendar.get(java.util.Calendar.DAY_OF_YEAR)
    val thisYear = currentCalendar.get(java.util.Calendar.YEAR)

    val overdueMedications = pendingMeds.filter { pending ->
        // Calculate scheduled time from timestamp and hour/minute
        val timestampCal = java.util.Calendar.getInstance()
        timestampCal.timeInMillis = pending.timestamp

        // Set the calendar to the scheduled time
        val scheduledCal = java.util.Calendar.getInstance()
        scheduledCal.timeInMillis = pending.timestamp
        scheduledCal.set(java.util.Calendar.HOUR_OF_DAY, pending.hour)
        scheduledCal.set(java.util.Calendar.MINUTE, pending.minute)
        scheduledCal.set(java.util.Calendar.SECOND, 0)
        scheduledCal.set(java.util.Calendar.MILLISECOND, 0)

        val pendingDay = scheduledCal.get(java.util.Calendar.DAY_OF_YEAR)
        val pendingYear = scheduledCal.get(java.util.Calendar.YEAR)

        // Overdue if from a previous day or previous year
        (pendingYear < thisYear) || (pendingYear == thisYear && pendingDay < today)
    }

    // Handle back button press - show exit confirmation
    // Only enable when dialogs are not open to avoid conflicts
    BackHandler(enabled = !showMenu) {
        showExitDialog = true
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.White,
        floatingActionButton = {
            // Only show FAB when there are medications (otherwise the empty state has its own large Add button)
            if (medications.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onAddMedication,
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_medication),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    // Profile indicator in top bar
                    val profile = activeProfile
                    if (profile != null) {
                        Box {
                            IconButton(onClick = {
                                android.util.Log.d("ProfileMenu", "Opening profile menu")
                                showProfileMenu = true
                            }) {
                                if (profile.photoUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(Uri.parse(profile.photoUri)),
                                        contentDescription = "Profile: ${profile.name}",
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, androidx.compose.ui.graphics.Color.White, CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(androidx.compose.ui.graphics.Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = profile.name.take(1).uppercase(),
                                            color = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                                            fontSize = 16.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Profile switcher dropdown - anchored to the button
                            DropdownMenu(
                                expanded = showProfileMenu,
                                onDismissRequest = { showProfileMenu = false }
                            ) {
                                android.util.Log.d("ProfileMenu", "DropdownMenu content composing, profiles count: ${profiles.size}")
                                // List all profiles
                                profiles.forEach { profileItem ->
                                    val isActive = profileItem.id == activeProfile?.id
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            // Profile icon
                                            if (profileItem.photoUri != null) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(Uri.parse(profileItem.photoUri)),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(androidx.compose.ui.graphics.Color(0xFF4A90E2)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = profileItem.name.take(1).uppercase(),
                                                        color = androidx.compose.ui.graphics.Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                    )
                                                }
                                            }
                                        },
                                        text = {
                                            Text(text = profileItem.name)
                                        },
                                        trailingIcon = {
                                            if (isActive) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Active",
                                                    tint = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                                                )
                                            }
                                        },
                                        onClick = {
                                            profileViewModel.switchProfile(profileItem.id)
                                            showProfileMenu = false
                                        }
                                    )
                                }

                                // Divider
                                Divider()

                                // Manage Profiles option
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text("Manage Profiles...")
                                    },
                                    onClick = {
                                        showProfileMenu = false
                                        onManageProfiles()
                                    }
                                )
                            }
                        }
                    }
                },
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
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                    Text(stringResource(R.string.settings), fontSize = 18.sp)
                                }
                            },
                            onClick = {
                                showMenu = false
                                onOpenSettings()
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null)
                                    Text("Profiles", fontSize = 18.sp)
                                }
                            },
                            onClick = {
                                showMenu = false
                                onManageProfiles()
                            }
                        )
                        Divider()
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
                                onViewHistory()
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
                    text = stringResource(R.string.start_tracking_medications),
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
                // Overdue Medications Section - Expandable when lower section is collapsed
                if (overdueMedications.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(androidx.compose.ui.graphics.Color(0xFFFFF3F3)) // Light red background
                            .then(
                                if (isLowerSectionExpanded)
                                    Modifier.heightIn(max = 200.dp)
                                else
                                    Modifier.weight(1f) // Take up remaining space when lower section is collapsed
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        OverdueMedicationsSection(
                            overdueMedications = overdueMedications,
                            medications = medications,
                            currentLanguage = currentLanguage,
                            context = context
                        )
                    }

                    // Divider after overdue section
                    Divider(
                        thickness = 1.dp,
                        color = androidx.compose.ui.graphics.Color(0xFFE0E0E0)
                    )
                }

                // Lower section with drag handle and collapsible content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLowerSectionExpanded)
                                Modifier.weight(1f)
                            else
                                Modifier.height(80.dp) // Just show the drag handle when collapsed
                        )
                        .background(androidx.compose.ui.graphics.Color.White)
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { isLowerSectionExpanded = !isLowerSectionExpanded }
                            .background(androidx.compose.ui.graphics.Color(0xFFF8F8F8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isLowerSectionExpanded)
                                    Icons.Default.ExpandMore
                                else
                                    Icons.Default.ExpandLess,
                                contentDescription = if (isLowerSectionExpanded) "Collapse" else "Expand",
                                tint = androidx.compose.ui.graphics.Color.Gray
                            )
                            Text(
                                text = if (isLowerSectionExpanded)
                                    stringResource(R.string.collapse)
                                else
                                    stringResource(R.string.expand),
                                fontSize = 14.sp,
                                color = androidx.compose.ui.graphics.Color.Gray,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        }
                    }

                    // Only show content when expanded
                    if (isLowerSectionExpanded) {
                        Column(modifier = Modifier.fillMaxSize()) {
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
                                text = stringResource(R.string.list_view),
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
                                text = stringResource(R.string.timeline_view),
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

                // Section Header for main content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            showTimelineView -> stringResource(R.string.todays_schedule)
                            else -> stringResource(R.string.your_medications)
                        },
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFF424242)
                    )
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

                            // Add some bottom padding to account for FAB
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

    // Language selection dialog removed; language changes live under Settings

    // Transcription consent dialog
    if (viewModel.showTranscriptionConsentDialog) {
        TranscriptionConsentDialog(
            currentLanguage = currentLanguage,
            onDismiss = { viewModel.dismissTranscriptionConsentDialog() },
            onAccept = { viewModel.onTranscriptionConsentAccepted() },
            onDecline = { viewModel.onTranscriptionConsentDeclined() }
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
                        text = stringResource(R.string.exit_app),
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.exit_app_message),
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
                            stringResource(R.string.yes_exit),
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
                            stringResource(R.string.cancel),
                            fontSize = 18.sp
                        )
                    }
                }
            )
    }
}
