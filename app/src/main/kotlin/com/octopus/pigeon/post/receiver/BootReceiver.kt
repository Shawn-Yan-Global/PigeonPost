package com.octopus.pigeon.post.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.permission.PermissionManager
import com.octopus.pigeon.post.service.SmsKeepAliveWorker
import com.octopus.pigeon.post.service.SmsMonitorService
import com.octopus.pigeon.post.util.PigeonPostNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Broadcast receiver for handling system boot completion and package updates
 * Automatically restarts SMS monitoring services when appropriate
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                PigeonLogger.info(TAG, "System boot completed, preparing to start services")
                Log.d(TAG, "System boot completed")
                handleBootCompleted(context)
            }

            Intent.ACTION_PACKAGE_REPLACED -> {
                PigeonLogger.info(TAG, "App upgrade completed, preparing to restore services")
                Log.d(TAG, "App upgrade completed")
                handlePackageReplaced(context)
            }

            else -> {
                PigeonLogger.debug(TAG, "Received other broadcast: ${intent.action}")
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize notification channels first to ensure they exist
                PigeonPostNotificationManager.initializeChannels(context)
                PigeonLogger.info(TAG, "Notification channels initialized after boot")

                val preferencesRepository = PreferencesRepository(context)
                val serviceEnabled = preferencesRepository.serviceEnabled.first()

                PigeonLogger.debug(
                    TAG,
                    "Checking service status: $serviceEnabled, permission status: ${PermissionManager.hasAllPermissions(
                        context,
                    )}",
                )

                if (!SmsMonitorService.isAutoRestartAllowed()) {
                    PigeonLogger.warn(TAG, "Auto restart disabled, skipping boot service start")
                    return@launch
                }

                if (serviceEnabled && PermissionManager.hasAllPermissions(context)) {
                    PigeonLogger.info(TAG, "Service enabled and permissions granted, starting services")
                    Log.d(TAG, "Starting SMS monitoring service")
                    startServices(context)

                    // Arm the WorkManager safety net that restores the foreground service if killed
                    SmsKeepAliveWorker.enqueue(context)
                    PigeonLogger.info(TAG, "Keep-alive worker scheduled for service restoration")
                } else {
                    PigeonLogger.warn(TAG, "Service not enabled or insufficient permissions, skipping startup")
                    Log.d(TAG, "Service not enabled or insufficient permissions")
                }
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "Failed to start services", e)
                Log.e(TAG, "Failed to start services", e)
            }
        }
    }

    private fun handlePackageReplaced(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize notification channels first to ensure they exist
                PigeonPostNotificationManager.initializeChannels(context)
                PigeonLogger.info(TAG, "Notification channels initialized after app upgrade")

                val preferencesRepository = PreferencesRepository(context)
                val serviceEnabled = preferencesRepository.serviceEnabled.first()

                PigeonLogger.debug(
                    TAG,
                    "Post-upgrade service status check: $serviceEnabled, permission status: ${PermissionManager.hasAllPermissions(
                        context,
                    )}",
                )

                if (!SmsMonitorService.isAutoRestartAllowed()) {
                    PigeonLogger.warn(TAG, "Auto restart disabled, skipping upgrade service start")
                    return@launch
                }

                if (serviceEnabled && PermissionManager.hasAllPermissions(context)) {
                    PigeonLogger.info(TAG, "Restoring services after app upgrade")
                    Log.d(TAG, "Restoring services after app upgrade")
                    startServices(context)

                    // Arm the WorkManager safety net that restores the foreground service if killed
                    SmsKeepAliveWorker.enqueue(context)
                    PigeonLogger.info(TAG, "Keep-alive worker rescheduled after app upgrade")
                } else {
                    PigeonLogger.warn(TAG, "Service not enabled or insufficient permissions, skipping restoration")
                    Log.d(TAG, "Service not enabled or insufficient permissions, skipping restoration")
                }
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "Failed to restore services", e)
                Log.e(TAG, "Failed to restore services", e)
            }
        }
    }

    private fun startServices(context: Context) {
        try {
            if (!SmsMonitorService.isAutoRestartAllowed()) {
                PigeonLogger.warn(TAG, "Auto restart disabled, not starting SmsMonitorService")
                return
            }
            val smsIntent = Intent(context, SmsMonitorService::class.java)
            context.startForegroundService(smsIntent)
            PigeonLogger.info(TAG, "SMS monitoring service started successfully")
            PigeonLogger.info(TAG, "All services started successfully")
            Log.d(TAG, "All services started successfully")
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Exception occurred while starting services", e)
            Log.e(TAG, "Exception occurred while starting services", e)
        }
    }
}
