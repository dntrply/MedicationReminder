package com.medreminder.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/**
 * Photo selection state manager
 * Handles camera and gallery photo selection with common dialog
 */
@Composable
fun rememberPhotoPickerState(
    initialUri: Uri? = null,
    onPhotoSelected: (Uri?) -> Unit
): PhotoPickerState {
    val context = LocalContext.current
    var currentUri by remember { mutableStateOf(initialUri) }
    var showDialog by remember { mutableStateOf(false) }

    // Create temp file URI for camera
    fun createTempImageUri(): Uri {
        val timeStamp = System.currentTimeMillis()
        val imageFile = File(context.filesDir, "temp_photo_$timeStamp.jpg")
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
        if (success && currentUri != null) {
            onPhotoSelected(currentUri)
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val tempUri = createTempImageUri()
            currentUri = tempUri
            cameraLauncher.launch(tempUri)
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            currentUri = it
            onPhotoSelected(it)
        }
    }

    return PhotoPickerState(
        showDialog = { showDialog = it },
        isDialogShowing = showDialog,
        onTakePhoto = {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        },
        onChooseFromGallery = {
            galleryLauncher.launch("image/*")
        }
    )
}

/**
 * State holder for photo picker
 */
data class PhotoPickerState(
    val showDialog: (Boolean) -> Unit,
    val isDialogShowing: Boolean,
    val onTakePhoto: () -> Unit,
    val onChooseFromGallery: () -> Unit
)

/**
 * Photo picker dialog
 * Shows options to take a photo or choose from gallery
 */
@Composable
fun PhotoPickerDialog(
    state: PhotoPickerState,
    title: String = "Add Photo"
) {
    if (state.isDialogShowing) {
        AlertDialog(
            onDismissRequest = { state.showDialog(false) },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            state.showDialog(false)
                            state.onTakePhoto()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Camera, contentDescription = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Take Photo")
                        }
                    }
                    TextButton(
                        onClick = {
                            state.showDialog(false)
                            state.onChooseFromGallery()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Photo, contentDescription = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Choose from Gallery")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { state.showDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}
