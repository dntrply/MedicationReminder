package com.medreminder.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.app.data.Medication
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.data.SettingsStore
import com.medreminder.app.notifications.NotificationScheduler
import com.medreminder.app.notifications.PendingMedicationTracker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MedicationViewModel(application: Application) : AndroidViewModel(application) {
    private val medicationDao = MedicationDatabase.getDatabase(application).medicationDao()
    private val context = application.applicationContext

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
            medicationDao.insertMedication(medication)
        }
    }

    suspend fun insertMedicationReturnId(medication: Medication): Long {
        return medicationDao.insertMedication(medication)
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
