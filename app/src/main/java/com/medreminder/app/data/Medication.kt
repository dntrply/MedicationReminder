package com.medreminder.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val photoUri: String? = null,
    val dosage: String? = null,
    val notes: String? = null,
    val reminderTimesJson: String? = null, // JSON string of List<ReminderTime>
    val createdAt: Long = System.currentTimeMillis()
)
