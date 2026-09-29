package com.octopus.pigeon.post.ui.screen

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.jakewharton.processphoenix.ProcessPhoenix
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.permission.PermissionManager
import com.octopus.ui.spacing.AppSpacing

@Composable
fun PermissionStatusCard(
    hasPermissions: Boolean,
    serviceEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
) {
    val context = LocalContext.current
    val notificationEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val autostartConfirmedToast = stringResource(R.string.autostart_confirmed_toast)
    var isExpanded by remember { mutableStateOf(true) }

    // Get detailed permission status and refresh when hasPermissions changes
    val permissionStatus by remember {
        derivedStateOf {
            PermissionManager.getPermissionStatus(context)
        }
    }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.large, vertical = AppSpacing.small),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        permissionStatus.isFullyGranted && serviceEnabled && notificationEnabled -> MaterialTheme.colorScheme.primaryContainer

                        permissionStatus.isFullyGranted && serviceEnabled -> MaterialTheme.colorScheme.tertiaryContainer

                        else -> MaterialTheme.colorScheme.errorContainer
                    },
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(AppSpacing.large),
        ) {
            // Card title with expand/collapse functionality
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(bottom = AppSpacing.small),
            ) {
                Text(
                    text = stringResource(R.string.permission_status_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Main status and switch row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            when {
                                permissionStatus.isFullyGranted && serviceEnabled && notificationEnabled ->
                                    stringResource(
                                        R.string.status_service_running_normal,
                                    )

                                permissionStatus.isFullyGranted && serviceEnabled ->
                                    stringResource(
                                        R.string.status_service_running_no_notification,
                                    )
                                permissionStatus.isFullyGranted -> stringResource(R.string.status_service_stopped)
                                permissionStatus.hasBasicPermissions ->
                                    stringResource(
                                        R.string.status_need_system_permissions,
                                    )
                                else -> stringResource(R.string.status_need_basic_permissions)
                            },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    if (isExpanded) {
                        // Permission status details
                        if (!permissionStatus.hasBasicPermissions) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = AppSpacing.tiny),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(end = AppSpacing.extraSmall),
                                )
                                Text(
                                    text = stringResource(R.string.status_missing_basic_sms),
                                    fontSize = 12.sp,
                                )
                            }
                        } else if (!permissionStatus.hasSmsAppOps) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = AppSpacing.tiny),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(end = AppSpacing.extraSmall),
                                )
                                Text(
                                    text = stringResource(R.string.status_need_sms_access),
                                    fontSize = 12.sp,
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = AppSpacing.tiny),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = AppSpacing.extraSmall),
                                )
                                Text(
                                    text = stringResource(R.string.status_all_permissions_ready),
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        // Notification permission status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = AppSpacing.tiny),
                        ) {
                            Icon(
                                imageVector = if (notificationEnabled) Icons.Default.CheckCircle else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (notificationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(end = AppSpacing.extraSmall),
                            )
                            Text(
                                text =
                                    if (notificationEnabled) {
                                        stringResource(R.string.status_notification_enabled)
                                    } else {
                                        stringResource(
                                            R.string.status_notification_recommended,
                                        )
                                    },
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                // Pre-fetch string resources for toast messages
                val toastGrantBasic = stringResource(R.string.toast_grant_basic_permissions)
                val toastEnableSms = stringResource(R.string.toast_enable_sms_access)
                val toastDisableBattery =
                    stringResource(R.string.toast_disable_battery_optimization)
                val toastEnableAutostart = stringResource(R.string.toast_enable_autostart)
                val toastCompleteSetup = stringResource(R.string.toast_complete_permission_setup)

                Switch(
                    checked = serviceEnabled && permissionStatus.isFullyGranted,
                    enabled = permissionStatus.isFullyGranted || !serviceEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (permissionStatus.isFullyGranted) {
                                onStartService()
                            } else {
                                // Show toast instead of direct navigation
                                val message =
                                    when {
                                        !permissionStatus.hasBasicPermissions -> toastGrantBasic
                                        !permissionStatus.hasSmsAppOps -> toastEnableSms
                                        !permissionStatus.isIgnoringBatteryOptimizations -> toastDisableBattery
                                        !permissionStatus.hasAutostartPermission -> toastEnableAutostart
                                        else -> toastCompleteSetup
                                    }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            onStopService()
                        }
                    },
                )
            }

            // Action button area - only show when expanded
            if (isExpanded && (permissionStatus.hasAnyIssue || !notificationEnabled)) {
                Spacer(modifier = Modifier.height(AppSpacing.medium))

                // Permission issue description
                if (permissionStatus.hasAnyIssue) {
                    Text(
                        text =
                            PermissionManager.getPermissionIssueDescription(
                                context,
                                permissionStatus,
                            ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                    )

                    Text(
                        text =
                            PermissionManager.getPermissionFixSuggestion(
                                context,
                                permissionStatus,
                            ),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                    )
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = AppSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
                ) {
                    // 1. Basic permissions (SMS, etc.)
                    if (!permissionStatus.hasBasicPermissions) {
                        Button(
                            onClick = onRequestPermissions,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.button_request_permissions))
                        }
                    }

                    // 2. When AppOps permission is not granted, navigate to system settings
                    if (!permissionStatus.hasSmsAppOps) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                        ) {
                            Column(
                                modifier = Modifier.padding(AppSpacing.medium),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(AppSpacing.extraLarge),
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.small))
                                    Text(
                                        text = stringResource(R.string.sms_service_permission_help_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                                Spacer(modifier = Modifier.height(AppSpacing.small))
                                Text(
                                    text = stringResource(R.string.sms_service_permission_help_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(AppSpacing.small))

                        OutlinedButton(
                            onClick = { PermissionManager.openAppSettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.button_open_settings))
                        }
                    }

                    // 3. When not default SMS app, guide user to set it
                    if (permissionStatus.needsSpecialAccess && !permissionStatus.isDefaultSmsApp) {
                        OutlinedButton(
                            onClick = { PermissionManager.openDefaultSmsSettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.button_set_as_default))
                        }
                    }

                    // 4. When battery optimization is not ignored, guide to settings
                    if (!permissionStatus.isIgnoringBatteryOptimizations) {
                        OutlinedButton(
                            onClick = { PermissionManager.requestIgnoreBatteryOptimization(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.button_battery_optimization))
                        }
                    }

                    // 5. Autostart permission (requires manual authorization)
                    // Only show for manufacturers that require it
                    if (PermissionManager.requiresAutostartPermission(context) &&
                        !permissionStatus.hasAutostartPermission
                    ) {
                        Spacer(modifier = Modifier.height(AppSpacing.small))

                        // Warning message
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppSpacing.extraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(AppSpacing.extraLarge),
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.small))
                            Text(
                                text = stringResource(R.string.autostart_permission_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.small))

                        // Two buttons: Go to settings and Confirm
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                        ) {
                            OutlinedButton(
                                onClick = { PermissionManager.openAutostartSettings(context) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.button_autostart_permission),
                                    fontSize = 12.sp,
                                )
                            }

                            Button(
                                onClick = {
                                    PermissionManager.confirmAutostartPermission(context)
                                    Toast
                                        .makeText(
                                            context,
                                            autostartConfirmedToast,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    // Trigger UI refresh by changing hasPermissions state
                                    onRequestPermissions()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.button_confirm_autostart),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    // 6. Notification permission not enabled (additional check)
                    if (!notificationEnabled) {
                        OutlinedButton(
                            onClick = {
                                val intent =
                                    Intent().apply {
                                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.button_enable_notifications))
                        }
                    }

                    // 7. Restart app button - always show when there are permission issues
                    // This helps refresh permission states after user returns from settings
                    if (permissionStatus.hasAnyIssue || !notificationEnabled) {
                        Spacer(modifier = Modifier.height(AppSpacing.extraSmall))
                        Button(
                            onClick = {
                                (context as? android.app.Activity)?.let { activity ->
                                    ProcessPhoenix.triggerRebirth(activity)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                ),
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(AppSpacing.small))
                            Text(stringResource(R.string.permission_granted_restart_app))
                        }
                    }

                    // Manual restart hint when permissions changed
                    if (permissionStatus.hasAnyIssue.not()) {
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                        ) {
                            Column(
                                modifier = Modifier.padding(AppSpacing.medium),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(AppSpacing.extraLarge),
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.small))
                                    Text(
                                        text = stringResource(R.string.permission_refresh_hint_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                                Spacer(modifier = Modifier.height(AppSpacing.small))
                                Text(
                                    text = stringResource(R.string.permission_refresh_hint_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.medium))
                                Button(
                                    onClick = {
                                        (context as? android.app.Activity)?.let { activity ->
                                            ProcessPhoenix.triggerRebirth(activity)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                                    Spacer(modifier = Modifier.width(AppSpacing.small))
                                    Text(stringResource(R.string.button_restart_app))
                                }
                            }
                        }
                    }
                }

                if (!permissionStatus.isFullyGranted) {
                    Spacer(modifier = Modifier.height(AppSpacing.medium))
                    Text(
                        text = stringResource(R.string.permission_refresh_hint_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
