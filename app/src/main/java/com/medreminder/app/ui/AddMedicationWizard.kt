package com.medreminder.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import com.medreminder.app.ui.components.PhotoPickerDialog
import com.medreminder.app.ui.components.rememberPhotoPickerState
import com.medreminder.app.utils.AudioPlayer
import com.medreminder.app.utils.AudioRecorder

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

    // Handle back button press
    BackHandler(onBack = onBack)

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

    // Handle back button press
    BackHandler(onBack = onBack)

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
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
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
                fontSize = 16.sp,
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
                border = BorderStroke(
                    2.dp,
                    if (audioPath == null) androidx.compose.ui.graphics.Color(0xFF4A90E2)
                    else androidx.compose.ui.graphics.Color.LightGray
                ),
                contentPadding = PaddingValues(12.dp),
                enabled = audioPath == null
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.skip),
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (audioPath == null) androidx.compose.ui.graphics.Color(0xFF4A90E2)
                               else androidx.compose.ui.graphics.Color.LightGray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.no_audio),
                        fontSize = 20.sp,
                        color = androidx.compose.ui.graphics.Color.LightGray
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
