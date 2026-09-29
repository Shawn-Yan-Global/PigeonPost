package com.octopus.logging.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.octopus.logging.PigeonLogger
import com.octopus.logging.R
import com.octopus.logging.model.LogFileInfo
import com.octopus.ui.spacing.AppSpacing
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Configuration interface for log cleanup
 */
interface LogCleanupConfig {
    suspend fun getAutoCleanupEnabled(): Boolean

    suspend fun getAutoCleanupDays(): Int

    suspend fun setAutoCleanup(
        enabled: Boolean,
        days: Int,
    )

    /**
     * Run a one-off cleanup right now and return the number of deleted log files.
     *
     * Returns 0 when nothing was old enough to be removed, which is the common case right after a
     * fresh install. Callers must report that outcome instead of treating it as a failure.
     */
    suspend fun triggerImmediateCleanup(days: Int): Int
}

/**
 * Centralized log management screen that integrates log viewing and language settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogManagementScreen(
    onBackClick: () -> Unit,
    onOpenLogFiles: () -> Unit,
    cleanupConfig: LogCleanupConfig,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val cleanupDeletedTemplate = stringResource(R.string.cleanup_deleted_result)
    val cleanupNothingOldTemplate = stringResource(R.string.cleanup_nothing_old)

    var logFiles by remember { mutableStateOf<List<LogFileInfo>>(emptyList()) }
    var showAutoCleanupDialog by remember { mutableStateOf(false) }
    var showCleanupConfirmDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var autoCleanupLogsEnabled by remember { mutableStateOf(false) }
    var autoCleanupLogsDays by remember { mutableIntStateOf(7) }

    fun refreshLogFiles() {
        logFiles = PigeonLogger.getAllLogFiles()
    }

    LaunchedEffect(Unit) {
        refreshLogFiles()
        autoCleanupLogsEnabled = cleanupConfig.getAutoCleanupEnabled()
        autoCleanupLogsDays = cleanupConfig.getAutoCleanupDays()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.log_management),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
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
                // Overview information
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(AppSpacing.large),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.small))
                                Text(
                                    stringResource(R.string.log_overview),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Spacer(modifier = Modifier.height(AppSpacing.medium))

                            LogInfoItem(
                                stringResource(R.string.total_log_files),
                                stringResource(R.string.log_files_count_display, logFiles.size),
                            )
                            LogInfoItem(
                                stringResource(R.string.total_size),
                                stringResource(
                                    R.string.total_size_kb,
                                    logFiles.sumOf { it.size } / 1024,
                                ),
                            )

                            if (logFiles.isNotEmpty()) {
                                val latestFile = logFiles.first()
                                LogInfoItem(
                                    stringResource(R.string.latest_file),
                                    latestFile.path.substringAfterLast("/"),
                                )
                                LogInfoItem(
                                    stringResource(R.string.last_updated),
                                    latestFile.lastModified,
                                )
                            }
                        }
                    }
                }

                // Quick actions
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(AppSpacing.large),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.small))
                                Text(
                                    stringResource(R.string.quick_actions),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Spacer(modifier = Modifier.height(AppSpacing.large))

                            Button(
                                onClick = { onOpenLogFiles() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                                Spacer(modifier = Modifier.width(AppSpacing.small))
                                Text(stringResource(R.string.view_all_log_files))
                            }

                            Spacer(modifier = Modifier.height(AppSpacing.small))

                            Button(
                                onClick = { showExportDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = logFiles.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(AppSpacing.small))
                                Text(stringResource(R.string.export_logs))
                            }

                            Spacer(modifier = Modifier.height(AppSpacing.small))

                            OutlinedButton(
                                onClick = { showClearDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = logFiles.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.small))
                                Text(
                                    stringResource(R.string.clear_logs),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                // Auto cleanup settings
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(AppSpacing.large),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.small))
                                Text(
                                    stringResource(R.string.auto_cleanup_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Spacer(modifier = Modifier.height(AppSpacing.large))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.auto_cleanup_log_files),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        if (autoCleanupLogsEnabled) {
                                            stringResource(
                                                R.string.auto_delete_logs_desc,
                                                autoCleanupLogsDays,
                                            )
                                        } else {
                                            stringResource(R.string.manual_log_management_desc)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = autoCleanupLogsEnabled,
                                    onCheckedChange = { newValue ->
                                        if (newValue) {
                                            showAutoCleanupDialog = true
                                        } else {
                                            autoCleanupLogsEnabled = false
                                            scope.launch {
                                                cleanupConfig.setAutoCleanup(false, autoCleanupLogsDays)
                                            }
                                        }
                                    },
                                )
                            }

                            if (autoCleanupLogsEnabled) {
                                Spacer(modifier = Modifier.height(AppSpacing.large))

                                Text(
                                    stringResource(R.string.cleanup_period),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.small))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    listOf(3, 7, 30).forEach { days ->
                                        FilterChip(
                                            onClick = {
                                                autoCleanupLogsDays = days
                                                scope.launch {
                                                    cleanupConfig.setAutoCleanup(
                                                        autoCleanupLogsEnabled,
                                                        days,
                                                    )
                                                }
                                            },
                                            label = {
                                                Text(
                                                    stringResource(
                                                        R.string.days_ago,
                                                        days,
                                                    ),
                                                )
                                            },
                                            selected = autoCleanupLogsDays == days,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dialogs
            if (showAutoCleanupDialog) {
                AlertDialog(
                    onDismissRequest = { showAutoCleanupDialog = false },
                    title = { Text(stringResource(R.string.enable_auto_cleanup_now)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.select_auto_cleanup_period))
                            Spacer(modifier = Modifier.height(AppSpacing.large))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                listOf(3, 7, 30).forEach { days ->
                                    FilterChip(
                                        onClick = { autoCleanupLogsDays = days },
                                        label = { Text(stringResource(R.string.days_ago, days)) },
                                        selected = autoCleanupLogsDays == days,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(AppSpacing.large))
                            Text(
                                stringResource(R.string.will_auto_delete_logs, autoCleanupLogsDays),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showAutoCleanupDialog = false
                                showCleanupConfirmDialog = true
                            },
                        ) {
                            Text(stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAutoCleanupDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            if (showCleanupConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showCleanupConfirmDialog = false },
                    title = { Text(stringResource(R.string.enable_auto_cleanup_now)) },
                    text = { Text(stringResource(R.string.execute_cleanup_immediately)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val days = autoCleanupLogsDays
                                autoCleanupLogsEnabled = true
                                scope.launch {
                                    cleanupConfig.setAutoCleanup(true, days)
                                    val deleted = cleanupConfig.triggerImmediateCleanup(days)
                                    // The cleanup is awaited, so this refresh already sees the final
                                    // file list and no polling is needed.
                                    refreshLogFiles()
                                    val message =
                                        if (deleted > 0) {
                                            String.format(
                                                Locale.getDefault(),
                                                cleanupDeletedTemplate,
                                                deleted,
                                            )
                                        } else {
                                            String.format(
                                                Locale.getDefault(),
                                                cleanupNothingOldTemplate,
                                                days,
                                            )
                                        }
                                    snackbarHostState.showSnackbar(message)
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
                                autoCleanupLogsEnabled = true
                                scope.launch {
                                    cleanupConfig.setAutoCleanup(true, autoCleanupLogsDays)
                                }
                                showCleanupConfirmDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.cleanup_later))
                        }
                    },
                )
            }

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text(stringResource(R.string.clear_logs)) },
                    text = { Text(stringResource(R.string.clear_logs_desc)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val deleted = PigeonLogger.clearLogs(null)
                                refreshLogFiles()
                                if (deleted > 0) {
                                    PigeonLogger.info(
                                        "LogManagementScreen",
                                        "User cleared $deleted log files",
                                    )
                                }
                                showClearDialog = false
                            },
                        ) { Text(stringResource(R.string.clear_all)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            if (showExportDialog) {
                AlertDialog(
                    onDismissRequest = { showExportDialog = false },
                    title = { Text(stringResource(R.string.export_logs)) },
                    text = { Text(stringResource(R.string.export_logs_desc)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val exportIntent = PigeonLogger.exportLogs(context, null)
                                if (exportIntent != null) {
                                    context.startActivity(exportIntent)
                                    PigeonLogger.info(
                                        "LogManagementScreen",
                                        "User triggered log export",
                                    )
                                }
                                showExportDialog = false
                            },
                        ) { Text(stringResource(R.string.export)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExportDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LogInfoItem(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacing.tiny),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
