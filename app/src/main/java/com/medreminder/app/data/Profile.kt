package com.medreminder.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val photoUri: String? = null,
    val notificationSoundUri: String? = null,
    val notificationMessageTemplate: String? = null, // Template like "Time for {profileName} to take {medicationName}"
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_MESSAGE_TEMPLATE = "Time to take {medicationName}"

        /**
         * Renders the notification message by replacing placeholders
         * @param profileName The name of the profile
         * @param medicationName The name of the medication
         * @return The rendered message
         */
        fun renderNotificationMessage(
            template: String?,
            profileName: String,
            medicationName: String
        ): String {
            val effectiveTemplate = template ?: DEFAULT_MESSAGE_TEMPLATE
            return effectiveTemplate
                .replace("{profileName}", profileName)
                .replace("{medicationName}", medicationName)
        }
    }
}
