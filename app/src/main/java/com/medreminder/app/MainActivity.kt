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
import com.medreminder.app.ui.MedicationViewModel
import com.medreminder.app.ui.ProfileViewModel
import com.medreminder.app.ui.SetReminderTimesScreen
import com.medreminder.app.ui.SettingsScreen
import com.medreminder.app.ui.components.PhotoPickerDialog
import com.medreminder.app.ui.components.rememberPhotoPickerState
import com.medreminder.app.ui.theme.MedicationReminderTheme
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
    val context = LocalContext.current
    val audioPlayer = remember { com.medreminder.app.utils.AudioPlayer(context) }
    var isPlaying by remember { mutableStateOf(false) }

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

            Spacer(modifier = Modifier.width(8.dp))

            // Middle section: optional slim audio column + name/schedule column
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                // Always reserve a slim column for audio to keep name alignment consistent
                Column(
                    modifier = Modifier
                        .width(24.dp)
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
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Stop audio" else "Play audio",
                                tint = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))

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

// Data class to hold medication times for timeline display
data class MedicationTimeSlot(
    val medication: Medication,
    val hour: Int,
    val minute: Int
)

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

    // Observe pending medications reactively from DataStore
    val allPendingMeds by PendingMedicationTracker.pendingMedicationsFlow(context)
        .collectAsState(initial = emptyList())

    // Filter to only TODAY's pending medications (exclude overdue from previous days)
    val pendingMeds = remember(allPendingMeds) {
        val currentCal = java.util.Calendar.getInstance()
        val today = currentCal.get(java.util.Calendar.DAY_OF_YEAR)
        val thisYear = currentCal.get(java.util.Calendar.YEAR)

        allPendingMeds.filter { pending ->
            val scheduledCal = java.util.Calendar.getInstance()
            scheduledCal.timeInMillis = pending.timestamp
            scheduledCal.set(java.util.Calendar.HOUR_OF_DAY, pending.hour)
            scheduledCal.set(java.util.Calendar.MINUTE, pending.minute)

            val pendingDay = scheduledCal.get(java.util.Calendar.DAY_OF_YEAR)
            val pendingYear = scheduledCal.get(java.util.Calendar.YEAR)

            // Only include today's pending medications
            (pendingYear == thisYear && pendingDay == today)
        }
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

    // Find the upcoming medication hour (prioritizing actual pending notifications)
    // Recalculate when timeSlots, todayHistory, or pendingMeds change
    val upcomingHour = remember(timeSlots, todayHistory, pendingMeds) {
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

        // Outstanding = those with an active pending notification (i.e., notification was sent)
        val outstandingSlots = timeSlots.filter { slot ->
            pendingMeds.any {
                it.medicationId == slot.medication.id &&
                it.hour == slot.hour &&
                it.minute == slot.minute
            }
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
            .padding(8.dp)
    ) {

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
                        text = stringResource(R.string.no_times_set),
                        fontSize = 20.sp,
                        color = androidx.compose.ui.graphics.Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Timeline with horizontal and vertical scrolling
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
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
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Container for timeline and medications
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 100.dp, max = 200.dp)
                                    .height(360.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                // Hour marker line (centered)
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(360.dp)
                                        .background(androidx.compose.ui.graphics.Color(0xFFE0E0E0))
                                )

                                // Find medications for this hour and bucket them into 4 x 15-minute slices
                                val medicationsInHour = timeSlots.filter { it.hour == hour }.sortedBy { it.minute }
                                val hourHeightDp = 360f
                                val bucketCount = 4
                                val bucketHeightDp = hourHeightDp / bucketCount
                                // Visual tuning for tile layout
                                val tileBlockDp = 96f // image + label + spacing (larger to avoid clipping)
                                val verticalStepFactor = 0.40f // 40% downward step for visible overlap

                                // Build buckets: 0..3 => 00-14, 15-29, 30-44, 45-59
                                val buckets: Map<Int, List<MedicationTimeSlot>> = (0 until bucketCount).associateWith { idx ->
                                    medicationsInHour.filter { it.minute / 15 == idx }
                                }

                                buckets.forEach { (bucketIndex, group) ->
                                    if (group.isEmpty()) return@forEach

                                    // Base Y for this bucket
                                    val bucketTop = bucketIndex * bucketHeightDp
                                    val bucketBottom = bucketTop + bucketHeightDp

                                    // Stack items with overlap inside the bucket
                                    // Reverse order so earlier medications appear on top (more visible/urgent)
                                    group.reversed().forEachIndexed { reverseIdx, slot ->
                                        val idx = group.size - 1 - reverseIdx

                                        // Vertical offset: shift later items down for visible overlap
                                        val verticalStepDp = 20f // vertical shift per medication
                                        val yOffset = idx * verticalStepDp
                                        val position = bucketTop + yOffset

                                        // Horizontal offset: push later items right
                                        val horizontalStepDp = 24f // horizontal shift per medication
                                        val xOffset = idx * horizontalStepDp

                                        Column(
                                            modifier = Modifier
                                                .offset(x = xOffset.dp, y = position.dp)
                                                .wrapContentWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
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
                                                    takenCal.get(java.util.Calendar.MINUTE) == slot.minute &&
                                                    history.action == "TAKEN"  // Only count as taken if action is TAKEN
                                                if (matches) {
                                                    Log.d("Timeline", "Found match for ${slot.medication.name} at ${slot.hour}:${slot.minute}")
                                                }
                                                matches
                                            }

                                            // A medication should only be marked as "outstanding" if:
                                            // 1. It's in the pending notification tracker (meaning a notification was actually sent)
                                            // OR
                                            // 2. It has history (meaning it was taken/skipped, so we know notification was sent)
                                            val hasPendingNotification = pendingMeds.any {
                                                it.medicationId == slot.medication.id &&
                                                it.hour == slot.hour &&
                                                it.minute == slot.minute
                                            }

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
                                                // Earlier medications get higher elevation (they're on top)
                                                val elevation = if (group.size > 1) {
                                                    (2 + (group.size - idx - 1) * 2).dp
                                                } else {
                                                    2.dp
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .shadow(
                                                            elevation = elevation,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
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
    return String.format("%02d:00 %s", displayHour, amPm)
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
                            stringResource(R.string.taken),
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
                            stringResource(R.string.snooze),
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
                            stringResource(R.string.skip),
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
                    stringResource(R.string.cancel),
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
    initialAudioPath: String? = null,
    onBack: () -> Unit,
    onNext: (String, String?, String?) -> Unit, // name, photoUri, audioPath
    onAutosaveDetails: (String, String?, String?) -> Unit = { _, _, _ -> }
) {
    // Wizard state
    var wizardStep by remember { mutableStateOf(1) }
    var medicationName by remember { mutableStateOf(initialName) }
    var selectedPhotoUri by remember { mutableStateOf(initialPhotoUri) }
    var audioPath by remember { mutableStateOf(initialAudioPath) }

    when (wizardStep) {
        1 -> AddMedicationStep1(
            currentLanguage = currentLanguage,
            initialName = medicationName,
            initialPhotoUri = selectedPhotoUri,
            onBack = onBack,
            onNext = { name, photoUri ->
                medicationName = name
                selectedPhotoUri = photoUri
                // Autosave after step 1
                onAutosaveDetails(name, photoUri, audioPath)
                wizardStep = 2
            }
        )
        2 -> AddMedicationStep2(
            currentLanguage = currentLanguage,
            initialAudioPath = audioPath,
            onBack = { wizardStep = 1 },
            onNext = { audio ->
                audioPath = audio
                // Final callback to proceed to schedule
                onNext(medicationName, selectedPhotoUri, audio)
            },
            onSkip = {
                // Skip audio, proceed directly to schedule
                onNext(medicationName, selectedPhotoUri, null)
            },
            onAutosaveAudio = { audio ->
                audioPath = audio
                onAutosaveDetails(medicationName, selectedPhotoUri, audio)
            }
        )
    }
}

// Step 1: Photo + Name
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationStep1(
    currentLanguage: String = "en",
    initialName: String = "",
    initialPhotoUri: String? = null,
    onBack: () -> Unit,
    onNext: (String, String?) -> Unit // name, photoUri
) {
    var medicationName by remember { mutableStateOf(initialName) }
    var selectedImageUri by remember {
        mutableStateOf<Uri?>(initialPhotoUri?.let { Uri.parse(it) })
    }

    val context = LocalContext.current

    // Photo picker state
    val photoPickerState = rememberPhotoPickerState(
        initialUri = selectedImageUri
    ) { uri ->
        selectedImageUri = uri
    }

    // Photo picker dialog
    PhotoPickerDialog(
        state = photoPickerState,
        title = "Add Medication Photo"
    )

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
            // Progress indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.step_1_of_3),
                    fontSize = 16.sp,
                    color = androidx.compose.ui.graphics.Color.Gray,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
            // Large instruction text
            Text(
                text = stringResource(R.string.what_is_medication),
                fontSize = 28.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Photo Card - shows image if selected, otherwise camera icon
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFF5F5F5)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                onClick = { photoPickerState.showDialog(true) }
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
                                text = stringResource(R.string.take_photo_medicine),
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

            // Flexible spacer to push button to bottom
            Spacer(modifier = Modifier.weight(1f))

            // Next Button
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

    }
}

// Step 2: Audio Instructions
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationStep2(
    currentLanguage: String = "en",
    initialAudioPath: String? = null,
    onBack: () -> Unit,
    onNext: (String?) -> Unit, // audioPath
    onSkip: () -> Unit,
    onAutosaveAudio: (String?) -> Unit
) {
    // Audio recording state
    var audioPath by remember { mutableStateOf(initialAudioPath) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val audioRecorder = remember { com.medreminder.app.utils.AudioRecorder(context) }
    val audioPlayer = remember { com.medreminder.app.utils.AudioPlayer(context) }

    // Update recording duration every second while recording
    LaunchedEffect(isRecording) {
        while (isRecording) {
            recordingDuration = audioRecorder.getRecordingDuration()
            kotlinx.coroutines.delay(100)
        }
    }

    // Cleanup audio resources when screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            // Only cancel if actively recording (don't delete saved files!)
            if (isRecording) {
                audioRecorder.cancelRecording()
            }
            audioPlayer.release()
        }
    }

    // Autosave debounce for audio changes
    LaunchedEffect(audioPath) {
        if (audioPath != null) {
            kotlinx.coroutines.delay(350)
            onAutosaveAudio(audioPath)
        }
    }

    // Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Start recording
            val file = audioRecorder.startRecording()
            if (file != null) {
                isRecording = true
                recordingDuration = 0L
            }
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Progress indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.step_2_of_3),
                    fontSize = 16.sp,
                    color = androidx.compose.ui.graphics.Color.Gray,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large instruction text
            Text(
                text = stringResource(R.string.add_voice_instructions),
                fontSize = 28.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.Black
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.optional),
                fontSize = 18.sp,
                color = androidx.compose.ui.graphics.Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Note Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFF5F5F5)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.audio_instructions),
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )

                    if (audioPath != null) {
                        // Show audio controls when audio exists
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play/Stop button
                            Button(
                                onClick = {
                                    if (isPlaying) {
                                        audioPlayer.stop()
                                        isPlaying = false
                                    } else {
                                        audioPlayer.play(
                                            audioPath,
                                            onCompletion = { isPlaying = false },
                                            onError = { isPlaying = false }
                                        )
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (isPlaying) stringResource(R.string.stop_playback) else stringResource(R.string.play_audio),
                                    fontSize = 16.sp
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = {
                                    audioPlayer.stop()
                                    audioRecorder.deleteAudioFile(audioPath)
                                    audioPath = null
                                    isPlaying = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete_audio),
                                    tint = androidx.compose.ui.graphics.Color(0xFFE53935),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    } else if (isRecording) {
                        // Show recording controls
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(androidx.compose.ui.graphics.Color.Red)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format("%02d:%02d", recordingDuration / 1000 / 60, recordingDuration / 1000 % 60),
                                    fontSize = 24.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.Red
                                )
                            }

                            // Stop and Save button
                            Button(
                                onClick = {
                                    val file = audioRecorder.stopRecording()
                                    isRecording = false
                                    // Delete old audio file if it exists before setting new path
                                    if (audioPath != null && audioPath != file?.absolutePath) {
                                        audioRecorder.deleteAudioFile(audioPath)
                                    }
                                    audioPath = file?.absolutePath
                                    recordingDuration = 0L
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.stop_recording),
                                    fontSize = 18.sp
                                )
                            }

                            // Cancel button
                            OutlinedButton(
                                onClick = {
                                    audioRecorder.cancelRecording()
                                    isRecording = false
                                    recordingDuration = 0L
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.cancel),
                                    fontSize = 18.sp
                                )
                            }
                        }
                    } else {
                        // Show record button
                        Button(
                            onClick = {
                                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.record_audio), fontSize = 18.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Helper text
            Text(
                text = stringResource(R.string.continue_without_recording),
                fontSize = 14.sp,
                color = androidx.compose.ui.graphics.Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Skip Button (full width, prominent)
            OutlinedButton(
                onClick = {
                    audioPlayer.stop()
                    onSkip()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                border = BorderStroke(2.dp, androidx.compose.ui.graphics.Color(0xFF4A90E2)),
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.skip),
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.no_audio),
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Next Button (only enabled if audio recorded)
            Button(
                onClick = {
                    audioPlayer.stop()
                    onNext(audioPath)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                ),
                enabled = audioPath != null,
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.next),
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    if (audioPath != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Profile indicator that shows the currently active profile
 */
@Composable
fun ProfileIndicator(
    profile: com.medreminder.app.data.Profile?,
    onProfileClick: () -> Unit
) {
    android.util.Log.d("ProfileIndicator", "ProfileIndicator called with profile: ${profile?.name ?: "null"}")
    if (profile != null) {
        android.util.Log.d("ProfileIndicator", "Rendering profile indicator for: ${profile.name}")
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onProfileClick),
            color = androidx.compose.ui.graphics.Color(0xFFF5F5F5),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Profile image or icon
                if (profile.photoUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(profile.photoUri)),
                        contentDescription = "Profile photo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(2.dp, androidx.compose.ui.graphics.Color(0xFF4A90E2), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Default profile icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(0xFF4A90E2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.name.take(1).uppercase(),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 18.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }

                // Profile name
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = profile.name,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                    Text(
                        text = "Active Profile",
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }

                // Icon to indicate clickable
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Change profile",
                    tint = androidx.compose.ui.graphics.Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

