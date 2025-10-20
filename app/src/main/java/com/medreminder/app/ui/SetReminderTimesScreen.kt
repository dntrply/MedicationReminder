package com.medreminder.app.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.medreminder.app.R
import com.medreminder.app.data.PresetTimes
import com.medreminder.app.data.PresetTimesManager
import com.medreminder.app.data.ReminderTime
import com.medreminder.app.data.formatTime
import com.medreminder.app.data.toDisplayString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetReminderTimesScreen(
    currentLanguage: String,
    medicationName: String,
    medicationPhotoUri: String? = null,
    initialReminderTimes: String? = null,
    onBack: () -> Unit,
    onSave: (List<ReminderTime>) -> Unit,
    onEditDetails: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var presetTimes by remember { mutableStateOf(PresetTimesManager.getPresetTimes(context)) }

    // Parse initial reminder times if provided (for editing)
    val parsedInitialTimes = remember(initialReminderTimes) {
        initialReminderTimes?.let { jsonString ->
            parseReminderTimesFromJson(jsonString)
        } ?: emptyList()
    }

    var reminderTimes by remember { mutableStateOf(parsedInitialTimes.sortedWith(compareBy({ it.hour }, { it.minute }))) }
    // Track newly added times in this session (using Set of hour:minute strings)
    var newlyAddedTimes by remember { mutableStateOf(setOf<String>()) }
    // Track if this is the first composition to skip initial auto-save
    var isInitialLoad by remember { mutableStateOf(true) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf<Int?>(null) } // Index of time being edited
    var selectedHour by remember { mutableStateOf(9) }
    var selectedMinute by remember { mutableStateOf(0) }
    var hourInputText by remember { mutableStateOf("") }
    var minuteInputText by remember { mutableStateOf("") }
    var isHourFocused by remember { mutableStateOf(false) }
    var isMinuteFocused by remember { mutableStateOf(false) }
    var showTimeError by remember { mutableStateOf(false) }
    var timeErrorMessage by remember { mutableStateOf("") }
    val hourFocusRequester = remember { FocusRequester() }
    val minuteFocusRequester = remember { FocusRequester() }

    // Helper function to sort reminder times chronologically
    fun sortReminderTimes(times: List<ReminderTime>): List<ReminderTime> {
        return times.sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    // Helper function to check if a time already exists with overlapping days
    fun hasDuplicateTime(hour: Int, minute: Int, days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)): Boolean {
        return reminderTimes.any { existing ->
            existing.hour == hour &&
            existing.minute == minute &&
            existing.daysOfWeek.any { it in days }
        }
    }

    // Auto-save whenever reminder times change (skip initial load)
    LaunchedEffect(reminderTimes) {
        if (isInitialLoad) {
            isInitialLoad = false
        } else {
            onSave(reminderTimes)
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Medication photo/icon
                        if (medicationPhotoUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(Uri.parse(medicationPhotoUri)),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Medication,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                        // Title with medication name
                        Column {
                            Text(
                                text = stringResource(R.string.set_reminder_times),
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = medicationName,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 22.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                maxLines = 1,
                                letterSpacing = 0.2.sp
                            )
                        }
                    }
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
                actions = {
                    // Edit name/photo button (only show when editing)
                    if (onEditDetails != null) {
                        IconButton(onClick = onEditDetails) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = when (currentLanguage) {
                                    "hi" -> "नाम / फोटो संपादित करें"
                                    "gu" -> "નામ / ફોટો સંપાદિત કરો"
                                    else -> "Edit Name / Photo"
                                },
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
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
            // Preset time buttons header with customize link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (currentLanguage) {
                        "hi" -> "लोकप्रिय समय:"
                        "gu" -> "લોકપ્રિય સમય:"
                        else -> "Quick Select:"
                    },
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontally scrollable preset times with staggered layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy((-20).dp)
            ) {
                PresetTimeButton(
                    label = when (currentLanguage) {
                        "hi" -> "सुबह"
                        "gu" -> "સવાર"
                        else -> "Morning"
                    },
                    time = formatTime(presetTimes.morningHour, presetTimes.morningMinute),
                    icon = Icons.Default.WbSunny,
                    modifier = Modifier.width(110.dp),
                    gradient = Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color(0xFFFFB74D), // Sunrise orange
                            androidx.compose.ui.graphics.Color(0xFFFFA726)  // Morning gold
                        )
                    ),
                    onClick = {
                        if (!hasDuplicateTime(presetTimes.morningHour, presetTimes.morningMinute)) {
                            val newTime = ReminderTime(presetTimes.morningHour, presetTimes.morningMinute)
                            reminderTimes = sortReminderTimes(reminderTimes + newTime)
                            newlyAddedTimes = newlyAddedTimes + "${newTime.hour}:${newTime.minute}"
                        }
                    }
                )
                PresetTimeButton(
                    label = when (currentLanguage) {
                        "hi" -> "दोपहर"
                        "gu" -> "બપોર"
                        else -> "Lunch"
                    },
                    time = formatTime(presetTimes.lunchHour, presetTimes.lunchMinute),
                    icon = Icons.Default.LunchDining,
                    modifier = Modifier.width(110.dp),
                    gradient = Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color(0xFF42A5F5), // Bright day blue
                            androidx.compose.ui.graphics.Color(0xFF2196F3)  // Midday blue
                        )
                    ),
                    onClick = {
                        if (!hasDuplicateTime(presetTimes.lunchHour, presetTimes.lunchMinute)) {
                            val newTime = ReminderTime(presetTimes.lunchHour, presetTimes.lunchMinute)
                            reminderTimes = sortReminderTimes(reminderTimes + newTime)
                            newlyAddedTimes = newlyAddedTimes + "${newTime.hour}:${newTime.minute}"
                        }
                    }
                )
                PresetTimeButton(
                    label = when (currentLanguage) {
                        "hi" -> "शाम"
                        "gu" -> "સાંજ"
                        else -> "Evening"
                    },
                    time = formatTime(presetTimes.eveningHour, presetTimes.eveningMinute),
                    icon = Icons.Default.WbTwilight,
                    modifier = Modifier.width(110.dp),
                    gradient = Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color(0xFFFF7043), // Sunset orange
                            androidx.compose.ui.graphics.Color(0xFFE64A19)  // Dusk red
                        )
                    ),
                    onClick = {
                        if (!hasDuplicateTime(presetTimes.eveningHour, presetTimes.eveningMinute)) {
                            val newTime = ReminderTime(presetTimes.eveningHour, presetTimes.eveningMinute)
                            reminderTimes = sortReminderTimes(reminderTimes + newTime)
                            newlyAddedTimes = newlyAddedTimes + "${newTime.hour}:${newTime.minute}"
                        }
                    }
                )
                PresetTimeButton(
                    label = when (currentLanguage) {
                        "hi" -> "रात"
                        "gu" -> "રાત્રે"
                        else -> "Bedtime"
                    },
                    time = formatTime(presetTimes.bedtimeHour, presetTimes.bedtimeMinute),
                    icon = Icons.Default.Nightlight,
                    modifier = Modifier.width(110.dp),
                    gradient = Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color(0xFF5C6BC0), // Night purple
                            androidx.compose.ui.graphics.Color(0xFF3F51B5)  // Deep night blue
                        )
                    ),
                    onClick = {
                        if (!hasDuplicateTime(presetTimes.bedtimeHour, presetTimes.bedtimeMinute)) {
                            val newTime = ReminderTime(presetTimes.bedtimeHour, presetTimes.bedtimeMinute)
                            reminderTimes = sortReminderTimes(reminderTimes + newTime)
                            newlyAddedTimes = newlyAddedTimes + "${newTime.hour}:${newTime.minute}"
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Add custom time button
            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color(0xFF4A90E2)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    when (currentLanguage) {
                        "hi" -> "विशेष समय जोड़ें"
                        "gu" -> "વિશેષ સમય ઉમેરો"
                        else -> "Add Custom Time"
                    },
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // List of reminder times
            if (reminderTimes.isNotEmpty()) {
                Text(
                    text = when (currentLanguage) {
                        "hi" -> "चयनित समय:"
                        "gu" -> "પસંદ કરેલ સમય:"
                        else -> "Selected Times:"
                    },
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                reminderTimes.forEachIndexed { index, time ->
                    val isNewlyAdded = newlyAddedTimes.contains("${time.hour}:${time.minute}")
                    ReminderTimeCard(
                        reminderTime = time,
                        currentLanguage = currentLanguage,
                        isNewlyAdded = isNewlyAdded,
                        onDelete = {
                            reminderTimes = reminderTimes.filterIndexed { i, _ -> i != index }
                        },
                        onEditDays = {
                            showDayPicker = index
                        }
                    )
                    if (index < reminderTimes.size - 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

        }

        // Time picker dialog
        if (showTimePicker) {
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = when (currentLanguage) {
                            "hi" -> "समय चुनें"
                            "gu" -> "સમય પસંદ કરો"
                            else -> "Select Time"
                        },
                        fontSize = 24.sp
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Hour and minute pickers
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hour
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    selectedHour = (selectedHour + 1) % 24
                                    isHourFocused = false
                                    hourInputText = ""
                                }) {
                                    Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(40.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp)
                                        .clickable {
                                            isHourFocused = true
                                            hourInputText = ""
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isHourFocused) {
                                        LaunchedEffect(Unit) {
                                            hourFocusRequester.requestFocus()
                                        }
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = hourInputText,
                                            onValueChange = { newValue ->
                                                val digitsOnly = newValue.filter { it.isDigit() }
                                                if (digitsOnly.length <= 2) {
                                                    hourInputText = digitsOnly
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontSize = 48.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                            ),
                                            modifier = Modifier
                                                .width(90.dp)
                                                .focusRequester(hourFocusRequester)
                                        )
                                    } else {
                                        Text(
                                            text = String.format("%02d", selectedHour),
                                            fontSize = 48.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    selectedHour = if (selectedHour == 0) 23 else selectedHour - 1
                                    isHourFocused = false
                                    hourInputText = ""
                                }) {
                                    Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(40.dp))
                                }
                            }

                            Text(":", fontSize = 48.sp, modifier = Modifier.padding(horizontal = 16.dp))

                            // Minute
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    selectedMinute = (selectedMinute + 1) % 60
                                    isMinuteFocused = false
                                    minuteInputText = ""
                                }) {
                                    Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(40.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp)
                                        .clickable {
                                            isMinuteFocused = true
                                            minuteInputText = ""
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isMinuteFocused) {
                                        LaunchedEffect(Unit) {
                                            minuteFocusRequester.requestFocus()
                                        }
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = minuteInputText,
                                            onValueChange = { newValue ->
                                                val digitsOnly = newValue.filter { it.isDigit() }
                                                if (digitsOnly.length <= 2) {
                                                    minuteInputText = digitsOnly
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontSize = 48.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                            ),
                                            modifier = Modifier
                                                .width(90.dp)
                                                .focusRequester(minuteFocusRequester)
                                        )
                                    } else {
                                        Text(
                                            text = String.format("%02d", selectedMinute),
                                            fontSize = 48.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    selectedMinute = if (selectedMinute == 0) 59 else selectedMinute - 1
                                    isMinuteFocused = false
                                    minuteInputText = ""
                                }) {
                                    Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(40.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Validate current input
                            var isValid = true

                            // Check if hour field is being edited with invalid value
                            if (isHourFocused && hourInputText.isNotEmpty()) {
                                val hourValue = hourInputText.toIntOrNull()
                                if (hourValue == null || hourValue !in 0..23) {
                                    timeErrorMessage = "Hour must be between 0 and 23"
                                    showTimeError = true
                                    isValid = false
                                }
                            }

                            // Check if minute field is being edited with invalid value
                            if (isMinuteFocused && minuteInputText.isNotEmpty()) {
                                val minuteValue = minuteInputText.toIntOrNull()
                                if (minuteValue == null || minuteValue !in 0..59) {
                                    timeErrorMessage = "Minutes must be between 0 and 59"
                                    showTimeError = true
                                    isValid = false
                                }
                            }

                            if (isValid) {
                                // Apply any pending input
                                if (isHourFocused && hourInputText.isNotEmpty()) {
                                    hourInputText.toIntOrNull()?.let { value ->
                                        if (value in 0..23) {
                                            selectedHour = value
                                        }
                                    }
                                }
                                if (isMinuteFocused && minuteInputText.isNotEmpty()) {
                                    minuteInputText.toIntOrNull()?.let { value ->
                                        if (value in 0..59) {
                                            selectedMinute = value
                                        }
                                    }
                                }

                                // Check for duplicate before adding
                                if (hasDuplicateTime(selectedHour, selectedMinute)) {
                                    timeErrorMessage = when (currentLanguage) {
                                        "hi" -> "यह समय पहले से जोड़ा गया है"
                                        "gu" -> "આ સમય પહેલેથી ઉમેરવામાં આવ્યો છે"
                                        else -> "This time is already added"
                                    }
                                    showTimeError = true
                                } else {
                                    val newTime = ReminderTime(selectedHour, selectedMinute)
                                    reminderTimes = sortReminderTimes(reminderTimes + newTime)
                                    newlyAddedTimes = newlyAddedTimes + "${newTime.hour}:${newTime.minute}"
                                    showTimePicker = false
                                    isHourFocused = false
                                    isMinuteFocused = false
                                    hourInputText = ""
                                    minuteInputText = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("OK", fontSize = 20.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showTimePicker = false },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(R.string.cancel), fontSize = 18.sp)
                    }
                }
            )
        }

        // Time validation error dialog
        if (showTimeError) {
            AlertDialog(
                onDismissRequest = { showTimeError = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text("Invalid Time", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                },
                text = {
                    Text(timeErrorMessage, fontSize = 18.sp)
                },
                confirmButton = {
                    Button(
                        onClick = { showTimeError = false },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("OK", fontSize = 20.sp)
                    }
                }
            )
        }

        // Day picker dialog
        showDayPicker?.let { timeIndex ->
            val currentTime = reminderTimes[timeIndex]
            var selectedDays by remember { mutableStateOf(currentTime.daysOfWeek) }

            AlertDialog(
                onDismissRequest = { showDayPicker = null },
                title = {
                    Text(
                        text = stringResource(R.string.repeat_on),
                        fontSize = 24.sp
                    )
                },
                text = {
                    Column {
                        listOf(
                            1 to R.string.monday_full,
                            2 to R.string.tuesday_full,
                            3 to R.string.wednesday_full,
                            4 to R.string.thursday_full,
                            5 to R.string.friday_full,
                            6 to R.string.saturday_full,
                            7 to R.string.sunday_full
                        ).forEach { (day, stringRes) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedDays.contains(day),
                                    onCheckedChange = { checked ->
                                        selectedDays = if (checked) {
                                            selectedDays + day
                                        } else {
                                            selectedDays - day
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(stringRes),
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            reminderTimes = sortReminderTimes(
                                reminderTimes.mapIndexed { index, time ->
                                    if (index == timeIndex) time.copy(daysOfWeek = selectedDays) else time
                                }
                            )
                            showDayPicker = null
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("OK", fontSize = 20.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showDayPicker = null },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(R.string.cancel), fontSize = 18.sp)
                    }
                }
            )
        }

    }
}

 

@Composable
fun TimePresetRow(
    label: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Column {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color(0xFF4A90E2)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Hour
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { onHourChange((hour + 1) % 24) }) {
                    Icon(Icons.Default.KeyboardArrowUp, null)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = String.format("%02d", hour),
                    onValueChange = { newValue ->
                        // Remove non-digit characters
                        val digitsOnly = newValue.filter { it.isDigit() }

                        when {
                            digitsOnly.isEmpty() -> onHourChange(0)
                            digitsOnly.length == 1 -> {
                                // Single digit - just set it
                                onHourChange(digitsOnly.toInt())
                            }
                            digitsOnly.length == 2 -> {
                                // Two digits - validate range
                                val value = digitsOnly.toInt()
                                if (value in 0..23) {
                                    onHourChange(value)
                                }
                            }
                            else -> {
                                // More than 2 digits - take last 2
                                val value = digitsOnly.takeLast(2).toInt()
                                if (value in 0..23) {
                                    onHourChange(value)
                                }
                            }
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 32.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier
                        .width(60.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(4.dp)
                )
                IconButton(onClick = { onHourChange(if (hour == 0) 23 else hour - 1) }) {
                    Icon(Icons.Default.KeyboardArrowDown, null)
                }
            }

            Text(":", fontSize = 32.sp, modifier = Modifier.padding(horizontal = 12.dp))

            // Minute
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { onMinuteChange((minute + 15) % 60) }) {
                    Icon(Icons.Default.KeyboardArrowUp, null)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = String.format("%02d", minute),
                    onValueChange = { newValue ->
                        // Remove non-digit characters
                        val digitsOnly = newValue.filter { it.isDigit() }

                        when {
                            digitsOnly.isEmpty() -> onMinuteChange(0)
                            digitsOnly.length == 1 -> {
                                // Single digit - just set it
                                onMinuteChange(digitsOnly.toInt())
                            }
                            digitsOnly.length == 2 -> {
                                // Two digits - validate range
                                val value = digitsOnly.toInt()
                                if (value in 0..59) {
                                    onMinuteChange(value)
                                }
                            }
                            else -> {
                                // More than 2 digits - take last 2
                                val value = digitsOnly.takeLast(2).toInt()
                                if (value in 0..59) {
                                    onMinuteChange(value)
                                }
                            }
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 32.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier
                        .width(60.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(4.dp)
                )
                IconButton(onClick = { onMinuteChange(if (minute == 0) 45 else minute - 15) }) {
                    Icon(Icons.Default.KeyboardArrowDown, null)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeCard(
    reminderTime: ReminderTime,
    currentLanguage: String,
    isNewlyAdded: Boolean = false,
    onDelete: () -> Unit,
    onEditDays: () -> Unit
) {
    val dayAbbreviations = mapOf(
        1 to R.string.monday,
        2 to R.string.tuesday,
        3 to R.string.wednesday,
        4 to R.string.thursday,
        5 to R.string.friday,
        6 to R.string.saturday,
        7 to R.string.sunday
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNewlyAdded) {
                androidx.compose.ui.graphics.Color(0xFFD4EDDA) // Light green
            } else {
                androidx.compose.ui.graphics.Color(0xFFF5F5F5)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time display
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminderTime.toDisplayString(),
                    fontSize = 28.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Days display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    reminderTime.daysOfWeek.sorted().forEach { day ->
                        Text(
                            text = stringResource(dayAbbreviations[day]!!),
                            fontSize = 16.sp,
                            color = androidx.compose.ui.graphics.Color.Gray
                        )
                    }
                }
            }

            // Edit days button
            IconButton(onClick = onEditDays) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit days",
                    tint = androidx.compose.ui.graphics.Color(0xFF4A90E2),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_time),
                    tint = androidx.compose.ui.graphics.Color(0xFFE53935),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetTimeButton(
    label: String,
    time: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    gradient: Brush? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = gradient ?: Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color(0xFF4A90E2),
                            androidx.compose.ui.graphics.Color(0xFF4A90E2)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = androidx.compose.ui.graphics.Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = time,
                    fontSize = 14.sp,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Helper function to parse reminder times from JSON
fun parseReminderTimesFromJson(jsonString: String): List<ReminderTime> {
    try {
        val times = mutableListOf<ReminderTime>()
        val timePattern = """"hour":(\d+),"minute":(\d+),"days":\[([\d,]*)\]""".toRegex()

        timePattern.findAll(jsonString).forEach { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val daysString = match.groupValues[3]
            val days = if (daysString.isNotEmpty()) {
                daysString.split(",").map { it.toInt() }.toSet()
            } else {
                setOf(1, 2, 3, 4, 5, 6, 7) // Default to all days
            }

            times.add(ReminderTime(hour, minute, days))
        }

        return times
    } catch (e: Exception) {
        return emptyList()
    }
}
