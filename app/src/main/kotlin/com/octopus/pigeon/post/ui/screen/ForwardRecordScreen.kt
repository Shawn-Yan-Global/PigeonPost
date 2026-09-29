package com.octopus.pigeon.post.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.database.AppDatabase
import com.octopus.pigeon.post.data.model.SmsRecord
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.data.repository.SmsRepository
import com.octopus.pigeon.post.service.CleanupService
import com.octopus.pigeon.post.service.CleanupWorker
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel
import com.octopus.ui.spacing.AppSpacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardRecordScreen(
    paddingValues: PaddingValues,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentRecords by viewModel.recentRecords.collectAsState()

    var isEditMode by remember { mutableStateOf(false) }
    var selectedRecords by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var selectedCleanupDays by remember { mutableIntStateOf(7) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showForceRetryConfirmDialog by remember { mutableStateOf(false) }
    var recordToRetry by remember { mutableStateOf<SmsRecord?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showSingleDeleteConfirmDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<SmsRecord?>(null) }

    // Notification and auto-cleanup settings
    var smsReceivedNotificationEnabled by remember { mutableStateOf(true) }
    var smsForwardedNotificationEnabled by remember { mutableStateOf(true) }
    var autoCleanupRecordsEnabled by remember { mutableStateOf(false) }
    var autoCleanupRecordsDays by remember { mutableIntStateOf(7) }

    // Load settings
    LaunchedEffect(Unit) {
        val preferencesRepository = PreferencesRepository(context)
        smsReceivedNotificationEnabled =
            preferencesRepository.smsReceivedNotificationEnabled.first()
        smsForwardedNotificationEnabled =
            preferencesRepository.smsForwardedNotificationEnabled.first()
        autoCleanupRecordsEnabled = preferencesRepository.autoCleanupRecordsEnabled.first()
        autoCleanupRecordsDays = preferencesRepository.autoCleanupRecordsDays.first()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                // Simulate refresh delay
                kotlinx.coroutines.delay(500)
                isRefreshing = false
            }
        },
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
        ) {
            // Title and action bar
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
                                    text =
                                        if (isEditMode) {
                                            stringResource(R.string.select_records_to_delete)
                                        } else {
                                            stringResource(
                                                R.string.forward_records_title,
                                            )
                                        },
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = AppSpacing.extraSmall),
                                )
                                Text(
                                    text =
                                        if (isEditMode) {
                                            stringResource(
                                                R.string.selected_records,
                                                selectedRecords.size,
                                            )
                                        } else {
                                            stringResource(R.string.recent_sms_desc)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Row {
                                if (isEditMode) {
                                    // Select all button
                                    IconButton(
                                        onClick = {
                                            selectedRecords =
                                                if (selectedRecords.size == recentRecords.size) {
                                                    emptySet()
                                                } else {
                                                    recentRecords.map { it.id }.toSet()
                                                }
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = stringResource(R.string.select_all),
                                        )
                                    }

                                    // delete button
                                    IconButton(
                                        onClick = {
                                            if (selectedRecords.isNotEmpty()) {
                                                showDeleteConfirmDialog = true
                                            }
                                        },
                                        enabled = selectedRecords.isNotEmpty(),
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = if (selectedRecords.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    // Cancel button
                                    IconButton(
                                        onClick = {
                                            isEditMode = false
                                            selectedRecords = emptySet()
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.cancel),
                                        )
                                    }
                                } else {
                                    // Settings button
                                    IconButton(
                                        onClick = { showSettingsDialog = true },
                                    ) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = stringResource(R.string.notification_settings_desc),
                                        )
                                    }

                                    // Clean button
                                    IconButton(
                                        onClick = { showCleanupDialog = true },
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.cleanup_records_desc),
                                        )
                                    }

                                    // Edit button
                                    IconButton(
                                        onClick = { isEditMode = true },
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.edit_desc),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Record list
            if (recentRecords.isEmpty()) {
                item {
                    Card {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(AppSpacing.xxxLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(AppSpacing.minTouchTarget),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.large))
                            Text(
                                text = stringResource(R.string.no_sms_records),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.sms_will_show_here),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(recentRecords) { record ->
                    SmsRecordItem(
                        record = record,
                        isEditMode = isEditMode,
                        isSelected = selectedRecords.contains(record.id),
                        onSelectionChanged = { isSelected ->
                            selectedRecords =
                                if (isSelected) {
                                    selectedRecords + record.id
                                } else {
                                    selectedRecords - record.id
                                }
                        },
                        onRetry = {
                            recordToRetry = record
                            showForceRetryConfirmDialog = true
                        },
                        onRetryWithFilter = { viewModel.retryForwardSmsWithFilter(record) },
                        onDelete = {
                            recordToDelete = record
                            showSingleDeleteConfirmDialog = true
                        },
                    )
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.confirm_delete_message,
                        selectedRecords.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val database = AppDatabase.getDatabase(context)
                            val smsRepository = SmsRepository(database.smsRecordDao())
                            smsRepository.deleteRecordsByIds(selectedRecords.toList())
                            selectedRecords = emptySet()
                            isEditMode = false
                            showDeleteConfirmDialog = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showCleanupDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            title = { Text(stringResource(R.string.cleanup_records_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.select_cleanup_time_range))
                    Spacer(modifier = Modifier.height(AppSpacing.large))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        listOf(3, 7, 30).forEach { days ->
                            FilterChip(
                                onClick = { selectedCleanupDays = days },
                                label = { Text(stringResource(R.string.days_ago_label, days)) },
                                selected = selectedCleanupDays == days,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.small))
                    Text(
                        stringResource(
                            R.string.will_delete_records_before_days,
                            selectedCleanupDays,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent =
                            Intent(context, CleanupService::class.java).apply {
                                action = CleanupService.ACTION_CLEANUP_RECORDS
                                putExtra(CleanupService.EXTRA_CLEANUP_DAYS, selectedCleanupDays)
                            }
                        context.startService(intent)
                        showCleanupDialog = false
                    },
                ) {
                    Text(
                        stringResource(R.string.cleanup),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    var showCleanupConfirmDialog by remember { mutableStateOf(false) }

    if (showForceRetryConfirmDialog && recordToRetry != null) {
        AlertDialog(
            onDismissRequest = {
                showForceRetryConfirmDialog = false
                recordToRetry = null
            },
            title = { Text(stringResource(R.string.force_retry_confirm_title)) },
            text = {
                Text(stringResource(R.string.force_retry_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordToRetry?.let { viewModel.retryForwardSms(it) }
                        showForceRetryConfirmDialog = false
                        recordToRetry = null
                    },
                ) {
                    Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showForceRetryConfirmDialog = false
                        recordToRetry = null
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showSingleDeleteConfirmDialog && recordToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showSingleDeleteConfirmDialog = false
                recordToDelete = null
            },
            title = { Text(stringResource(R.string.confirm_delete_single_title)) },
            text = {
                Text(stringResource(R.string.confirm_delete_single_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordToDelete?.let { record ->
                            scope.launch {
                                val database = AppDatabase.getDatabase(context)
                                val smsRepository = SmsRepository(database.smsRecordDao())
                                smsRepository.deleteRecordsByIds(listOf(record.id))
                                showSingleDeleteConfirmDialog = false
                                recordToDelete = null
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSingleDeleteConfirmDialog = false
                        recordToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(stringResource(R.string.forward_record_settings_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.notification_settings_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.sms_received_notification_title))
                            Text(
                                stringResource(R.string.sms_received_notification_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = smsReceivedNotificationEnabled,
                            onCheckedChange = {
                                smsReceivedNotificationEnabled = it
                                scope.launch {
                                    val preferencesRepository = PreferencesRepository(context)
                                    preferencesRepository.setSmsReceivedNotificationEnabled(it)
                                }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.forward_result_notification_title))
                            Text(
                                stringResource(R.string.forward_result_notification_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = smsForwardedNotificationEnabled,
                            onCheckedChange = {
                                smsForwardedNotificationEnabled = it
                                scope.launch {
                                    val preferencesRepository = PreferencesRepository(context)
                                    preferencesRepository.setSmsForwardedNotificationEnabled(it)
                                }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(AppSpacing.large))

                    Text(
                        stringResource(R.string.auto_cleanup_settings_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.auto_cleanup_forward_records))
                            Text(
                                stringResource(
                                    R.string.auto_delete_records_desc,
                                    autoCleanupRecordsDays,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = autoCleanupRecordsEnabled,
                            onCheckedChange = { newValue ->
                                if (newValue) {
                                    showSettingsDialog = false
                                    showCleanupConfirmDialog = true
                                } else {
                                    autoCleanupRecordsEnabled = false
                                    scope.launch {
                                        val preferencesRepository =
                                            PreferencesRepository(context)
                                        preferencesRepository.setAutoCleanupRecords(
                                            false,
                                            autoCleanupRecordsDays,
                                        )
                                        // Only stop the schedule when neither records nor logs
                                        // want automatic cleanup any more.
                                        if (!preferencesRepository.autoCleanupLogsEnabled.first()) {
                                            CleanupWorker.cancel(context)
                                        }
                                    }
                                }
                            },
                        )
                    }

                    if (autoCleanupRecordsEnabled) {
                        Spacer(modifier = Modifier.height(AppSpacing.small))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            listOf(3, 7, 30).forEach { days ->
                                FilterChip(
                                    onClick = {
                                        autoCleanupRecordsDays = days
                                        scope.launch {
                                            val preferencesRepository =
                                                PreferencesRepository(context)
                                            preferencesRepository.setAutoCleanupRecords(
                                                autoCleanupRecordsEnabled,
                                                days,
                                            )
                                        }
                                    },
                                    label = { Text(stringResource(R.string.days_label, days)) },
                                    selected = autoCleanupRecordsDays == days,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(stringResource(R.string.done))
                }
            },
        )
    }

    if (showCleanupConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirmDialog = false },
            title = { Text(stringResource(R.string.enable_auto_cleanup_title)) },
            text = { Text(stringResource(R.string.execute_cleanup_now_question)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        autoCleanupRecordsEnabled = true
                        scope.launch {
                            val preferencesRepository = PreferencesRepository(context)
                            preferencesRepository.setAutoCleanupRecords(
                                true,
                                autoCleanupRecordsDays,
                            )
                            val intent =
                                Intent(context, CleanupService::class.java).apply {
                                    action = CleanupService.ACTION_CLEANUP_RECORDS
                                    putExtra(
                                        CleanupService.EXTRA_CLEANUP_DAYS,
                                        autoCleanupRecordsDays,
                                    )
                                }
                            context.startService(intent)
                            // Also arm the recurring schedule, otherwise this one-off pass is
                            // the only cleanup the user will ever get.
                            CleanupWorker.enqueue(context)
                        }
                        showCleanupConfirmDialog = false
                    },
                ) {
                    Text(stringResource(R.string.cleanup_now))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        autoCleanupRecordsEnabled = true
                        scope.launch {
                            val preferencesRepository = PreferencesRepository(context)
                            preferencesRepository.setAutoCleanupRecords(
                                true,
                                autoCleanupRecordsDays,
                            )
                            CleanupWorker.enqueue(context)
                        }
                        showCleanupConfirmDialog = false
                    },
                ) {
                    Text(stringResource(R.string.cleanup_later))
                }
            },
        )
    }
}

@Composable
fun SmsRecordItem(
    record: SmsRecord,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChanged: (Boolean) -> Unit = {},
    onRetry: () -> Unit,
    onRetryWithFilter: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    Card {
        Column(
            modifier = Modifier.padding(AppSpacing.large),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isEditMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = onSelectionChanged,
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                    }

                    Column {
                        Text(
                            text = record.sender,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = record.receivedTime.format(formatter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                ) {
                    when {
                        record.isForwarded && record.forwardSuccess -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(AppSpacing.extraLarge),
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                                Text(
                                    text = stringResource(R.string.forwarded),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        record.isForwarded && !record.forwardSuccess -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(AppSpacing.extraLarge),
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                                Text(
                                    text = stringResource(R.string.forward_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        else -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(AppSpacing.extraLarge),
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                                Text(
                                    text = stringResource(R.string.not_forwarded),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (!isEditMode) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(AppSpacing.xxxLarge),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(AppSpacing.extraLarge),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.small))

            Text(
                text = record.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            // Always show error details when forward failed
            if (!record.forwardSuccess) {
                Spacer(modifier = Modifier.height(AppSpacing.small))
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.medium),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(AppSpacing.large),
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                            Text(
                                text = stringResource(R.string.error_info),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        Spacer(modifier = Modifier.height(AppSpacing.extraSmall))
                        Text(
                            text =
                                if (record.errorMessage.isNullOrBlank()) {
                                    stringResource(R.string.forward_error_unknown)
                                } else {
                                    record.errorMessage
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )

                        // Add common error hints
                        if (record.errorMessage?.contains(
                                "authentication",
                                ignoreCase = true,
                            ) == true ||
                            record.errorMessage?.contains("password", ignoreCase = true) == true
                        ) {
                            Spacer(modifier = Modifier.height(AppSpacing.small))
                            Text(
                                text = "💡 " + stringResource(R.string.forward_error_hint_auth),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            )
                        } else if (record.errorMessage?.contains(
                                "connect",
                                ignoreCase = true,
                            ) == true ||
                            record.errorMessage?.contains("network", ignoreCase = true) == true ||
                            record.errorMessage?.contains("timeout", ignoreCase = true) == true
                        ) {
                            Spacer(modifier = Modifier.height(AppSpacing.small))
                            Text(
                                text = "💡 " + stringResource(R.string.forward_error_hint_network),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            )
                        } else if (record.errorMessage?.contains(
                                "smtp",
                                ignoreCase = true,
                            ) == true ||
                            record.errorMessage?.contains("server", ignoreCase = true) == true
                        ) {
                            Spacer(modifier = Modifier.height(AppSpacing.small))
                            Text(
                                text = "💡 " + stringResource(R.string.forward_error_hint_smtp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            )
                        }
                    }
                }
            }

            if (!record.forwardSuccess) {
                Spacer(modifier = Modifier.height(AppSpacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                ) {
                    OutlinedButton(
                        onClick = onRetryWithFilter,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                        Text(stringResource(R.string.retry_with_filter))
                    }
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                        Text(stringResource(R.string.force_retry))
                    }
                }
            }
        }
    }
}
