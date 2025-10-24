package com.medreminder.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing when a medication was taken, skipped, or missed
 */
@Entity(tableName = "medication_history")
data class MedicationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val medicationId: Long,  // Foreign key to Medication
    val medicationName: String,  // Denormalized for easier queries

    val scheduledTime: Long,  // When it was supposed to be taken (timestamp)
    val takenTime: Long,      // When it was actually taken (timestamp)

    val wasOnTime: Boolean,   // Was it taken within acceptable window?
    val action: String = "TAKEN",  // Action: "TAKEN", "SKIPPED", "MISSED"
    val notes: String? = null // Optional notes about this dose
)
