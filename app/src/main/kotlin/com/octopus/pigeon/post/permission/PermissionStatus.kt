package com.octopus.pigeon.post.permission

/**
 * Application permission status data class
 * Comprehensively reflects the status of critical and special permissions
 */
data class PermissionStatus(
    /**
     * Whether basic runtime permissions are granted (READ_SMS, RECEIVE_SMS, etc.)
     */
    val hasBasicPermissions: Boolean,
    /**
     * Whether SMS AppOps permission is granted (some systems/ROMs require additional authorization)
     */
    val hasSmsAppOps: Boolean,
    /**
     * Whether the app is set as the default SMS application
     * (some permissions are only available to default SMS apps)
     */
    val isDefaultSmsApp: Boolean,
    /**
     * Whether the app is ignoring battery optimization (Battery Optimization)
     */
    val isIgnoringBatteryOptimizations: Boolean,
    /**
     * Whether autostart permission is granted (restricted by some Chinese ROMs, manual authorization required)
     */
    val hasAutostartPermission: Boolean,
    /**
     * List of missing basic permissions
     */
    val missingPermissions: List<String>,
    /**
     * Whether additional special access permissions are needed (e.g., Android 10+ restrictions)
     */
    val needsSpecialAccess: Boolean,
) {
    /**
     * Whether all critical permissions are satisfied (basic permissions, AppOps, battery optimization)
     * Note: Autostart permission is recommended but not required, as it cannot be reliably detected via API
     */
    val isFullyGranted: Boolean
        get() =
            hasBasicPermissions &&
                hasSmsAppOps &&
                isIgnoringBatteryOptimizations

    /**
     * Whether there are any permission issues (insufficient basic permissions or special permissions needed)
     */
    val hasAnyIssue: Boolean
        get() = !isFullyGranted || needsSpecialAccess
}
