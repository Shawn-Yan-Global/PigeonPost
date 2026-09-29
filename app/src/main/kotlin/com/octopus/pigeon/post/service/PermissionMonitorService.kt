package com.octopus.pigeon.post.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.permission.PermissionManager
import com.octopus.pigeon.post.ui.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Permission monitoring service - monitors SMS permission status
 * Shows silent notification when permissions are revoked and provides a single short haptic prompt
 */
class PermissionMonitorService : Service() {
    companion object {
        private const val TAG = "PermissionMonitorService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "permission_monitor_channel"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var permissionCheckJob: Job? = null
    private var isAlertShown = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startPermissionMonitoring()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (PermissionManager.hasAllPermissions(this)) {
            PigeonLogger.info(TAG, "All permissions granted, stopping PermissionMonitorService")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        startPermissionMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        permissionCheckJob?.cancel()
        serviceScope.cancel()
    }

    private fun createNotification(): Notification {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.permission_monitor_service))
            .setContentText(getString(R.string.permission_monitor_service_desc))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.permission_monitor_service),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.permission_monitor_service_desc)
                enableVibration(false)
                enableLights(false)
            }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startPermissionMonitoring() {
        permissionCheckJob?.cancel()
        permissionCheckJob =
            serviceScope.launch {
                while (isActive) {
                    try {
                        val hasPermissions = PermissionManager.hasAllPermissions(this@PermissionMonitorService)
                        if (!hasPermissions) {
                            if (!isAlertShown) {
                                showPermissionAlert()
                                vibrateOnce()
                                isAlertShown = true
                            }
                        } else {
                            hidePermissionAlert()
                            isAlertShown = false
                            PigeonLogger.info(
                                TAG,
                                "Permissions restored, stopping PermissionMonitorService",
                            )
                            stopSelf()
                            break
                        }

                        delay(60000) // Check every 60 seconds
                    } catch (e: Exception) {
                        PigeonLogger.error(
                            TAG,
                            "Permission monitoring error",
                            e,
                        )
                        delay(60000)
                    }
                }
            }
    }

    private fun showPermissionAlert() {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.permission_disabled_title))
                .setContentText(getString(R.string.permission_disabled_desc))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun hidePermissionAlert() {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Single, short (150ms) haptic feedback pulse when missing permissions are detected for the first time
     */
    private fun vibrateOnce() {
        try {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Failed to perform single vibration prompt", e)
        }
    }
}
