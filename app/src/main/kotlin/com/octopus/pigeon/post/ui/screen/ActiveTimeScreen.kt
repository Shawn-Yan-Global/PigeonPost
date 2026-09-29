package com.octopus.pigeon.post.ui.screen

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.model.ActiveTimeConfig
import com.octopus.pigeon.post.ui.component.ConfirmSaveDialog
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel
import com.octopus.ui.spacing.AppSpacing
import java.time.LocalTime

/**
 * Enhanced active time configuration screen with improved time selection UI
 * Supports cross-day time periods and intuitive time management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTimeScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activeTimeConfig by viewModel.activeTimeConfig.collectAsState()

    var isEnabled by remember { mutableStateOf(activeTimeConfig.isEnabled) }
    var startHour by remember { mutableIntStateOf(activeTimeConfig.startHour) }
    var startMinute by remember { mutableIntStateOf(activeTimeConfig.startMinute) }
    var endHour by remember { mutableIntStateOf(activeTimeConfig.endHour) }
    var endMinute by remember { mutableIntStateOf(activeTimeConfig.endMinute) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    var pendingConfig by remember { mutableStateOf<ActiveTimeConfig?>(null) }

    LaunchedEffect(activeTimeConfig) {
        isEnabled = activeTimeConfig.isEnabled
        startHour = activeTimeConfig.startHour
        startMinute = activeTimeConfig.startMinute
        endHour = activeTimeConfig.endHour
        endMinute = activeTimeConfig.endMinute
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
    ) {
        // Feature description
        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.enable_active_time_control),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.active_time_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                // Only update the form here. Persisting immediately would let a
                                // stray tap turn the schedule on or off without confirmation, and
                                // would skip the confirmation the Save button asks for.
                                isEnabled = enabled
                            },
                        )
                    }
                }
            }
        }

        // Quick configuration
        if (isEnabled) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(AppSpacing.large),
                    ) {
                        Text(
                            text = stringResource(R.string.quick_config),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = AppSpacing.small),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    startHour = 8
                                    startMinute = 0
                                    endHour = 9
                                    endMinute = 0
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.morning_rush))
                            }

                            OutlinedButton(
                                onClick = {
                                    startHour = 17
                                    startMinute = 0
                                    endHour = 19
                                    endMinute = 0
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.evening_rush))
                            }
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.small))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    startHour = 9
                                    startMinute = 0
                                    endHour = 18
                                    endMinute = 0
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.work_hours))
                            }

                            OutlinedButton(
                                onClick = {
                                    startHour = 0
                                    startMinute = 0
                                    endHour = 23
                                    endMinute = 59
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.all_day_listening))
                            }
                        }
                    }
                }
            }
        }

        // Time settings
        if (isEnabled) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(AppSpacing.large),
                    ) {
                        Text(
                            text = stringResource(R.string.time_settings),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = AppSpacing.small),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.large),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.start_time),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = AppSpacing.small),
                                )

                                OutlinedButton(
                                    onClick = {
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                startHour = hour
                                                startMinute = minute
                                            },
                                            startHour,
                                            startMinute,
                                            true,
                                        ).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null)
                                    Spacer(modifier = Modifier.width(AppSpacing.small))
                                    Text(String.format("%02d:%02d", startHour, startMinute))
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.end_time),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = AppSpacing.small),
                                )

                                OutlinedButton(
                                    onClick = {
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                endHour = hour
                                                endMinute = minute
                                            },
                                            endHour,
                                            endMinute,
                                            true,
                                        ).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null)
                                    Spacer(modifier = Modifier.width(AppSpacing.small))
                                    Text(String.format("%02d:%02d", endHour, endMinute))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.large))

                        // Current status display
                        val currentTime = LocalTime.now()
                        val startTime = LocalTime.of(startHour, startMinute)
                        val endTime = LocalTime.of(endHour, endMinute)
                        val isInTimeRange =
                            if (startTime <= endTime) {
                                currentTime in startTime..endTime
                            } else {
                                currentTime >= startTime || currentTime <= endTime
                            }

                        // Cross-day indicator
                        val isCrossDay = startTime > endTime
                        if (isCrossDay) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = AppSpacing.small),
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(AppSpacing.large),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                                Text(
                                    stringResource(R.string.cross_day_period),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Display different status based on whether active time feature is enabled
                        val (statusText, statusColor) =
                            if (!isEnabled) {
                                stringResource(R.string.status_disabled) to MaterialTheme.colorScheme.primary
                            } else if (isInTimeRange) {
                                stringResource(R.string.status_active) to MaterialTheme.colorScheme.primary
                            } else {
                                stringResource(R.string.status_paused) to MaterialTheme.colorScheme.onSurfaceVariant
                            }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                if (isInTimeRange && isEnabled) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = statusColor,
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.small))
                            Text(
                                text = statusText,
                                color = statusColor,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        // 24-hour visualization
        if (isEnabled) {
            item {
                TimeVisualizationBar(startHour, startMinute, endHour, endMinute)
            }
        }

        // Save button
        item {
            Button(
                onClick = {
                    // Hold the pending config until the user confirms, so an accidental tap
                    // cannot silently change when the app forwards messages.
                    pendingConfig =
                        ActiveTimeConfig(
                            isEnabled = isEnabled,
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                        )
                    showSaveConfirmDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(AppSpacing.small))
                Text(stringResource(R.string.save))
            }
        }
    }

    if (showSaveConfirmDialog) {
        val pending = pendingConfig
        ConfirmSaveDialog(
            title = stringResource(R.string.confirm_save_title),
            message = stringResource(R.string.confirm_save_active_time_message),
            onConfirm = {
                showSaveConfirmDialog = false
                pending?.let(viewModel::updateActiveTimeConfig)
                pendingConfig = null
            },
            onDismiss = {
                showSaveConfirmDialog = false
                pendingConfig = null
            },
        )
    }
}

/**
 * Visual representation of active time periods across 24 hours
 */
@Composable
private fun TimeVisualizationBar(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
) {
    Card {
        Column(
            modifier = Modifier.padding(AppSpacing.large),
        ) {
            Text(
                stringResource(R.string.active_periods_24h),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = AppSpacing.small),
            )

            // Time scale (0-23 hours)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.hairline),
            ) {
                items(24) { hour ->
                    val startTime = LocalTime.of(startHour, startMinute)
                    val endTime = LocalTime.of(endHour, endMinute)
                    val isCrossDay = startTime > endTime

                    val isActive =
                        if (isCrossDay) {
                            // Cross-day: active if hour >= start OR hour <= end
                            hour >= startHour || hour <= endHour
                        } else {
                            // Same day: active if hour is between start and end
                            hour >= startHour && hour <= endHour
                        }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(AppSpacing.medium),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(AppSpacing.medium, AppSpacing.xxxLarge)
                                    .clip(RoundedCornerShape(AppSpacing.tinyCornerRadius))
                                    .background(
                                        if (isActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    ),
                        )

                        if (hour % 6 == 0) {
                            Text(
                                text = hour.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = AppSpacing.tiny),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.small))

            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.large),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(AppSpacing.medium)
                                .clip(RoundedCornerShape(AppSpacing.tinyCornerRadius))
                                .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                    Text(
                        stringResource(R.string.active),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(AppSpacing.medium)
                                .clip(RoundedCornerShape(AppSpacing.tinyCornerRadius))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                    Text(
                        stringResource(R.string.inactive),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
