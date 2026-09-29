package com.octopus.pigeon.post.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.permission.PermissionManager
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background safety net for [SmsMonitorService].
 *
 * The lightweight silent foreground service is the primary mechanism that keeps the app out of the
 * `RESTRICTED` app standby bucket, so SMS is still delivered on day 2, day 10 and beyond. This worker
 * only exists to bring that service back when a cleaner or the system kills it, because WorkManager
 * is rescheduled by the platform and therefore survives a process death without an exact alarm.
 *
 * It is intentionally cheap: a periodic job that first inspects the current state and only touches
 * the service when the foreground notification is actually gone.
 */
class SmsKeepAliveWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(
        appContext,
        params,
    ) {
    companion object {
        private const val TAG = "SmsKeepAliveWorker"

        private const val PERIODIC_WORK_NAME = "pigeon_post_sms_keep_alive"
        private const val IMMEDIATE_WORK_NAME = "pigeon_post_sms_keep_alive_immediate"

        /** WorkManager enforces a 15 minute floor for periodic work, which is also the OEM friendly cadence. */
        private const val CHECK_INTERVAL_MINUTES = 15L

        /**
         * Enqueue the periodic safety net. Safe to call from any component: the work is unique, so
         * repeated calls collapse into the already scheduled request instead of piling up jobs.
         */
        fun enqueue(context: Context) {
            try {
                val request =
                    PeriodicWorkRequestBuilder<SmsKeepAliveWorker>(CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
                        .setConstraints(Constraints.NONE)
                        .setBackoffCriteria(
                            BackoffPolicy.LINEAR,
                            1,
                            TimeUnit.MINUTES,
                        ).build()

                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)

                PigeonLogger.info(
                    TAG,
                    "Keep-alive worker scheduled, checking every $CHECK_INTERVAL_MINUTES minutes",
                )
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "Failed to schedule keep-alive worker", e)
            }
        }

        /**
         * Ask for an out-of-band check as soon as the platform allows it, e.g. right after the task is
         * swiped away. Runs outside the quota so the request is not dropped when several are queued.
         */
        fun enqueueImmediate(context: Context) {
            try {
                val request =
                    OneTimeWorkRequestBuilder<SmsKeepAliveWorker>()
                        .setConstraints(Constraints.NONE)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()

                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)

                PigeonLogger.info(TAG, "Immediate keep-alive check requested")
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "Failed to request immediate keep-alive check", e)
            }
        }

        /** Stop the safety net, used when the user turns SMS monitoring off. */
        fun cancel(context: Context) {
            try {
                val workManager = WorkManager.getInstance(context)
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
                PigeonLogger.info(TAG, "Keep-alive worker canceled")
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "Failed to cancel keep-alive worker", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext

        if (!PreferencesRepository(context).serviceEnabled.first()) {
            PigeonLogger.debug(TAG, "Service disabled by user, releasing keep-alive worker")
            cancel(context)
            return Result.success()
        }

        if (!PermissionManager.hasAllPermissions(context)) {
            PigeonLogger.warn(TAG, "Required permissions missing, cannot restore foreground service")
            return Result.success()
        }

        if (isMonitorServiceRunning()) {
            PigeonLogger.debug(TAG, "Foreground service still alive, nothing to do")
            // Make sure the periodic net stays armed while the service is healthy.
            enqueue(context)
            return Result.success()
        }

        PigeonLogger.warn(TAG, "Foreground service was killed, restoring it")
        return if (startMonitorService(context)) {
            Result.success()
        } else {
            // Android 12+ refuses foreground starts from the background. Let WorkManager retry later
            // instead of spinning, the periodic request is already queued.
            Result.retry()
        }
    }

    private fun isMonitorServiceRunning(): Boolean {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        return notificationManager.activeNotifications.any { it.id == SmsMonitorService.NOTIFICATION_ID }
    }

    private fun startMonitorService(context: Context): Boolean {
        if (!SmsMonitorService.isAutoRestartAllowed()) {
            PigeonLogger.warn(TAG, "Auto restart disabled, skipping restore")
            return false
        }

        return try {
            context.startForegroundService(Intent(context, SmsMonitorService::class.java))
            PigeonLogger.info(TAG, "Restore command sent to SMS monitoring service")
            true
        } catch (e: Exception) {
            if (e.javaClass.simpleName == "ForegroundServiceStartNotAllowedException") {
                PigeonLogger.warn(TAG, "Background foreground-service start not allowed, will retry later")
            } else {
                PigeonLogger.error(TAG, "Failed to restore SMS monitoring service", e)
            }
            false
        }
    }
}
