package com.medreminder.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medreminder.app.R
import com.medreminder.app.data.PresetTimes
import com.medreminder.app.data.PresetTimesManager
import com.medreminder.app.data.SettingsStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String,
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current

    val presetsFromStore by PresetTimesManager.getPresetTimesFlow(context)
        .collectAsState(initial = PresetTimes())
    var presets by remember { mutableStateOf(presetsFromStore) }
    LaunchedEffect(presetsFromStore) { presets = presetsFromStore }

    val scope = rememberCoroutineScope()
    fun updatePresets(builder: (PresetTimes) -> PresetTimes) {
        val updated = builder(presets)
        presets = updated
        scope.launch { PresetTimesManager.savePresetTimes(context, updated) }
    }

    // Transcription consent dialog state
    var showTranscriptionConsentDialog by remember { mutableStateOf(false) }

    // Handle back button press
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        color = androidx.compose.ui.graphics.Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                    titleContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language section
            Text(
                text = stringResource(R.string.language_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageOptionCard(
                    label = "English",
                    selected = currentLanguage == "en",
                    onClick = { onLanguageChange("en") }
                )
                LanguageOptionCard(
                    label = "हिन्दी",
                    selected = currentLanguage == "hi",
                    onClick = { onLanguageChange("hi") }
                )
                LanguageOptionCard(
                    label = "ગુજરાતી",
                    selected = currentLanguage == "gu",
                    onClick = { onLanguageChange("gu") }
                )
                LanguageOptionCard(
                    label = "मराठी",
                    selected = currentLanguage == "mr",
                    onClick = { onLanguageChange("mr") }
                )
            }

            Divider(Modifier.padding(vertical = 8.dp))

            // Preset times section (collapsible, default collapsed)
            var presetsExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { presetsExpanded = !presetsExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.preset_times),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                )
                Icon(
                    imageVector = if (presetsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                )
            }

            if (presetsExpanded) {
                // Reuse the existing TimePresetRow from SetReminderTimesScreen
                TimePresetRow(
                    label = stringResource(R.string.morning),
                    hour = presets.morningHour,
                    minute = presets.morningMinute,
                    onHourChange = { newHour -> updatePresets { p -> p.copy(morningHour = newHour) } },
                    onMinuteChange = { newMinute -> updatePresets { p -> p.copy(morningMinute = newMinute) } }
                )

                TimePresetRow(
                    label = stringResource(R.string.lunch),
                    hour = presets.lunchHour,
                    minute = presets.lunchMinute,
                    onHourChange = { newHour -> updatePresets { p -> p.copy(lunchHour = newHour) } },
                    onMinuteChange = { newMinute -> updatePresets { p -> p.copy(lunchMinute = newMinute) } }
                )

                TimePresetRow(
                    label = stringResource(R.string.evening),
                    hour = presets.eveningHour,
                    minute = presets.eveningMinute,
                    onHourChange = { newHour -> updatePresets { p -> p.copy(eveningHour = newHour) } },
                    onMinuteChange = { newMinute -> updatePresets { p -> p.copy(eveningMinute = newMinute) } }
                )

                TimePresetRow(
                    label = stringResource(R.string.bedtime),
                    hour = presets.bedtimeHour,
                    minute = presets.bedtimeMinute,
                    onHourChange = { newHour -> updatePresets { p -> p.copy(bedtimeHour = newHour) } },
                    onMinuteChange = { newMinute -> updatePresets { p -> p.copy(bedtimeMinute = newMinute) } }
                )
            }

            Divider(Modifier.padding(vertical = 8.dp))

            // Notifications: Repeat interval
            Text(
                text = stringResource(R.string.notifications_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )

            val repeatMinutes by SettingsStore.repeatIntervalFlow(context).collectAsState(initial = 10)
            var sliderValue by remember { mutableStateOf(repeatMinutes.toFloat()) }
            LaunchedEffect(repeatMinutes) { sliderValue = repeatMinutes.toFloat() }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.repeat_interval, sliderValue.toInt()),
                    fontSize = 16.sp
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        scope.launch { SettingsStore.setRepeatInterval(context, sliderValue.toInt()) }
                    },
                    valueRange = 2f..120f,
                    steps = 118,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Privacy (single toggle for lock-screen details)
            Text(
                text = stringResource(R.string.privacy_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )

            val showFullOnLock by SettingsStore.showFullOnLockscreenFlow(context)
                .collectAsState(initial = false)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.show_full_details_lock_screen),
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                    Text(
                        text = stringResource(R.string.hidden_by_default),
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
                Switch(
                    checked = showFullOnLock,
                    onCheckedChange = { checked ->
                        scope.launch { SettingsStore.setShowFullOnLockscreen(context, checked) }
                    }
                )
            }

            Divider(Modifier.padding(vertical = 8.dp))

            // Features section
            Text(
                text = stringResource(R.string.features_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )

            val transcriptionEnabled by SettingsStore.transcriptionEnabledFlow(context)
                .collectAsState(initial = false)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audio_transcription),
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                    Text(
                        text = stringResource(R.string.audio_transcription_description),
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
                Switch(
                    checked = transcriptionEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            // User is turning ON - show consent dialog
                            showTranscriptionConsentDialog = true
                        } else {
                            // User is turning OFF - just disable it
                            scope.launch { SettingsStore.setTranscriptionEnabled(context, false) }
                        }
                    }
                )
            }

        }
    }

    // Transcription consent dialog
    if (showTranscriptionConsentDialog) {
        TranscriptionConsentDialog(
            currentLanguage = currentLanguage,
            onDismiss = { showTranscriptionConsentDialog = false },
            onAccept = {
                scope.launch {
                    // Save both the feature enabled and consent granted
                    SettingsStore.setTranscriptionEnabled(context, true)
                    SettingsStore.setTranscriptionConsent(context, granted = true)
                }
                showTranscriptionConsentDialog = false
            },
            onDecline = {
                scope.launch {
                    // Keep feature disabled and save that consent was declined
                    SettingsStore.setTranscriptionEnabled(context, false)
                    SettingsStore.setTranscriptionConsent(context, granted = false)
                }
                showTranscriptionConsentDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageOptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                androidx.compose.ui.graphics.Color(0xFF4A90E2)
            else
                androidx.compose.ui.graphics.Color(0xFFF5F5F5)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = if (selected)
                    androidx.compose.ui.graphics.Color.White
                else
                    androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 18.sp,
                color = if (selected)
                    androidx.compose.ui.graphics.Color.White
                else
                    androidx.compose.ui.graphics.Color.Black
            )
        }
    }
}
