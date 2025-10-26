package com.medreminder.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.app.data.*
import com.medreminder.app.notifications.NotificationScheduler
import com.medreminder.app.notifications.PendingMedicationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MedicationDatabase.getDatabase(application)
    private val profileDao = database.profileDao()
    private val medicationDao = database.medicationDao()
    private val historyDao = database.historyDao()

    val profiles: Flow<List<Profile>> = profileDao.getAllProfiles()

    private val _activeProfileId = MutableStateFlow<Long?>(null)
    val activeProfileId: StateFlow<Long?> = _activeProfileId.asStateFlow()

    val activeProfile: StateFlow<Profile?> = activeProfileId
        .filterNotNull()
        .flatMapLatest { profileId ->
            profileDao.getProfileByIdFlow(profileId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        // Initialize active profile from settings or default profile
        viewModelScope.launch {
            val savedProfileId = SettingsStore.activeProfileIdFlow(application).first()
            if (savedProfileId != null) {
                _activeProfileId.value = savedProfileId
            } else {
                // No saved profile, get or create default profile
                val defaultProfile = withContext(Dispatchers.IO) {
                    profileDao.getDefaultProfile() ?: run {
                        // Create default profile if none exists
                        val newProfileId = profileDao.insert(
                            Profile(
                                name = "Me",
                                isDefault = true
                            )
                        )
                        profileDao.getProfileById(newProfileId)
                    }
                }
                defaultProfile?.let {
                    _activeProfileId.value = it.id
                    SettingsStore.setActiveProfileId(application, it.id)
                }
            }
        }
    }

    fun switchProfile(profileId: Long) {
        viewModelScope.launch {
            _activeProfileId.value = profileId
            SettingsStore.setActiveProfileId(getApplication(), profileId)
        }
    }

    fun addProfile(
        name: String,
        photoUri: String? = null,
        notificationSoundUri: String? = null,
        notificationMessageTemplate: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = Profile(
                    name = name,
                    photoUri = photoUri,
                    notificationSoundUri = notificationSoundUri,
                    notificationMessageTemplate = notificationMessageTemplate,
                    isDefault = false
                )
                profileDao.insert(profile)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error adding profile", e)
            }
        }
    }

    fun updateProfile(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                profileDao.update(profile)
                // Reschedule notifications for this profile's medications with updated settings
                val medications = medicationDao.getMedicationsByProfileSync(profile.id)
                medications.forEach { medication ->
                    NotificationScheduler.cancelMedicationNotifications(getApplication(), medication)
                    NotificationScheduler.scheduleMedicationNotifications(
                        getApplication(),
                        medication
                    )
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating profile", e)
            }
        }
    }

    suspend fun deleteProfile(profileId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val profileCount = profileDao.getProfileCount()
            if (profileCount <= 1) {
                return@withContext Result.failure(
                    IllegalStateException("Cannot delete the last profile")
                )
            }

            val profile = profileDao.getProfileById(profileId)
                ?: return@withContext Result.failure(
                    IllegalStateException("Profile not found")
                )

            // Get medications count for confirmation
            val medications = medicationDao.getMedicationsByProfileSync(profileId)
            val medicationCount = medications.size

            // Cancel all notifications for this profile's medications
            medications.forEach { medication ->
                NotificationScheduler.cancelMedicationNotifications(getApplication(), medication)
                // Delete audio files
                medication.audioNotePath?.let { audioPath ->
                    try {
                        File(audioPath).delete()
                    } catch (e: Exception) {
                        Log.e("ProfileViewModel", "Error deleting audio file", e)
                    }
                }
                // Delete photo files if they're in app storage
                medication.photoUri?.let { photoUri ->
                    if (photoUri.contains(getApplication<Application>().filesDir.absolutePath)) {
                        try {
                            File(photoUri).delete()
                        } catch (e: Exception) {
                            Log.e("ProfileViewModel", "Error deleting photo file", e)
                        }
                    }
                }
            }

            // Remove pending medications for this profile
            PendingMedicationTracker.removePendingMedicationsForProfile(getApplication(), profileId)

            // Delete all history for this profile
            historyDao.deleteHistoryForProfile(profileId)

            // Delete all medications for this profile
            medicationDao.deleteMedicationsByProfile(profileId)

            // Delete the profile
            profileDao.deleteById(profileId)

            // If this was the active profile, switch to first available profile
            if (_activeProfileId.value == profileId) {
                val remainingProfiles = profileDao.getAllProfiles().first()
                if (remainingProfiles.isNotEmpty()) {
                    val newActiveProfile = remainingProfiles.first()
                    _activeProfileId.value = newActiveProfile.id
                    SettingsStore.setActiveProfileId(getApplication(), newActiveProfile.id)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error deleting profile", e)
            Result.failure(e)
        }
    }

    fun getProfileMedicationCount(profileId: Long): Flow<Int> {
        return medicationDao.getMedicationsByProfile(profileId)
            .map { it.size }
    }
}
