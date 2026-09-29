package com.octopus.pigeon.post.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.octopus.logging.PigeonLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Handles user triggered cleanups of forwarded records and log files.
 *
 * The recurring part of the cleanup lives in [CleanupWorker], which WorkManager keeps alive across
 * process death and reboots. This service only reacts to explicit one-off requests, so it has no
 * long running loop of its own and is free to stop as soon as the requested work is done.
 */
class CleanupService : Service() {
    companion object {
        private const val TAG = "CleanupService"

        const val ACTION_CLEANUP_RECORDS = "com.octopus.pigeon.post.CLEANUP_RECORDS"
        const val ACTION_CLEANUP_LOGS = "com.octopus.pigeon.post.CLEANUP_LOGS"
        const val ACTION_CLEANUP_ALL = "com.octopus.pigeon.post.CLEANUP_ALL"
        const val EXTRA_CLEANUP_DAYS = "cleanup_days"

        private const val DEFAULT_CLEANUP_DAYS = 7
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        PigeonLogger.info(TAG, "Cleanup service started")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val days = intent?.getIntExtra(EXTRA_CLEANUP_DAYS, DEFAULT_CLEANUP_DAYS) ?: DEFAULT_CLEANUP_DAYS

        when (intent?.action) {
            ACTION_CLEANUP_RECORDS ->
                serviceScope.launchCleanup(startId) {
                    CleanupActions.cleanupRecords(this@CleanupService, days)
                }

            ACTION_CLEANUP_LOGS ->
                serviceScope.launchCleanup(startId) {
                    CleanupActions.cleanupLogs(this@CleanupService, days)
                }

            ACTION_CLEANUP_ALL ->
                serviceScope.launchCleanup(startId) {
                    CleanupActions.cleanupRecords(this@CleanupService, days)
                    CleanupActions.cleanupLogs(this@CleanupService, days)
                }

            else -> {
                PigeonLogger.warn(TAG, "Unknown cleanup action: ${intent?.action}, stopping")
                stopSelf(startId)
            }
        }

        // Nothing here needs to survive the process being killed, the schedule lives in CleanupWorker.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        PigeonLogger.info(TAG, "Cleanup service destroyed")
    }

    /**
     * Run [block] and stop the service once it finishes, so a one-off cleanup does not linger in the
     * background only to be killed by the system later on.
     */
    private fun CoroutineScope.launchCleanup(
        startId: Int,
        block: suspend () -> Unit,
    ) {
        launch {
            try {
                block()
            } finally {
                stopSelf(startId)
            }
        }
    }
}
