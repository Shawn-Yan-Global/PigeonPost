package com.octopus.pigeon.post.ui.screen

import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Feed
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.model.SmsRecord
import com.octopus.pigeon.post.permission.PermissionManager
import com.octopus.pigeon.post.receiver.SmsReceiver
import com.octopus.pigeon.post.service.SmsMatchingService
import com.octopus.pigeon.post.service.SmsMonitorService
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel
import com.octopus.ui.spacing.AppSpacing
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsTestScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    // Get default test content with proper i18n support
    val defaultTestContent = stringResource(R.string.test_sms_default_content)

    var testSender by remember { mutableStateOf("10086") }
    var testContent by remember(defaultTestContent) { mutableStateOf(defaultTestContent) }
    var testResult by remember { mutableStateOf<SmsTestResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val templateConfig by viewModel.templateConfig.collectAsState()
    val emailConfig by viewModel.emailConfig.collectAsState()
    val serviceEnabled by viewModel.serviceEnabled.collectAsState()
    val activeTimeConfig by viewModel.activeTimeConfig.collectAsState()
    val recentRecords by viewModel.recentRecords.collectAsState()

    // Check foreground service status
    val isForegroundServiceRunning =
        remember(refreshTrigger) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val activeNotifications = notificationManager.activeNotifications
            activeNotifications.any { it.id == SmsMonitorService.NOTIFICATION_ID }
        }

    // Get detailed permission status
    val permissionStatus =
        remember(refreshTrigger) {
            PermissionManager.getPermissionStatus(context)
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
    ) {
        // Service status check
        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                        Text(
                            text = stringResource(R.string.service_status_check),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.medium))

                    StatusItem(
                        stringResource(R.string.sms_monitor_service_status),
                        if (serviceEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                        serviceEnabled,
                    )
                    StatusItem(
                        stringResource(R.string.active_time_restriction),
                        if (activeTimeConfig.isEnabled) {
                            stringResource(R.string.enabled)
                        } else {
                            stringResource(
                                R.string.disabled_all_day_monitor,
                            )
                        },
                        !activeTimeConfig.isEnabled || activeTimeConfig.isInActiveTime(),
                    )

                    // Show detailed active time status
                    if (activeTimeConfig.isEnabled) {
                        val currentInActiveTime = activeTimeConfig.isInActiveTime()
                        StatusItem(
                            stringResource(R.string.current_time_status),
                            if (currentInActiveTime) {
                                stringResource(R.string.in_active_time)
                            } else {
                                stringResource(
                                    R.string.not_in_active_time,
                                )
                            },
                            currentInActiveTime,
                        )
                        StatusItem(
                            stringResource(R.string.active_time_range),
                            "${
                                String.format(
                                    "%02d:%02d",
                                    activeTimeConfig.startHour,
                                    activeTimeConfig.startMinute,
                                )
                            } - ${
                                String.format(
                                    "%02d:%02d",
                                    activeTimeConfig.endHour,
                                    activeTimeConfig.endMinute,
                                )
                            }",
                            true,
                        )
                    }
                    StatusItem(
                        stringResource(R.string.keyword_count),
                        "${templateConfig.keywords.size} ${stringResource(R.string.keywords_unit)}",
                        templateConfig.keywords.isNotEmpty(),
                    )
                    StatusItem(
                        stringResource(R.string.email_configuration),
                        if (emailConfig.smtpServer.isNotBlank()) {
                            stringResource(R.string.configured)
                        } else {
                            stringResource(
                                R.string.not_configured,
                            )
                        },
                        emailConfig.smtpServer.isNotBlank(),
                    )

                    // Additional status check items
                    StatusItem(
                        stringResource(R.string.foreground_service_status),
                        if (isForegroundServiceRunning) {
                            stringResource(R.string.running)
                        } else {
                            stringResource(
                                R.string.not_running,
                            )
                        },
                        isForegroundServiceRunning,
                    )

                    // Show last received SMS time
                    val lastSmsTime = recentRecords.firstOrNull()?.receivedTime
                    StatusItem(
                        stringResource(R.string.last_received_sms),
                        if (lastSmsTime != null) {
                            "${lastSmsTime.toLocalDate()} ${lastSmsTime.toLocalTime()}"
                        } else {
                            stringResource(R.string.no_record)
                        },
                        lastSmsTime != null,
                    )

                    // Show duplicate prevention cache status
                    StatusItem(
                        stringResource(R.string.duplicate_prevention_cache),
                        stringResource(R.string.cache_entries, SmsReceiver.getCacheStatus()),
                        true,
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    // Detailed permission status
                    Text(
                        text = stringResource(R.string.detailed_permission_status),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = AppSpacing.small),
                    )

                    StatusItem(
                        stringResource(R.string.basic_sms_permissions),
                        if (permissionStatus.hasBasicPermissions) {
                            stringResource(R.string.granted)
                        } else {
                            stringResource(
                                R.string.missing_permissions,
                                permissionStatus.missingPermissions.joinToString(),
                            )
                        },
                        permissionStatus.hasBasicPermissions,
                    )

                    StatusItem(
                        stringResource(R.string.system_sms_access_permission),
                        if (permissionStatus.hasSmsAppOps) {
                            stringResource(R.string.enabled)
                        } else {
                            stringResource(
                                R.string.needs_manual_enable,
                            )
                        },
                        permissionStatus.hasSmsAppOps,
                    )

                    StatusItem(
                        stringResource(R.string.default_sms_app_status),
                        if (permissionStatus.isDefaultSmsApp) {
                            stringResource(R.string.is_default_app)
                        } else {
                            stringResource(
                                R.string.not_default_app,
                            )
                        },
                        permissionStatus.isDefaultSmsApp,
                    )

                    StatusItem(
                        stringResource(R.string.special_access_permissions),
                        if (permissionStatus.needsSpecialAccess) {
                            stringResource(R.string.needs_special_permissions)
                        } else {
                            stringResource(
                                R.string.normal,
                            )
                        },
                        !permissionStatus.needsSpecialAccess,
                    )

                    StatusItem(
                        stringResource(R.string.overall_permission_status),
                        if (permissionStatus.isFullyGranted) {
                            stringResource(R.string.fully_ready)
                        } else {
                            stringResource(
                                R.string.has_issues,
                            )
                        },
                        permissionStatus.isFullyGranted,
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.medium))

                    // Refresh status button
                    OutlinedButton(
                        onClick = { refreshTrigger++ },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                        Text(stringResource(R.string.refresh_service_status))
                    }

                    // Pre-fetch string resources for logging
                    val logTestActiveTime = stringResource(R.string.log_test_active_time)
                    val logEnabledStatus = stringResource(R.string.log_enabled_status, activeTimeConfig.isEnabled)
                    val logStartTime =
                        stringResource(
                            R.string.log_start_time,
                            String.format(
                                "%02d:%02d",
                                activeTimeConfig.startHour,
                                activeTimeConfig.startMinute,
                            ),
                        )
                    val logEndTime =
                        stringResource(
                            R.string.log_end_time,
                            String.format(
                                "%02d:%02d",
                                activeTimeConfig.endHour,
                                activeTimeConfig.endMinute,
                            ),
                        )

                    // Active time test button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                PigeonLogger.info("SmsTestScreen", logTestActiveTime)
                                PigeonLogger.info("SmsTestScreen", logEnabledStatus)
                                PigeonLogger.info("SmsTestScreen", logStartTime)
                                PigeonLogger.info("SmsTestScreen", logEndTime)
                                PigeonLogger.info(
                                    "SmsTestScreen",
                                    resources.getString(
                                        R.string.log_current_time,
                                        java.time.LocalTime.now(),
                                    ),
                                )
                                PigeonLogger.info(
                                    "SmsTestScreen",
                                    resources.getString(
                                        R.string.log_is_in_active_time_result,
                                        activeTimeConfig.isInActiveTime(),
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                        Text(stringResource(R.string.test_active_time_logic))
                    }
                }
            }
        }

        // Test input
        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                        Text(
                            text = stringResource(R.string.sms_test_input),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.large))

                    OutlinedTextField(
                        value = testSender,
                        onValueChange = { testSender = it },
                        label = { Text(stringResource(R.string.sender_number)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Next,
                                keyboardType = KeyboardType.Phone,
                            ),
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    OutlinedTextField(
                        value = testContent,
                        onValueChange = { testContent = it },
                        label = { Text(stringResource(R.string.sms_content)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Done,
                            ),
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.large))

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                testResult = null

                                try {
                                    PigeonLogger.info(
                                        "SmsTestScreen",
                                        "Start SMS test matching, sender: $testSender",
                                    )

                                    val smsMatchingService = SmsMatchingService()
                                    val smsRecord =
                                        SmsRecord(
                                            sender = testSender,
                                            content = testContent,
                                            receivedTime = LocalDateTime.now(),
                                        )

                                    // Test keyword matching
                                    val matchResult =
                                        smsMatchingService.matchKeywords(
                                            testContent,
                                            templateConfig,
                                        )
                                    PigeonLogger.debug(
                                        "SmsTestScreen",
                                        "Keyword match result: $matchResult",
                                    )

                                    // Test active time check
                                    val isInActiveTime = activeTimeConfig.isInActiveTime()
                                    PigeonLogger.debug(
                                        "SmsTestScreen",
                                        "Active time check: $isInActiveTime",
                                    )

                                    // Test verification code extraction
                                    val extractedCode =
                                        smsMatchingService.extractVerificationCode(testContent)
                                    PigeonLogger.debug(
                                        "SmsTestScreen",
                                        "Extracted verification code: $extractedCode",
                                    )
                                    PigeonLogger.debug(
                                        "SmsTestScreen",
                                        "serviceEnabled：$serviceEnabled",
                                    )
                                    PigeonLogger.debug("SmsTestScreen", "matchResult：$matchResult")
                                    PigeonLogger.debug(
                                        "SmsTestScreen",
                                        "!activeTimeConfig.isEnabled：${!activeTimeConfig.isEnabled}",
                                    )
                                    PigeonLogger.debug(
                                        "SmsTestScreen",
                                        "isInActiveTime：$isInActiveTime",
                                    )

                                    testResult =
                                        SmsTestResult(
                                            matched = matchResult,
                                            inActiveTime = isInActiveTime,
                                            extractedCode = extractedCode,
                                            shouldForward =
                                                serviceEnabled &&
                                                    matchResult &&
                                                    (!activeTimeConfig.isEnabled || isInActiveTime),
                                            matchedKeywords =
                                                templateConfig.keywords.filter { keyword ->
                                                    if (templateConfig.caseSensitive) {
                                                        testContent.contains(keyword)
                                                    } else {
                                                        testContent
                                                            .lowercase()
                                                            .contains(keyword.lowercase())
                                                    }
                                                },
                                        )

                                    PigeonLogger.info(
                                        "SmsTestScreen",
                                        "Test completed, should forward: ${testResult!!.shouldForward}",
                                    )
                                } catch (e: Exception) {
                                    PigeonLogger.error("SmsTestScreen", "Test failed", e)
                                    testResult =
                                        SmsTestResult(
                                            matched = false,
                                            inActiveTime = false,
                                            extractedCode = null,
                                            shouldForward = false,
                                            matchedKeywords = emptyList(),
                                            error = e.message,
                                        )
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && testSender.isNotBlank() && testContent.isNotBlank(),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(AppSpacing.extraLarge),
                                strokeWidth = AppSpacing.strokeWidth,
                            )
                        } else {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                        Text(stringResource(R.string.test_sms_matching))
                    }
                }
            }
        }

        // Preset test cases
        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Text(
                        text = stringResource(R.string.preset_test_cases),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = AppSpacing.large),
                    )

                    val testCases =
                        listOf(
                            TestCase("10086", stringResource(R.string.china_mobile_verification)),
                            TestCase(
                                "95588",
                                stringResource(R.string.icbc_transaction_notice),
                            ),
                            TestCase("10010", stringResource(R.string.china_unicom_balance_notice)),
                            TestCase("106902", stringResource(R.string.taobao_verification)),
                        )

                    testCases.forEach { testCase ->
                        OutlinedButton(
                            onClick = {
                                testSender = testCase.sender
                                testContent = testCase.content
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppSpacing.tiny),
                        ) {
                            Text(
                                text = "${testCase.sender}: ${testCase.content.take(20)}...",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        // Test results
        testResult?.let { result ->
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(AppSpacing.large),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                if (result.error !=
                                    null
                                ) {
                                    Icons.Default.Warning
                                } else {
                                    Icons.AutoMirrored.Default.PlaylistAddCheck
                                },
                                contentDescription = null,
                                tint =
                                    if (result.error !=
                                        null
                                    ) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.small))
                            Text(
                                text = stringResource(R.string.test_results),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.medium))

                        if (result.error != null) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.test_failed_error,
                                        result.error,
                                    ),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            TestResultItem(
                                stringResource(R.string.keyword_matching),
                                if (result.matched) {
                                    stringResource(R.string.matched)
                                } else {
                                    stringResource(
                                        R.string.not_matched,
                                    )
                                },
                                result.matched,
                            )
                            TestResultItem(
                                stringResource(R.string.active_time_period),
                                if (result.inActiveTime) {
                                    stringResource(R.string.in_time_period)
                                } else {
                                    stringResource(
                                        R.string.not_in_time_period,
                                    )
                                },
                                result.inActiveTime,
                            )
                            TestResultItem(
                                stringResource(R.string.should_forward),
                                if (result.shouldForward) {
                                    stringResource(R.string.yes)
                                } else {
                                    stringResource(
                                        R.string.no,
                                    )
                                },
                                result.shouldForward,
                            )

                            if (result.extractedCode != null) {
                                TestResultItem(
                                    stringResource(R.string.extract_verification_code),
                                    result.extractedCode,
                                    true,
                                )
                            }

                            if (result.matchedKeywords.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(AppSpacing.small))
                                Text(
                                    text =
                                        stringResource(
                                            R.string.matched_keywords,
                                            result.matchedKeywords.joinToString(", "),
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusItem(
    label: String,
    value: String,
    isGood: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacing.tiny),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isGood) Icons.AutoMirrored.Default.Feed else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(AppSpacing.large),
            )
            Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TestResultItem(
    label: String,
    value: String,
    isGood: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacing.tiny),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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
            color = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

data class TestCase(
    val sender: String,
    val content: String,
)

data class SmsTestResult(
    val matched: Boolean,
    val inActiveTime: Boolean,
    val extractedCode: String?,
    val shouldForward: Boolean,
    val matchedKeywords: List<String>,
    val error: String? = null,
)
