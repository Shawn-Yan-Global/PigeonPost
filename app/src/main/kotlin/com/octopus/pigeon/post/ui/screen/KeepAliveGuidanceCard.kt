package com.octopus.pigeon.post.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.permission.PermissionManager
import com.octopus.ui.spacing.AppSpacing

/**
 * Explains why the app looks "broken" after a day and which two system settings make that impossible.
 *
 * Without a foreground service the platform downgrades the app to the `RESTRICTED` standby bucket
 * roughly 24 hours after the last app launch, and the Chinese OEM ROMs go one step further and freeze
 * the process outright. Both kill `SMS_RECEIVED`, so the symptom is "worked on day one, nothing on
 * day two". The fix is always the same pair of settings, so we ask for them explicitly.
 */
@Composable
fun KeepAliveGuidanceCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val permissionStatus = PermissionManager.getPermissionStatus(context)
    val autostartRelevant = PermissionManager.requiresAutostartPermission(context)

    val isBatteryUnrestricted = permissionStatus.isIgnoringBatteryOptimizations
    val isAutostartGranted = permissionStatus.hasAutostartPermission

    val allConfigured = isBatteryUnrestricted && (!autostartRelevant || isAutostartGranted)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (allConfigured) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.large),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (allConfigured) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint =
                        if (allConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                    modifier = Modifier.size(AppSpacing.extraLarge),
                )
                Spacer(modifier = Modifier.width(AppSpacing.small))
                Text(
                    text = stringResource(R.string.keep_alive_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.small))

            Text(
                text = stringResource(R.string.keep_alive_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(AppSpacing.medium))

            // Step 1: unrestricted battery so the system never throttles or freezes the service
            GuidanceRow(
                icon = Icons.Default.BatteryAlert,
                title = stringResource(R.string.keep_alive_battery_title),
                description = stringResource(R.string.keep_alive_battery_desc),
                isDone = isBatteryUnrestricted,
                actionLabel = stringResource(R.string.button_battery_optimization),
                onAction = { PermissionManager.requestIgnoreBatteryOptimization(context) },
            )

            // Step 2: autostart, only meaningful on ROMs that implement it
            if (autostartRelevant) {
                Spacer(modifier = Modifier.height(AppSpacing.small))
                GuidanceRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = stringResource(R.string.keep_alive_autostart_title),
                    description = stringResource(R.string.keep_alive_autostart_desc),
                    isDone = isAutostartGranted,
                    actionLabel = stringResource(R.string.keep_alive_autostart_action),
                    onAction = { PermissionManager.openAutostartSettings(context) },
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.small))

            Text(
                text = stringResource(R.string.keep_alive_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuidanceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isDone: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isDone) Icons.Default.CheckCircle else icon,
            contentDescription = null,
            tint =
                if (isDone) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            modifier = Modifier.size(AppSpacing.extraLarge),
        )
        Spacer(modifier = Modifier.width(AppSpacing.small))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.width(AppSpacing.small))
        Text(
            text = if (isDone) stringResource(R.string.keep_alive_state_done) else stringResource(R.string.keep_alive_state_pending),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (isDone) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
    }

    Spacer(modifier = Modifier.height(AppSpacing.extraSmall))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(AppSpacing.small))
        if (!isDone) {
            OutlinedButton(
                onClick = onAction,
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
