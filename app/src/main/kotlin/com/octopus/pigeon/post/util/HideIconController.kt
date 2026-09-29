package com.octopus.pigeon.post.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.octopus.pigeon.post.ui.activity.MainActivity

/**
 * Hiding the app icon.
 *
 * Two mechanisms, because they trade off differently:
 *
 *  - [isLauncherEntryEnabled] is the reversible one. It disables the launcher
 *    component, which removes the icon entirely, but the system offers no way
 *    to bring the component back, so the only recovery is the adb command in
 *    [restoreAdbCommand].
 *  - Letting the launcher hide the app instead is fully reversible from the
 *    home screen and is what [launcherHideInstructions] describes. It also
 *    survives a launcher change, so it is the recommended route.
 */
object HideIconController {
    private fun launcherComponent(context: Context): ComponentName = ComponentName(context, MainActivity::class.java)

    fun isLauncherEntryEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(launcherComponent(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /**
     * Disables the launcher entry. [DONT_KILL_APP] keeps the running service
     * alive, so forwarding is not interrupted by hiding the icon.
     *
     * This cannot be undone from inside the app. The caller is responsible for
     * showing [restoreAdbCommand] and warning the user first.
     */
    fun disableLauncherEntry(context: Context) {
        context.packageManager.setComponentEnabledSetting(
            launcherComponent(context),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /**
     * Re-enables the launcher entry, but only for a process that can still
     * reach this code, which after a disable means an adb invocation. Provided
     * so the app can self-heal if the icon ever comes back on its own.
     */
    fun enableLauncherEntry(context: Context) {
        context.packageManager.setComponentEnabledSetting(
            launcherComponent(context),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /**
     * The exact command that brings the icon back.
     *
     * Built from the live values rather than hardcoded, because the silent
     * flavour installs under a different applicationId than the source tree
     * suggests, and a hardcoded package name would be wrong there.
     */
    fun restoreAdbCommand(context: Context): String {
        val component = ComponentName(context, MainActivity::class.java)
        return "adb shell pm enable ${component.flattenToString()}"
    }
}

/** Guidance for the reversible path, in the user's own language. */
fun launcherHideInstructions(context: Context): List<String> =
    listOf(
        context.getString(com.octopus.pigeon.post.R.string.hide_mode_a_step1),
        context.getString(com.octopus.pigeon.post.R.string.hide_mode_a_step2),
        context.getString(com.octopus.pigeon.post.R.string.hide_mode_a_step3),
    )
