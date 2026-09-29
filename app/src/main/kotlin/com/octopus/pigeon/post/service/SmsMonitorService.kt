package com.octopus.pigeon.post.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.receiver.SmsReceiver
import com.octopus.pigeon.post.ui.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.jvm.Volatile
import kotlin.time.Duration.Companion.minutes

/**
 * Lightweight silent foreground service that keeps the app out of the `RESTRICTED` app standby bucket.
 *
 * A foreground service is a "the app is in use" signal for the platform, which is what stops Android 9+
 * and the Chinese OEM ROMs from freezing the process after 24 hours of inactivity. Without it, day 2
 * onwards the app is downgraded to `RARE`/`RESTRICTED`, broadcasts stop being delivered and SMS is lost.
 *
 * The service itself does no work while idle: it holds no wake lock and only wakes up every
 * [HEALTH_CHECK_INTERVAL] to verify the foreground notification and the dynamic receiver are still in
 * place, so a quiet day costs a few CPU wakeups and no measurable battery.
 *
 * [SmsKeepAliveWorker] is the safety net that restores this service if a cleaner kills it.
 */
class SmsMonitorService : Service() {
    private val TAG = SmsMonitorService::class.java.simpleName

    companion object {
        const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "sms_monitor_channel"
        const val ACTION_STOP_SERVICE = "com.octopus.pigeon.post.STOP_SERVICE"
        const val ACTION_RESTART_SERVICE = "com.octopus.pigeon.post.RESTART_SERVICE"

        /**
         * Health check cadence. Also the notification refresh cadence, chosen to stay well above the
         * WorkManager floor of 15 minutes so the service only wakes the CPU four times per hour.
         */
        private val HEALTH_CHECK_INTERVAL = 15.minutes

        @Volatile
        private var autoRestartAllowed = true

        fun isAutoRestartAllowed(): Boolean = autoRestartAllowed

        private fun allowAutoRestart() {
            autoRestartAllowed = true
        }

        private fun blockAutoRestart() {
            if (!autoRestartAllowed) {
                return
            }
            autoRestartAllowed = false
            PigeonLogger.warn(
                SmsMonitorService::class.java.simpleName,
                "🚫 Auto restart disabled: startForeground not allowed in background",
            )
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Separate from [serviceScope] so the restart decision made in [onDestroy] can still run after
     * the service scope has been cancelled. It never touches UI, so blocking is not required.
     */
    private val destroyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var restartOnDestroy = true
    private lateinit var preferencesRepository: PreferencesRepository
    private var healthCheckJob: Job? = null

    /**
     * Notification text is read synchronously by [startForeground], which must not block on DataStore.
     * The health check loop keeps this cache fresh.
     */
    @Volatile
    private var cachedStatusText: String? = null

    /** Last status text pushed to the notification, so we only post when it actually changes. */
    private var lastNotifiedStatusText: String? = null

    // Dynamically registered SmsReceiver - improve compatibility
    private val dynamicSmsReceiver = SmsReceiver()
    private var isReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        PigeonLogger.info(TAG, "📱 SMS monitoring service created")

        // Initialize preferences repository first
        preferencesRepository = PreferencesRepository(this)

        // Register dynamic SMS receiver IMMEDIATELY for fastest response
        // This is the most critical step for SMS monitoring
        registerDynamicSmsReceiver()
        PigeonLogger.info(TAG, "🚀 Dynamic SMS receiver registered immediately")

        // Then do other initialization
        createNotificationChannel()
        startHealthCheck()

        // Arm the WorkManager safety net that restores this service if it gets killed
        SmsKeepAliveWorker.enqueue(this)

        PigeonLogger.info(
            TAG,
            "Using both static and dynamic registration to improve compatibility, database prevents duplicates",
        )
        PigeonLogger.info(TAG, "✅ Service initialization completed with keep-alive worker scheduled")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        restartOnDestroy = true
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                restartOnDestroy = false
                serviceScope.launch {
                    preferencesRepository.setServiceEnabled(false)
                    // Only stop once the flag is persisted, otherwise onDestroy cancels the write and the
                    // keep-alive worker still sees the service as enabled and brings it straight back.
                    SmsKeepAliveWorker.cancel(applicationContext)
                    stopSelf()
                }
                return START_NOT_STICKY
            }

            ACTION_RESTART_SERVICE -> {
                PigeonLogger.info(TAG, "Received service restart request")
                // Re-register dynamic SmsReceiver
                unregisterDynamicSmsReceiver()
                registerDynamicSmsReceiver()
                PigeonLogger.info(
                    TAG,
                    "Service restart completed, re-registered dynamic SmsReceiver",
                )
            }
        }

        // Try to start as foreground service with proper exception handling
        try {
            val notification = createNotification()
            PigeonLogger.debug(
                TAG,
                "Starting foreground service, notification ID: $NOTIFICATION_ID",
            )
            startForeground(NOTIFICATION_ID, notification)
            PigeonLogger.info(TAG, "✅ Foreground service started successfully")
            allowAutoRestart()
            SmsKeepAliveWorker.enqueue(this)
        } catch (e: Exception) {
            // Handle ForegroundServiceStartNotAllowedException on Android 12+
            when (e.javaClass.simpleName) {
                "ForegroundServiceStartNotAllowedException" -> {
                    PigeonLogger.error(TAG, "⚠️ Cannot start as foreground service: ${e.message}")
                    PigeonLogger.error(
                        TAG,
                        "This is an Android 12+ restriction when app is in background",
                    )
                    // CRITICAL FIX: Must stop service to prevent ForegroundServiceDidNotStopInTimeException crash
                    // Since we were started with startForegroundService, we MUST go foreground or stop.
                    PigeonLogger.warn(TAG, "🛑 Stopping service to prevent ANR/Crash")
                    blockAutoRestart()
                    restartOnDestroy = false
                    stopSelf()
                    return START_NOT_STICKY
                }

                else -> {
                    PigeonLogger.error(TAG, "Failed to start foreground service", e)
                    throw e // Re-throw other exceptions
                }
            }
        }

        // Ensure service runs persistently, restart even if killed by system
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterDynamicSmsReceiver()
        healthCheckJob?.cancel()
        serviceScope.cancel()
        PigeonLogger.warn(
            TAG,
            "⚠️ SMS monitoring service destroyed, canceling dynamic registration",
        )

        // Attempt to restart service if it was not intentionally stopped
        destroyScope.launch {
            try {
                val serviceEnabled = preferencesRepository.serviceEnabled.first()
                if (serviceEnabled && restartOnDestroy && isAutoRestartAllowed()) {
                    PigeonLogger.info(TAG, "🔄 Service was enabled, attempting to restart...")
                    startMonitorService()
                } else {
                    PigeonLogger.info(
                        TAG,
                        "🛑 Skipping auto-restart (serviceEnabled=$serviceEnabled restartOnDestroy=$restartOnDestroy)",
                    )
                }
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "Failed to restart service", e)
            } finally {
                destroyScope.cancel()
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        PigeonLogger.warn(TAG, "⚠️ Task removed (app swiped from recents)")

        // The foreground service normally survives this, but ask the platform to double check as soon
        // as it is allowed to, instead of relying on an exact alarm.
        destroyScope.launch {
            try {
                val serviceEnabled = preferencesRepository.serviceEnabled.first()
                if (serviceEnabled && isAutoRestartAllowed()) {
                    PigeonLogger.info(TAG, "🔄 Requesting keep-alive check after task removal")
                    SmsKeepAliveWorker.enqueueImmediate(applicationContext)
                }
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "Failed to request keep-alive check", e)
            }
        }
    }

    private fun createNotificationChannel() {
        PigeonLogger.debug(TAG, "Creating notification channel")
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sms_monitor_service),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.sms_monitor_service_desc)
                setShowBadge(false)
                // Set to non-bypassable silent mode
                setBypassDnd(false)
                // Allow lockscreen display
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Don't allow users to close this notification
                setImportance(NotificationManager.IMPORTANCE_LOW)
            }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        PigeonLogger.debug(
            TAG,
            "Notification channel created, optimized background survival capability",
        )
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

        val stopIntent =
            Intent(this, SmsMonitorService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val statusText = cachedStatusText ?: getString(R.string.monitoring_sms)

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.pigeon_sms_listener))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.stop_listening),
                stopPendingIntent,
            ).setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // 增强后台生存能力的配置
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            // 防止在低内存时被清理
            .setAutoCancel(false)
            .build()
    }

    /**
     * Single low-frequency loop that keeps the notification status fresh and verifies the two things
     * that silently break SMS reception: the foreground notification and the dynamic receiver.
     */
    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob =
            serviceScope.launch {
                while (isActive) {
                    try {
                        refreshStatusText()

                        // Check foreground service status
                        val notificationManager =
                            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        val isForegroundActive =
                            notificationManager.activeNotifications.any { it.id == NOTIFICATION_ID }

                        if (!isForegroundActive) {
                            PigeonLogger.warn(
                                TAG,
                                "Detected foreground service notification inactive, attempting restart",
                            )
                            try {
                                startForeground(NOTIFICATION_ID, createNotification())
                            } catch (e: Exception) {
                                PigeonLogger.error(TAG, "Failed to restart foreground in health check", e)
                                // If we can't restart foreground, we are likely in background state restricted
                                // We don't need stopSelf() here because we weren't started via startForegroundService just now
                            }
                        }

                        // Check dynamic registration status
                        if (!isReceiverRegistered) {
                            PigeonLogger.warn(
                                TAG,
                                "Detected dynamic SmsReceiver not registered, attempting re-registration",
                            )
                            registerDynamicSmsReceiver()
                        }

                        // Only touch the notification when the displayed status actually changed
                        val statusText = cachedStatusText
                        if (statusText != lastNotifiedStatusText) {
                            lastNotifiedStatusText = statusText
                            updateNotification()
                        }

                        // Keep the safety net armed even if the app process survived without WorkManager state
                        SmsKeepAliveWorker.enqueue(this@SmsMonitorService)

                        delay(HEALTH_CHECK_INTERVAL)
                    } catch (e: Exception) {
                        PigeonLogger.error(TAG, "Health check error", e)
                        delay(HEALTH_CHECK_INTERVAL)
                    }
                }
            }
    }

    /**
     * Recompute the notification status from the active time configuration. Runs off the main thread
     * so the result can be cached instead of blocking [startForeground].
     */
    private suspend fun refreshStatusText() {
        val activeTimeConfig = preferencesRepository.activeTimeConfig.first()
        cachedStatusText =
            if (!activeTimeConfig.isEnabled) {
                PigeonLogger.debug(TAG, "No active time restriction configured, monitoring SMS all day")
                getString(R.string.monitoring_sms)
            } else if (activeTimeConfig.isInActiveTime()) {
                PigeonLogger.debug(TAG, "Currently in active period, monitoring SMS normally")
                getString(R.string.active_period_monitoring)
            } else {
                PigeonLogger.info(TAG, "Currently not in active period, but service remains running")
                getString(R.string.inactive_period_low_power)
            }
    }

    private fun updateNotification() {
        try {
            val notification = createNotification()
            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Try to update as foreground service notification
            // On Android 12+, this may fail if app is in background for too long
            try {
                startForeground(NOTIFICATION_ID, notification)
                PigeonLogger.debug(TAG, "Notification updated successfully via startForeground")
            } catch (e: Exception) {
                // Handle ForegroundServiceStartNotAllowedException on Android 12+
                if (e.javaClass.simpleName == "ForegroundServiceStartNotAllowedException") {
                    // Fallback: use regular notification update instead
                    PigeonLogger.warn(
                        TAG,
                        "⚠️ Cannot update as foreground service notification, using regular notification",
                    )
                    notificationManager.notify(NOTIFICATION_ID, notification)
                    PigeonLogger.debug(
                        TAG,
                        "Notification updated successfully via NotificationManager",
                    )
                } else {
                    // For other exceptions, try notification manager as fallback
                    PigeonLogger.warn(
                        TAG,
                        "Failed to update via startForeground, falling back to NotificationManager",
                    )
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Failed to update notification", e)
        }
    }

    /**
     * Start or restart this service, tolerating the Android 12+ background restriction.
     * Shared by [onDestroy] and the health check path.
     */
    private fun startMonitorService(): Boolean =
        try {
            startForegroundService(Intent(this, SmsMonitorService::class.java))
            PigeonLogger.info(TAG, "✅ SMS monitoring service start command sent")
            true
        } catch (e: Exception) {
            if (e.javaClass.simpleName == "ForegroundServiceStartNotAllowedException") {
                PigeonLogger.warn(
                    TAG,
                    "⚠️ Cannot start foreground service from background (Android 12+ restriction)",
                )
            } else {
                PigeonLogger.error(TAG, "Failed to start SMS monitoring service", e)
            }
            false
        }

    /**
     * Dynamically register SmsReceiver - enhance compatibility
     * Combined with static registration to ensure SMS reception on various Android versions and devices
     * CRITICAL: This must be called as early as possible for fastest SMS response
     */
    private fun registerDynamicSmsReceiver() {
        try {
            if (!isReceiverRegistered) {
                val intentFilter =
                    IntentFilter().apply {
                        addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
                        // Use high priority to ensure quick response
                        priority = 999 // High priority for dynamic registration
                    }

                // Register immediately with RECEIVER_NOT_EXPORTED for security
                registerReceiver(
                    dynamicSmsReceiver,
                    intentFilter,
                    RECEIVER_NOT_EXPORTED,
                )

                isReceiverRegistered = true
                PigeonLogger.info(
                    TAG,
                    "🔄 Dynamic SmsReceiver registration successful (priority 999, immediate)",
                )
            } else {
                PigeonLogger.debug(TAG, "Dynamic SmsReceiver already registered, skipping")
            }
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "❌ Dynamic SmsReceiver registration failed", e)
        }
    }

    /**
     * Unregister dynamic SmsReceiver
     */
    private fun unregisterDynamicSmsReceiver() {
        try {
            if (isReceiverRegistered) {
                unregisterReceiver(dynamicSmsReceiver)
                isReceiverRegistered = false
                PigeonLogger.info(TAG, "🚫 Dynamic SmsReceiver unregistration successful")
            }
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Dynamic SmsReceiver unregistration failed", e)
        }
    }
}
