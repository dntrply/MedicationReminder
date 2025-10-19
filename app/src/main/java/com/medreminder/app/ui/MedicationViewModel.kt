package com.medreminder.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.app.data.Medication
import com.medreminder.app.data.MedicationDatabase
import com.medreminder.app.notifications.NotificationScheduler
import com.medreminder.app.notifications.PendingMedicationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MedicationViewModel(application: Application) : AndroidViewModel(application) {
    private val medicationDao = MedicationDatabase.getDatabase(application).medicationDao()
    private val context = application.applicationContext

    val medications: Flow<List<Medication>> = medicationDao.getAllMedications()

    fun addMedication(medication: Medication) {
        viewModelScope.launch {
            medicationDao.insertMedication(medication)
        }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            // Cancel notifications for this medication
            NotificationScheduler.cancelMedicationNotifications(context, medication)
            // Delete from database
            medicationDao.deleteMedication(medication)
            // Remove from pending medication tracker
            PendingMedicationTracker.removePendingMedication(context, medication.id)
        }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch {
            medicationDao.updateMedication(medication)
        }
    }
}
