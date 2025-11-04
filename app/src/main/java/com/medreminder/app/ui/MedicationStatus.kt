package com.medreminder.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized medication status definitions for consistent iconography across the app.
 * Defines colors, icons, and labels for TAKEN, SKIPPED, and MISSED medication statuses.
 */
object MedicationStatus {

    /**
     * Status type enumeration
     */
    enum class Type {
        TAKEN_ON_TIME,
        TAKEN_LATE,
        SKIPPED,
        MISSED
    }

    /**
     * Data class holding all visual information for a status
     */
    data class StatusInfo(
        val icon: ImageVector,
        val color: Color,
        val label: String
    )

    // Color definitions
    val COLOR_TAKEN = Color(0xFF4CAF50)      // Green
    val COLOR_TAKEN_LATE = Color(0xFFFFA726) // Light Orange
    val COLOR_SKIPPED = Color(0xFFFF9800)    // Orange
    val COLOR_MISSED = Color(0xFFEF5350)     // Red

    /**
     * Get status info based on action string and on-time flag
     */
    fun getStatusInfo(action: String, wasOnTime: Boolean = true): StatusInfo {
        return when (action) {
            "TAKEN" -> {
                if (wasOnTime) {
                    StatusInfo(Icons.Default.CheckCircle, COLOR_TAKEN, "Taken")
                } else {
                    StatusInfo(Icons.Default.Schedule, COLOR_TAKEN_LATE, "Taken (Late)")
                }
            }
            "SKIPPED" -> StatusInfo(Icons.Default.Forward, COLOR_SKIPPED, "Skipped")
            "MISSED" -> StatusInfo(Icons.Default.Cancel, COLOR_MISSED, "Missed")
            else -> StatusInfo(Icons.Default.Help, Color.Gray, action)
        }
    }

    /**
     * Get status info by enum type
     */
    fun getStatusInfo(type: Type): StatusInfo {
        return when (type) {
            Type.TAKEN_ON_TIME -> StatusInfo(Icons.Default.CheckCircle, COLOR_TAKEN, "Taken")
            Type.TAKEN_LATE -> StatusInfo(Icons.Default.Schedule, COLOR_TAKEN_LATE, "Taken (Late)")
            Type.SKIPPED -> StatusInfo(Icons.Default.Forward, COLOR_SKIPPED, "Skipped")
            Type.MISSED -> StatusInfo(Icons.Default.Cancel, COLOR_MISSED, "Missed")
        }
    }

    /**
     * Composable: Render status icon with optional filled circle background
     *
     * @param action The medication action ("TAKEN", "SKIPPED", "MISSED")
     * @param wasOnTime Whether the medication was taken on time (for TAKEN status)
     * @param iconSize Size of the icon
     * @param showFilledCircle Whether to show filled circle background (default true)
     * @param circleSize Size of the circle background (if null, auto-calculated)
     */
    @Composable
    fun StatusIcon(
        action: String,
        wasOnTime: Boolean = true,
        iconSize: Dp = 20.dp,
        showFilledCircle: Boolean = true,
        circleSize: Dp? = null
    ) {
        val statusInfo = getStatusInfo(action, wasOnTime)

        if (showFilledCircle) {
            val actualCircleSize = circleSize ?: (iconSize * 1.6f)
            Box(
                modifier = Modifier
                    .size(actualCircleSize)
                    .background(statusInfo.color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusInfo.icon,
                    contentDescription = statusInfo.label,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        } else {
            Icon(
                imageVector = statusInfo.icon,
                contentDescription = statusInfo.label,
                tint = statusInfo.color,
                modifier = Modifier.size(iconSize)
            )
        }
    }

    /**
     * Composable: Render status icon by type
     */
    @Composable
    fun StatusIcon(
        type: Type,
        iconSize: Dp = 20.dp,
        showFilledCircle: Boolean = true,
        circleSize: Dp? = null
    ) {
        val statusInfo = getStatusInfo(type)

        if (showFilledCircle) {
            val actualCircleSize = circleSize ?: (iconSize * 1.6f)
            Box(
                modifier = Modifier
                    .size(actualCircleSize)
                    .background(statusInfo.color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusInfo.icon,
                    contentDescription = statusInfo.label,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        } else {
            Icon(
                imageVector = statusInfo.icon,
                contentDescription = statusInfo.label,
                tint = statusInfo.color,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
