package com.medreminder.app.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.app.data.Medication
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.data.SettingsStore
import com.medreminder.app.notifications.NotificationScheduler
import com.medreminder.app.notifications.PendingMedicationTracker
import com.medreminder.app.utils.TranscriptionScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MedicationViewModel(application: Application) : AndroidViewModel(application) {
    private val medicationDao = MedicationDatabase.getDatabase(application).medicationDao()
    private val context = application.applicationContext

    // Transcription consent dialog state
    var showTranscriptionConsentDialog by mutableStateOf(false)
        private set
    private var pendingTranscription: Pair<Long, String>? = null // medicationId, audioPath

    // Get active profile ID from settings
    private val activeProfileId: Flow<Long?> = SettingsStore.activeProfileIdFlow(context)

    // Filter medications by active profile
    val medications: Flow<List<Medication>> = activeProfileId.flatMapLatest { profileId ->
        if (profileId != null) {
            medicationDao.getMedicationsByProfile(profileId)
        } else {
            flowOf(emptyList())
        }
    }

    fun addMedication(medication: Medication) {
        viewModelScope.launch {
            val medicationId = medicationDao.insertMedication(medication)

            // Schedule background transcription if audio note exists (with consent check)
            if (medication.audioNotePath != null) {
                scheduleTranscriptionWithConsent(medicationId, medication.audioNotePath)
            }
        }
    }

    suspend fun insertMedicationReturnId(medication: Medication): Long {
        val medicationId = medicationDao.insertMedication(medication)

        // Schedule background transcription if audio note exists (with consent check)
        if (medication.audioNotePath != null) {
            scheduleTranscriptionWithConsent(medicationId, medication.audioNotePath)
        }

        return medicationId
    }

    /**
     * Schedule transcription with consent check.
     * Only runs if transcription feature is enabled in settings.
     * If consent hasn't been asked, shows dialog. Otherwise schedules directly.
     */
    private suspend fun scheduleTranscriptionWithConsent(medicationId: Long, audioPath: String) {
        // First check if transcription feature is enabled
        val isEnabled = SettingsStore.isTranscriptionEnabled(context)
        if (!isEnabled) {
            // Transcription is disabled - skip silently
            return
        }

        // Check if consent was already asked
        val wasAsked = SettingsStore.wasTranscriptionConsentAsked(context)

        if (!wasAsked) {
            // First time - show consent dialog
            pendingTranscription = Pair(medicationId, audioPath)
            showTranscriptionConsentDialog = true
        } else {
            // Consent already handled - check if granted
            val hasConsent = SettingsStore.hasTranscriptionConsent(context)
            if (hasConsent) {
                TranscriptionScheduler.scheduleTranscription(context, medicationId, audioPath)
            }
            // If not granted, silently skip transcription
        }
    }

    /**
     * Handle user accepting transcription consent.
     */
    fun onTranscriptionConsentAccepted() {
        viewModelScope.launch {
            // Save consent
            SettingsStore.setTranscriptionConsent(context, granted = true)

            // Schedule pending transcription
            pendingTranscription?.let { (medicationId, audioPath) ->
                TranscriptionScheduler.scheduleTranscription(context, medicationId, audioPath)
            }

            // Hide dialog and clear pending
            showTranscriptionConsentDialog = false
            pendingTranscription = null
        }
    }

    /**
     * Handle user declining transcription consent.
     */
    fun onTranscriptionConsentDeclined() {
        viewModelScope.launch {
            // Save that user declined
            SettingsStore.setTranscriptionConsent(context, granted = false)

            // Hide dialog and clear pending (don't schedule transcription)
            showTranscriptionConsentDialog = false
            pendingTranscription = null
        }
    }

    /**
     * Dismiss consent dialog without saving preference.
     */
    fun dismissTranscriptionConsentDialog() {
        showTranscriptionConsentDialog = false
        pendingTranscription = null
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            // Cancel notifications for this medication
            NotificationScheduler.cancelMedicationNotifications(context, medication)

            // Delete audio file if it exists
            if (medication.audioNotePath != null) {
                try {
                    val audioFile = java.io.File(medication.audioNotePath)
                    if (audioFile.exists()) {
                        audioFile.delete()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MedicationViewModel", "Error deleting audio file", e)
                }
            }

            // Delete from database
            medicationDao.deleteMedication(medication)
            // Remove from pending medication tracker
            PendingMedicationTracker.removePendingMedication(context, medication.id)
        }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch {
            // Get the old medication to check if audio file changed
            val oldMedication = medicationDao.getMedicationById(medication.id)

            // If audio path changed, delete the old audio file
            if (oldMedication != null &&
                oldMedication.audioNotePath != null &&
                oldMedication.audioNotePath != medication.audioNotePath) {
                try {
                    val oldAudioFile = java.io.File(oldMedication.audioNotePath)
                    if (oldAudioFile.exists()) {
                        oldAudioFile.delete()
                        android.util.Log.d("MedicationViewModel", "Deleted old audio file: ${oldMedication.audioNotePath}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MedicationViewModel", "Error deleting old audio file", e)
                }

                // Schedule transcription for new audio file (with consent check)
                if (medication.audioNotePath != null) {
                    scheduleTranscriptionWithConsent(medication.id, medication.audioNotePath)
                }
            } else if (medication.audioNotePath != null && oldMedication?.audioNotePath == null) {
                // Audio was added to existing medication (with consent check)
                scheduleTranscriptionWithConsent(medication.id, medication.audioNotePath)
            }

            medicationDao.updateMedication(medication)
        }
    }

    fun deleteMedicationById(id: Long) {
        viewModelScope.launch {
            medicationDao.deleteMedicationById(id)
        }
    }

    suspend fun getActiveProfileId(): Long {
        return SettingsStore.activeProfileIdFlow(context).first() ?: 1L
    }
}
