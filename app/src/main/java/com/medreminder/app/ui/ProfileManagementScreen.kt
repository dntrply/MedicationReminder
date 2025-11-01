package com.medreminder.app.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.medreminder.app.R
import com.medreminder.app.data.Profile
import com.medreminder.app.ui.components.PhotoPickerDialog
import com.medreminder.app.ui.components.rememberPhotoPickerState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagementScreen(
    onBack: () -> Unit
) {
    val profileViewModel: ProfileViewModel = viewModel()
    val profiles by profileViewModel.profiles.collectAsState(initial = emptyList())
    val activeProfile by profileViewModel.activeProfile.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var profileToDelete by remember { mutableStateOf<Profile?>(null) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Profiles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add Profile")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(profiles) { profile ->
                ProfileCard(
                    profile = profile,
                    isActive = profile.id == activeProfile?.id,
                    canDelete = profiles.size > 1,
                    onSelect = { profileViewModel.switchProfile(profile.id) },
                    onEdit = { editingProfile = profile },
                    onDelete = { profileToDelete = profile }
                )
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog || editingProfile != null) {
        ProfileDialog(
            profile = editingProfile,
            onDismiss = {
                showAddDialog = false
                editingProfile = null
            },
            onSave = { name, photoUri, messageTemplate ->
                if (editingProfile != null) {
                    profileViewModel.updateProfile(
                        editingProfile!!.copy(
                            name = name,
                            photoUri = photoUri,
                            notificationMessageTemplate = messageTemplate
                        )
                    )
                } else {
                    profileViewModel.addProfile(
                        name = name,
                        photoUri = photoUri,
                        notificationMessageTemplate = messageTemplate
                    )
                }
                showAddDialog = false
                editingProfile = null
            }
        )
    }

    // Delete Confirmation Dialog
    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Profile?") },
            text = { Text("This will permanently delete all medications and history for ${profile.name}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            profileViewModel.deleteProfile(profile.id)
                            profileToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCard(
    profile: Profile,
    isActive: Boolean,
    canDelete: Boolean = true,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Profile photo or initial
                if (profile.photoUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(profile.photoUri)),
                        contentDescription = "Profile photo",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.name.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isActive) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit")
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileDialog(
    profile: Profile?,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var photoUri by remember { mutableStateOf(profile?.photoUri) }
    var messageTemplate by remember { mutableStateOf(profile?.notificationMessageTemplate ?: "") }

    // Photo picker state
    val photoPickerState = rememberPhotoPickerState(
        initialUri = photoUri?.let { Uri.parse(it) }
    ) { uri ->
        photoUri = uri?.toString()
    }

    // Photo picker dialog
    PhotoPickerDialog(
        state = photoPickerState,
        title = "Add Profile Photo"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Add Profile" else "Edit Profile") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Profile photo section
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { photoPickerState.showDialog(true) }
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(Uri.parse(photoUri)),
                            contentDescription = "Profile photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Add photo",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap to add photo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (photoUri != null) {
                    TextButton(onClick = { photoUri = null }) {
                        Text("Remove Photo")
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = messageTemplate,
                    onValueChange = { messageTemplate = it },
                    label = { Text("Notification Message (optional)") },
                    placeholder = { Text("e.g., Time for {profileName} to take {medicationName}") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Text(
                    text = "Use {profileName} and {medicationName} as placeholders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            name.trim(),
                            photoUri,
                            messageTemplate.takeIf { it.isNotBlank() }
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Profile indicator displayed in the app's top bar showing the active profile.
 */
@Composable
fun ProfileIndicator(
    profile: Profile?,
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
                            fontWeight = FontWeight.Bold
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
                        fontWeight = FontWeight.SemiBold,
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
