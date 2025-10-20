package com.medreminder.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medreminder.app.data.PresetTimes
import com.medreminder.app.data.PresetTimesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String,
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current

    var presets by remember { mutableStateOf(PresetTimesManager.getPresetTimes(context)) }

    // Auto-save preset changes
    LaunchedEffect(presets) {
        PresetTimesManager.savePresetTimes(context, presets)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentLanguage) {
                            "hi" -> "सेटिंग्स"
                            "gu" -> "સેટિંગ્સ"
                            else -> "Settings"
                        },
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
                text = when (currentLanguage) {
                    "hi" -> "भाषा"
                    "gu" -> "ભાષા"
                    else -> "Language"
                },
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
            }

            Divider(Modifier.padding(vertical = 8.dp))

            // Preset times section
            Text(
                text = when (currentLanguage) {
                    "hi" -> "समय प्रीसेट"
                    "gu" -> "સમય પ્રીસેટ"
                    else -> "Preset Times"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
            )

            // Reuse the existing TimePresetRow from SetReminderTimesScreen
            TimePresetRow(
                label = when (currentLanguage) {
                    "hi" -> "सुबह"
                    "gu" -> "સવાર"
                    else -> "Morning"
                },
                hour = presets.morningHour,
                minute = presets.morningMinute,
                onHourChange = { presets = presets.copy(morningHour = it) },
                onMinuteChange = { presets = presets.copy(morningMinute = it) }
            )

            TimePresetRow(
                label = when (currentLanguage) {
                    "hi" -> "दोपहर"
                    "gu" -> "બપોર"
                    else -> "Lunch"
                },
                hour = presets.lunchHour,
                minute = presets.lunchMinute,
                onHourChange = { presets = presets.copy(lunchHour = it) },
                onMinuteChange = { presets = presets.copy(lunchMinute = it) }
            )

            TimePresetRow(
                label = when (currentLanguage) {
                    "hi" -> "शाम"
                    "gu" -> "સાંજ"
                    else -> "Evening"
                },
                hour = presets.eveningHour,
                minute = presets.eveningMinute,
                onHourChange = { presets = presets.copy(eveningHour = it) },
                onMinuteChange = { presets = presets.copy(eveningMinute = it) }
            )

            TimePresetRow(
                label = when (currentLanguage) {
                    "hi" -> "रात"
                    "gu" -> "રાત્રે"
                    else -> "Bedtime"
                },
                hour = presets.bedtimeHour,
                minute = presets.bedtimeMinute,
                onHourChange = { presets = presets.copy(bedtimeHour = it) },
                onMinuteChange = { presets = presets.copy(bedtimeMinute = it) }
            )

        }
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
