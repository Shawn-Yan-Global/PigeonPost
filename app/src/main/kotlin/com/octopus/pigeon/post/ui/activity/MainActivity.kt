package com.octopus.pigeon.post.ui.activity

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.octopus.logging.PigeonLogger
import com.octopus.logging.ui.activity.LogManagementActivity
import com.octopus.pigeon.post.BuildConfig
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.permission.PermissionManager
import com.octopus.pigeon.post.service.PermissionMonitorService
import com.octopus.pigeon.post.service.SmsKeepAliveWorker
import com.octopus.pigeon.post.service.SmsMonitorService
import com.octopus.pigeon.post.ui.base.BaseActivity
import com.octopus.pigeon.post.ui.screen.MainScreen
import com.octopus.pigeon.post.ui.theme.PigeonPostTheme
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel
import com.octopus.pigeon.post.util.PigeonPostNotificationManager
import com.octopus.pigeon.post.util.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {
    private val viewModel: MainViewModel by viewModels()

    private lateinit var onPermissionChanged: () -> Unit

    override fun onPermissionStateChanged() {
        super.onPermissionStateChanged()
        // Trigger UI refresh when returning from settings
        if (::onPermissionChanged.isInitialized) {
            onPermissionChanged()
        }
        PigeonLogger.info(TAG, "Permissions state refreshed after returning to MainActivity")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PigeonLogger.info(TAG, "MainActivity started")

        // Basic security check
        // Skipped in the silent build: these checks look for debugging and
        // emulator indicators, and an enterprise-managed device is commonly
        // instrumented, so the app would refuse to run on the very devices it
        // is meant for.
        if (!BuildConfig.SILENT && !SecurityUtils.performSecurityCheck(this)) {
            PigeonLogger.warn(TAG, "Security check failed - running in debug/insecure environment")
        }

        // Initialize enhanced notification manager
        PigeonPostNotificationManager.initializeChannels(this)

        // Check if service is running and sync status
        checkServiceStatus()

        // Auto-restore service if previously enabled by user
        autoRestoreServiceIfEnabled()
        updatePermissionMonitorService()

        setContent {
            PigeonPostTheme {
                var permissionRefreshTrigger by remember { mutableIntStateOf(0) }

                onPermissionChanged = {
                    permissionRefreshTrigger++
                }
                onBackPressedDispatcher.addCallback(
                    this,
                    object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() {
                            // On main screen, move app to background instead of finishing
                            PigeonLogger.info(TAG, "Moving application to background from main screen")
                            moveTaskToBack(true)
                        }
                    },
                )

                MainScreen(
                    viewModel = viewModel,
                    onDebugClick = {
                        startActivity(
                            Intent(
                                this@MainActivity,
                                DebugActivity::class.java,
                            ),
                        )
                    },
                    onLogManagementClick = {
                        startActivity(
                            Intent(
                                this@MainActivity,
                                LogManagementActivity::class.java,
                            ),
                        )
                    },
                    onForwardRecordsClick = {
                        startActivity(
                            Intent(
                                this@MainActivity,
                                ForwardRecordsActivity::class.java,
                            ),
                        )
                    },
                    onLanguageSettingsClick = {
                        startActivity(
                            Intent(
                                this@MainActivity,
                                LanguageSettingsActivity::class.java,
                            ),
                        )
                    },
                    permissionRefreshTrigger = permissionRefreshTrigger,
                )
            }
        }
    }

    private fun updatePermissionMonitorService() {
        val permissionIntent = Intent(this, PermissionMonitorService::class.java)
        if (PermissionManager.hasAllPermissions(this)) {
            PigeonLogger.debug(TAG, "All permissions granted, stopping permission monitor service")
            stopService(permissionIntent)
        } else {
            PigeonLogger.debug(TAG, "Missing permissions detected, starting permission monitor service")
            startService(permissionIntent)
        }
    }

    private fun startSmsMonitorService() {
        if (PermissionManager.hasAllPermissions(this)) {
            PigeonLogger.info(TAG, "Starting SMS monitoring service")
            val intent = Intent(this, SmsMonitorService::class.java)
            startForegroundService(intent)
            SmsKeepAliveWorker.enqueue(this)

            // Update service status
            viewModel.setServiceEnabled(true)
            updatePermissionMonitorService()
        } else {
            PigeonLogger.warn(TAG, "Insufficient permissions, cannot start service")
        }
    }

    private fun stopSmsMonitorService() {
        PigeonLogger.info(TAG, "Stopping SMS monitoring service")
        // Route the stop through the service so it can persist the disabled flag before shutting down.
        // Calling stopService() directly leaves a stale "enabled" flag that makes it restart itself.
        startService(
            Intent(this, SmsMonitorService::class.java).apply {
                action = SmsMonitorService.ACTION_STOP_SERVICE
            },
        )
        SmsKeepAliveWorker.cancel(this)

        // Stop permission monitoring service
        PigeonLogger.debug(TAG, "Stopping permission monitoring service")
        val permissionIntent = Intent(this, PermissionMonitorService::class.java)
        stopService(permissionIntent)

        // Update service status
        viewModel.setServiceEnabled(false)
        updatePermissionMonitorService()
    }

    private fun checkServiceStatus() {
        // Use modern method to check service status
        // Since getRunningServices is deprecated, we check notifications to determine if service is running
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val activeNotifications = notificationManager.activeNotifications

        val isServiceRunning =
            activeNotifications.any {
                it.id == SmsMonitorService.NOTIFICATION_ID
            }

        // If service is running but status doesn't match, sync status
        if (isServiceRunning) {
            viewModel.setServiceEnabled(true)
        }
    }

    /**
     * Auto-restore service - automatically start if user previously enabled service and permissions are satisfied
     */
    private fun autoRestoreServiceIfEnabled() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val preferencesRepository = PreferencesRepository(this@MainActivity)
                val serviceEnabled = preferencesRepository.serviceEnabled.first()
                val hasPermissions = PermissionManager.hasAllPermissions(this@MainActivity)

                PigeonLogger.debug(
                    TAG,
                    "Auto-restore check: service enabled=$serviceEnabled, permissions granted=$hasPermissions",
                )

                if (serviceEnabled && hasPermissions) {
                    // Check again if service is already running (avoid duplicate startup)
                    val notificationManager =
                        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    val activeNotifications = notificationManager.activeNotifications
                    val isAlreadyRunning =
                        activeNotifications.any { it.id == SmsMonitorService.NOTIFICATION_ID }

                    if (!isAlreadyRunning) {
                        PigeonLogger.info(
                            TAG,
                            "Auto-restoring service: previously enabled and permissions satisfied",
                        )
                        startSmsMonitorService()
                    } else {
                        PigeonLogger.debug(TAG, "Service already running, no need to restart")
                    }
                } else if (serviceEnabled) {
                    PigeonLogger.warn(
                        TAG,
                        "User enabled service but permissions insufficient, requesting permissions",
                    )
                    // Option to auto-request permissions or just log
                } else {
                    PigeonLogger.debug(TAG, "User did not enable service, no need to auto-restore")
                }
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "Exception occurred during service auto-restore", e)
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
