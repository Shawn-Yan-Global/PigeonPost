package com.octopus.pigeon.post.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.octopus.logging.PigeonLogRedactor
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.database.AppDatabase
import com.octopus.pigeon.post.data.model.SmsRecord
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.data.repository.SmsRepository
import com.octopus.pigeon.post.service.EmailService
import com.octopus.pigeon.post.service.SmsKeepAliveWorker
import com.octopus.pigeon.post.service.SmsMatchingService
import com.octopus.pigeon.post.service.SmsMonitorService
import com.octopus.pigeon.post.util.PigeonPostNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Broadcast receiver for handling incoming SMS messages
 * Processes SMS filtering, forwarding, and notifications
 */
class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"

        // Short-term cache to prevent duplicate processing from dual registration
        // key: sender + content + timestamp(milliseconds), value: processing time
        private val recentSmsCache = ConcurrentHashMap<String, Long>()
        private const val CACHE_EXPIRE_MS = 5 * 1000L // 5 seconds expiration - short enough to allow retesting, long enough to prevent duplicates

        /**
         * Get current cache status (for debugging)
         */
        fun getCacheStatus(): Int {
            cleanExpiredCache()
            return recentSmsCache.size
        }

        /**
         * Generate unique identifier key for SMS
         * Uses full content comparison - not just hashCode to avoid hash collisions
         */
        private fun generateSmsKey(
            sender: String,
            content: String,
            receivedTime: LocalDateTime,
        ): String {
            // Use second-level timestamp to group SMS arriving in the same second
            val timeSecond = receivedTime.toEpochSecond(java.time.ZoneOffset.UTC)
            // Use full content instead of hashCode to ensure accurate duplicate detection
            // Format: sender|content|timestamp
            return "$sender|$content|$timeSecond"
        }

        /**
         * Check if SMS is truly identical by comparing sender and content with cached entries
         * Returns true if sender AND content match exactly
         */
        private fun isTrueDuplicate(
            sender: String,
            content: String,
        ): Boolean {
            val now = System.currentTimeMillis()

            return recentSmsCache.entries.any { entry ->
                val cachedKey = entry.key
                val cachedTime = entry.value

                // Check if it's within cache expire time
                if (now - cachedTime > CACHE_EXPIRE_MS) {
                    return@any false
                }

                // Parse cached key
                val parts = cachedKey.split("|", limit = 3)
                if (parts.size >= 3) {
                    val cachedSender = parts[0]
                    val cachedContent = parts[1]
                    // Double confirm: sender and content must match exactly
                    cachedSender == sender && cachedContent == content
                } else {
                    false
                }
            }
        }

        /**
         * Clean expired cache entries
         */
        private fun cleanExpiredCache() {
            val now = System.currentTimeMillis()
            val iterator = recentSmsCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > CACHE_EXPIRE_MS) {
                    iterator.remove()
                }
            }
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        PigeonLogger.info(TAG, "🔔 SmsReceiver.onReceive() called - App woken by SMS!")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            PigeonLogger.warn(TAG, "Received non-SMS action: ${intent.action}")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            PigeonLogger.warn(TAG, "No SMS messages found in intent")
            return
        }

        val smsMessage = messages[0]
        val sender = smsMessage.originatingAddress ?: "Unknown number"
        val content = smsMessage.messageBody ?: ""
        val receivedTime = LocalDateTime.now()

        Log.d(TAG, "SMS received: sender=${PigeonLogRedactor.tag(sender)}, length=${content.length}")
        PigeonLogger.info(TAG, "📱 SMS received from ${PigeonLogRedactor.tag(sender)}")
        PigeonLogger.debug(
            TAG,
            "📱 SMS content digest: ${PigeonLogRedactor.contentTag(content)}",
        )

        // Critical: Ensure services are running when SMS arrives
        // This helps wake up the app even if it was killed
        try {
            val serviceIntent = Intent(context, SmsMonitorService::class.java)

            try {
                context.startForegroundService(serviceIntent)
                PigeonLogger.info(TAG, "🚀 Started SmsMonitorService from SMS broadcast")
            } catch (e: Exception) {
                // Handle Android 12+ ForegroundServiceStartNotAllowedException
                if (e.javaClass.simpleName == "ForegroundServiceStartNotAllowedException") {
                    PigeonLogger.warn(TAG, "⚠️ Cannot start foreground service: ${e.message}")
                    PigeonLogger.warn(TAG, "SMS will still be processed, service will start when possible")
                    // SMS processing will continue even without foreground service
                } else {
                    PigeonLogger.error(TAG, "Failed to start service from SMS broadcast", e)
                }
            }

            // Keep the WorkManager safety net armed so the foreground service survives a later kill
            SmsKeepAliveWorker.enqueue(context)
            PigeonLogger.info(TAG, "⚙️ Keep-alive worker armed from SMS broadcast")
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Failed to arm keep-alive worker from SMS broadcast", e)
        }

        // Duplicate check: generate unique SMS identifier
        val smsKey = generateSmsKey(sender, content, receivedTime)
        val now = System.currentTimeMillis()

        // Clean expired cache
        cleanExpiredCache()

        // Check if it's a true duplicate using isTrueDuplicate method
        if (isTrueDuplicate(sender, content)) {
            PigeonLogger.warn(TAG, "🚫 True duplicate SMS detected (sender & content match exactly)")
            PigeonLogger.warn(
                TAG,
                "   Sender: ${PigeonLogRedactor.tag(sender)}, Content length: ${content.length}",
            )
            PigeonLogger.warn(TAG, "   Skipping processing and NOT saving to database")
            return
        }

        // Record to cache
        recentSmsCache[smsKey] = now
        PigeonLogger.info(TAG, "✅ SMS passed duplicate check (cache size: ${recentSmsCache.size})")
        PigeonLogger.debug(
            TAG,
            "   Sender: ${PigeonLogRedactor.tag(sender)}, Content length: ${content.length}",
        )

        // 立即发送短信接收通知
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val preferencesRepository = PreferencesRepository(context)
                val notificationEnabled = preferencesRepository.smsReceivedNotificationEnabled.first()
                val serviceEnabled = preferencesRepository.serviceEnabled.first()

                PigeonLogger.info(TAG, "📱 Checking SMS received notification settings:")
                PigeonLogger.info(TAG, "   - Notification enabled: $notificationEnabled")
                PigeonLogger.info(TAG, "   - Service enabled: $serviceEnabled")

                if (notificationEnabled) {
                    PigeonLogger.info(
                        TAG,
                        "📬 Showing SMS received notification for sender: " +
                            PigeonLogRedactor.tag(sender),
                    )
                    PigeonPostNotificationManager.showSmsReceivedNotification(
                        context = context,
                        sender = sender,
                        contentPreview = content,
                        isServiceRunning = serviceEnabled,
                    )
                    PigeonLogger.info(TAG, "✅ SMS received notification posted successfully")
                } else {
                    PigeonLogger.warn(
                        TAG,
                        "⚠️ SMS received notification is DISABLED in settings, skipping notification",
                    )
                }
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "❌ Failed to send SMS received notification", e)
            }
        }

        // 在后台处理短信
        CoroutineScope(Dispatchers.IO).launch {
            processSms(context, sender, content, receivedTime)
        }
    }

    private suspend fun processSms(
        context: Context,
        sender: String,
        content: String,
        receivedTime: LocalDateTime,
    ) {
        try {
            PigeonLogger.info(
                TAG,
                "🔄 Starting SMS processing: sender=${PigeonLogRedactor.tag(sender)}",
            )
            val database = AppDatabase.getDatabase(context)
            val smsRepository = SmsRepository(database.smsRecordDao())
            val preferencesRepository = PreferencesRepository(context)
            val emailService = EmailService(context)
            val smsMatchingService = SmsMatchingService()

            // Save SMS record
            val smsRecord =
                SmsRecord(
                    sender = sender,
                    content = content,
                    receivedTime = receivedTime,
                )
            val recordId = smsRepository.insertSmsRecord(smsRecord)

            // If record is duplicate (recordId is -1), skip subsequent processing
            if (recordId == -1L) {
                PigeonLogger.info(TAG, "Duplicate SMS detected, skipping processing")
                return
            }

            // Check if service is enabled
            val serviceEnabled = preferencesRepository.serviceEnabled.first()
            if (!serviceEnabled) {
                Log.d(TAG, "Service not enabled, skipping processing")
                PigeonLogger.debug(TAG, "Service not enabled, skipping SMS processing")
                return
            }

            PigeonLogger.debug(TAG, "Starting SMS processing, record ID: $recordId")

            // Get active time configuration and check if in active period
            val activeTimeConfig = preferencesRepository.activeTimeConfig.first()
            val isInActiveTime = activeTimeConfig.isInActiveTime()
            PigeonLogger.debug(
                TAG,
                "⏰ Active time check: enabled=${activeTimeConfig.isEnabled}, currently in active period=$isInActiveTime",
            )

            if (!isInActiveTime) {
                PigeonLogger.info(TAG, "❌ Currently not in active period, skipping SMS processing")
                return
            }

            PigeonLogger.debug(TAG, "✅ Active time check passed, continuing SMS processing")

            // Get template configuration and no-filter forwarding mode
            val templateConfig = preferencesRepository.templateConfig.first()
            val noFilterMode = preferencesRepository.noFilterForwardMode.first()

            val shouldForward: Boolean
            var matchedKeyword: String? = null

            if (noFilterMode) {
                // No-filter full forwarding mode - forward all SMS directly
                shouldForward = true
                PigeonLogger.info(TAG, "🚀 No-filter mode: all SMS will be forwarded")
            } else {
                // Check if matches template
                shouldForward = smsMatchingService.isSmsMatchTemplate(content, templateConfig)
                if (shouldForward) {
                    // Find matched keyword (simplified implementation)
                    matchedKeyword =
                        templateConfig.keywords.find { keyword ->
                            if (templateConfig.caseSensitive) {
                                content.contains(keyword)
                            } else {
                                content.contains(keyword, ignoreCase = true)
                            }
                        }
                    PigeonLogger.info(TAG, "✅ SMS matched template keyword: $matchedKeyword")
                } else {
                    PigeonLogger.info(TAG, "❌ SMS does not match template, skipping forwarding")
                }
            }

            val forwardNotificationEnabled = preferencesRepository.smsForwardedNotificationEnabled.first()
            val smsReceivedNotificationEnabled = preferencesRepository.smsReceivedNotificationEnabled.first()

            if (!shouldForward) {
                // Update error message but don't mark as forwarded
                smsRepository.updateErrorMessage(
                    id = recordId,
                    errorMessage = context.getString(R.string.sms_not_match_filter),
                )
                PigeonLogger.info(TAG, "❌ SMS does not match filter rules, will NOT forward")
                if (smsReceivedNotificationEnabled) {
                    withContext(Dispatchers.Main) {
                        try {
                            PigeonPostNotificationManager.showSmsReceivedNotification(
                                context = context,
                                sender = sender,
                                contentPreview = content,
                                isServiceRunning = true,
                                isEnabled = true,
                                statusOverride = context.getString(R.string.status_forward_failed),
                            )
                            PigeonLogger.info(TAG, "📢 Updated SMS received notification to show forward failure status")
                        } catch (notifError: Exception) {
                            PigeonLogger.error(TAG, "❌ Failed to update SMS received notification", notifError)
                        }
                    }
                }

                if (forwardNotificationEnabled) {
                    withContext(Dispatchers.Main) {
                        try {
                            PigeonPostNotificationManager.showForwardFailureNotification(
                                context = context,
                                sender = sender,
                                errorMessage = context.getString(R.string.sms_not_match_filter),
                                isEnabled = true,
                            )
                            PigeonLogger.info(
                                TAG,
                                "📧 Posted forward failure notification for sender: " +
                                    PigeonLogRedactor.tag(sender),
                            )
                        } catch (notifError: Exception) {
                            PigeonLogger.error(TAG, "❌ Failed to show forward failure notification", notifError)
                        }
                    }
                }
                return
            }

            // Get email configuration
            val emailConfig = preferencesRepository.emailConfig.first()

            // Send email
            val emailResult = emailService.sendSmsEmail(emailConfig, smsRecord, templateConfig)

            // Update forwarding status
            smsRepository.updateForwardStatus(
                id = recordId,
                success = emailResult.isSuccess,
                errorMessage = emailResult.exceptionOrNull()?.message,
            )

            // Send forwarding result notification - Switch to Main dispatcher for UI operations
            PigeonLogger.debug(
                TAG,
                "📋 Forward notification enabled: $forwardNotificationEnabled, emailResult.isSuccess: ${emailResult.isSuccess}",
            )

            if (forwardNotificationEnabled) {
                // Use Main dispatcher to ensure notification is shown on UI thread
                withContext(Dispatchers.Main) {
                    try {
                        if (emailResult.isSuccess) {
                            PigeonLogger.info(TAG, "✅ SMS forwarding successful, showing success notification")
                            PigeonPostNotificationManager.showForwardSuccessNotification(
                                context = context,
                                sender = sender,
                                templateKeyword = matchedKeyword,
                                isEnabled = true,
                            )
                            PigeonLogger.info(
                                TAG,
                                "📧 Success notification posted to NotificationManager for sender: $sender",
                            )
                        } else {
                            val errorMsg = emailResult.exceptionOrNull()?.message ?: "Unknown error"
                            PigeonLogger.error(TAG, "❌ Email sending failed: $errorMsg")
                            PigeonPostNotificationManager.showForwardFailureNotification(
                                context = context,
                                sender = sender,
                                errorMessage = errorMsg,
                                isEnabled = true,
                            )
                            PigeonLogger.info(
                                TAG,
                                "📧 Failure notification posted to NotificationManager for sender: $sender",
                            )
                        }
                    } catch (notifError: Exception) {
                        PigeonLogger.error(TAG, "❌ Failed to show forward notification", notifError)
                    }
                }
            } else {
                PigeonLogger.info(TAG, "⚠️ Forward notification is disabled in settings, skipping notification")
            }
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Error occurred while processing SMS", e)
        }
    }
}
