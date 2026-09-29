package com.octopus.pigeon.post.data.model

import java.time.LocalTime

/**
 * Configuration for active time periods when SMS monitoring should be enabled
 * When disabled, SMS monitoring runs 24/7
 */
data class ActiveTimeConfig(
    val isEnabled: Boolean = false,
    val startHour: Int = 8,
    val startMinute: Int = 0,
    val endHour: Int = 9,
    val endMinute: Int = 0,
) {
    /**
     * Check if current time is within the active time period
     * @return true if current time is within active period or if active time is disabled
     */
    fun isInActiveTime(): Boolean {
        // If active time restriction is not enabled, default to monitoring all day
        if (!isEnabled) return true

        val currentTime = LocalTime.now()
        val startTime = LocalTime.of(startHour, startMinute)
        val endTime = LocalTime.of(endHour, endMinute)

        return if (startTime <= endTime) {
            // Time period within the same day
            currentTime in startTime..endTime
        } else {
            // Cross-day time period (e.g., 22:00 - 06:00)
            currentTime >= startTime || currentTime <= endTime
        }
    }
}
