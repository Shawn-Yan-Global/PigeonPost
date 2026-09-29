package com.octopus.pigeon.post.permission

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.octopus.logging.PigeonLogger

object PermissionManager {
    private val TAG = PermissionManager::class.java.simpleName

    private val REQUIRED_PERMISSIONS =
        arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.POST_NOTIFICATIONS,
        )

    /**
     * Check if all critical permissions are granted (basic permissions, AppOps, battery optimization, autostart, etc.)
     */
    fun hasAllPermissions(context: Context): Boolean = getPermissionStatus(context).isFullyGranted

    /**
     * Get list of missing basic permissions
     */
    fun getMissingPermissions(context: Context): List<String> = getPermissionStatus(context).missingPermissions

    /**
     * Get complete permission status
     */
    fun getPermissionStatus(context: Context): PermissionStatus {
        val missingPermissions =
            REQUIRED_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }

        val hasBasicPermissions = missingPermissions.isEmpty()
        val hasSmsAppOps = checkSmsAppOpsPermission(context)
        val isDefaultSmsApp = checkIsDefaultSmsApp(context)
        val isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
        val hasAutostartPermission = checkAutostartPermission(context)

        val needsSpecialAccess = !isDefaultSmsApp && hasBasicPermissions && !hasSmsAppOps

        return PermissionStatus(
            hasBasicPermissions = hasBasicPermissions,
            hasSmsAppOps = hasSmsAppOps,
            isDefaultSmsApp = isDefaultSmsApp,
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
            hasAutostartPermission = hasAutostartPermission,
            missingPermissions = missingPermissions,
            needsSpecialAccess = needsSpecialAccess,
        )
    }

    /**
     * Check SMS permission at AppOps level (some ROMs require manual activation)
     */
    private fun checkSmsAppOpsPermission(context: Context): Boolean =
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode =
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_READ_SMS,
                    Process.myUid(),
                    context.packageName,
                )
            PigeonLogger.debug(TAG, "SMS AppOps permission check result: $mode")
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Exception occurred while checking AppOps permission", e)
            true // Default return true
        }

    /**
     * Check if app is set as default SMS application
     */
    private fun checkIsDefaultSmsApp(context: Context): Boolean =
        try {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            val isDefault = defaultSmsPackage == context.packageName
            PigeonLogger.debug(
                TAG,
                "Default SMS app: current=$defaultSmsPackage, this app=${context.packageName}",
            )
            isDefault
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Exception occurred while checking default SMS app", e)
            false
        }

    /**
     * Check if app is ignoring battery optimization
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            PigeonLogger.error(
                TAG,
                "Exception occurred while checking battery optimization status",
                e,
            )
            false
        }

    /**
     * Check if autostart permission is granted
     * Note: This is a heuristic check. Different ROMs have different autostart mechanisms:
     * - MIUI: Security app > Autostart management
     * - EMUI/HarmonyOS: Phone Manager > Protected apps
     * - ColorOS: Settings > Battery > App autostart
     * - FunTouch OS: Settings > More settings > Permission management > Autostart
     * - Samsung: Settings > Apps > Auto start
     *
     * Since there's no standard API to detect autostart permissions:
     * 1. For known manufacturers with strict restrictions, check if user has confirmed setup
     * 2. For other manufacturers, assume it's OK
     *
     * This is a RECOMMENDED permission, not REQUIRED. Service can still work without it,
     * but with reduced reliability.
     */
    private fun checkAutostartPermission(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()

        // Check if this manufacturer requires autostart permission
        val requiresAutostart =
            when {
                manufacturer.contains("xiaomi") -> true // MIUI
                manufacturer.contains("huawei") -> true // EMUI/HarmonyOS
                manufacturer.contains("honor") -> true // HarmonyOS
                manufacturer.contains("oppo") -> true // ColorOS
                manufacturer.contains("vivo") -> true // FunTouch OS
                manufacturer.contains("oneplus") -> true // OxygenOS (based on ColorOS)
                manufacturer.contains("realme") -> true // Realme UI (based on ColorOS)
                manufacturer.contains("samsung") -> true // One UI
                manufacturer.contains("meizu") -> true // Flyme
                manufacturer.contains("asus") -> true // ZenUI
                manufacturer.contains("letv") -> true // EUI
                manufacturer.contains("lenovo") -> true // ZUI
                manufacturer.contains("zte") -> true // MiFavor
                else -> false
            }

        if (!requiresAutostart) {
            // This manufacturer doesn't have strict autostart restrictions
            return true
        }

        // For manufacturers with autostart restrictions, check if user confirmed
        val prefs = context.getSharedPreferences("permission_status", Context.MODE_PRIVATE)
        val userConfirmed = prefs.getBoolean("autostart_confirmed", false)

        return userConfirmed
    }

    /**
     * Check if this device manufacturer requires autostart permission
     */
    fun requiresAutostartPermission(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") ||
            manufacturer.contains("huawei") ||
            manufacturer.contains("honor") ||
            manufacturer.contains("oppo") ||
            manufacturer.contains("vivo") ||
            manufacturer.contains("oneplus") ||
            manufacturer.contains("realme") ||
            manufacturer.contains("samsung") ||
            manufacturer.contains("meizu") ||
            manufacturer.contains("asus") ||
            manufacturer.contains("letv") ||
            manufacturer.contains("lenovo") ||
            manufacturer.contains("zte")
    }

    /**
     * Mark that user has confirmed autostart permission is enabled
     */
    fun confirmAutostartPermission(context: Context) {
        val prefs = context.getSharedPreferences("permission_status", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("autostart_confirmed", true).apply()
        PigeonLogger.info(TAG, "User confirmed autostart permission is enabled")
    }

    /**
     * Reset autostart permission confirmation (for testing or troubleshooting)
     */
    fun resetAutostartConfirmation(context: Context) {
        val prefs = context.getSharedPreferences("permission_status", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("autostart_confirmed", false).apply()
        PigeonLogger.info(TAG, "Autostart permission confirmation reset")
    }

    // -------------------------------
    // Settings navigation methods
    // -------------------------------

    /** Open app details settings page */
    fun openAppSettings(context: Context) {
        try {
            val intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Failed to navigate to app settings", e)
        }
    }

    /** Open default SMS app settings page */
    fun openDefaultSmsSettings(context: Context) {
        try {
            val intent =
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Failed to navigate to default SMS settings", e)
            openAppSettings(context)
        }
    }

    /** Open battery optimization settings */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent =
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Failed to navigate to battery optimization settings", e)
        }
    }

    /** Navigate to specific ignore battery optimization request page (requires manual app selection) */
    fun requestIgnoreBatteryOptimization(context: Context) {
        try {
            val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            PigeonLogger.error(
                TAG,
                "Failed to navigate to ignore battery optimization request page",
                e,
            )
        }
    }

    /**
     * Open autostart permission settings
     * Attempts to open manufacturer-specific autostart settings page, falls back to app details
     */
    fun openAutostartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        var success = false

        try {
            val intent =
                when {
                    // Xiaomi MIUI
                    manufacturer.contains("xiaomi") -> {
                        Intent().apply {
                            setClassName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity",
                            )
                        }
                    }
                    // Huawei EMUI / Honor
                    manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                        Intent().apply {
                            setClassName(
                                "com.huawei.systemmanager",
                                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                            )
                        }
                    }
                    // OPPO ColorOS
                    manufacturer.contains("oppo") -> {
                        Intent().apply {
                            setClassName(
                                "com.coloros.safecenter",
                                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                            )
                        }
                    }
                    // Vivo FunTouch OS
                    manufacturer.contains("vivo") -> {
                        Intent().apply {
                            setClassName(
                                "com.vivo.permissionmanager",
                                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                            )
                        }
                    }
                    // OnePlus OxygenOS
                    manufacturer.contains("oneplus") -> {
                        Intent().apply {
                            setClassName(
                                "com.oneplus.security",
                                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                            )
                        }
                    }
                    // Samsung One UI
                    manufacturer.contains("samsung") -> {
                        Intent().apply {
                            setClassName(
                                "com.samsung.android.lool",
                                "com.samsung.android.sm.ui.battery.BatteryActivity",
                            )
                        }
                    }
                    // Asus ZenUI
                    manufacturer.contains("asus") -> {
                        Intent().apply {
                            setClassName(
                                "com.asus.mobilemanager",
                                "com.asus.mobilemanager.autostart.AutoStartActivity",
                            )
                        }
                    }
                    // Letv EUI
                    manufacturer.contains("letv") -> {
                        Intent().apply {
                            setClassName(
                                "com.letv.android.letvsafe",
                                "com.letv.android.letvsafe.AutobootManageActivity",
                            )
                        }
                    }
                    // Meizu Flyme
                    manufacturer.contains("meizu") -> {
                        Intent().apply {
                            setClassName(
                                "com.meizu.safe",
                                "com.meizu.safe.security.SHOW_APPSEC",
                            )
                            addCategory(Intent.CATEGORY_DEFAULT)
                            putExtra("packageName", context.packageName)
                        }
                    }
                    else -> null
                }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                success = true
                PigeonLogger.info(TAG, "Opened manufacturer-specific autostart settings for $manufacturer")
            }
        } catch (e: Exception) {
            PigeonLogger.warn(TAG, "Failed to open manufacturer-specific autostart settings", e)
        }

        // Fallback: Open app details settings
        if (!success) {
            try {
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(intent)
                PigeonLogger.info(TAG, "Opened app details settings as fallback")
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "Failed to navigate to any settings page", e)
            }
        }
    }

    // -------------------------------
    // Permission issue descriptions
    // -------------------------------

    /**
     * Get localized permission issue description
     * @param context Context to get string resources
     * @param status Permission status
     * @return Localized description string
     */
    fun getPermissionIssueDescription(
        context: Context,
        status: PermissionStatus,
    ): String =
        when {
            !status.hasBasicPermissions ->
                context.getString(
                    com.octopus.pigeon.post.R.string.permission_issue_missing_basic,
                    status.missingPermissions.joinToString(", "),
                )

            !status.hasSmsAppOps -> context.getString(com.octopus.pigeon.post.R.string.permission_issue_sms_appops)

            !status.isIgnoringBatteryOptimizations ->
                context.getString(
                    com.octopus.pigeon.post.R.string.permission_issue_battery,
                )

            !status.hasAutostartPermission ->
                context.getString(
                    com.octopus.pigeon.post.R.string.permission_issue_autostart,
                )

            status.needsSpecialAccess ->
                context.getString(
                    com.octopus.pigeon.post.R.string.permission_issue_special_access,
                )

            else -> context.getString(com.octopus.pigeon.post.R.string.permission_issue_normal)
        }

    /**
     * Get localized permission fix suggestion
     * @param context Context to get string resources
     * @param status Permission status
     * @return Localized suggestion string
     */
    fun getPermissionFixSuggestion(
        context: Context,
        status: PermissionStatus,
    ): String =
        when {
            !status.hasBasicPermissions -> context.getString(com.octopus.pigeon.post.R.string.permission_fix_basic)

            !status.hasSmsAppOps -> context.getString(com.octopus.pigeon.post.R.string.permission_fix_sms_appops)

            !status.isIgnoringBatteryOptimizations ->
                context.getString(
                    com.octopus.pigeon.post.R.string.permission_fix_battery,
                )

            !status.hasAutostartPermission ->
                context.getString(
                    com.octopus.pigeon.post.R.string.permission_fix_autostart,
                )

            status.needsSpecialAccess && !status.isDefaultSmsApp ->
                context.getString(
                    com.octopus.pigeon.post.R.string.permission_fix_special_access,
                )

            else -> context.getString(com.octopus.pigeon.post.R.string.permission_fix_normal)
        }
}
