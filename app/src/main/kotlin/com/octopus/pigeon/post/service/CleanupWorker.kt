package com.octopus.pigeon.post.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic cleanup of forwarded records and log files.
 *
 * The cleanup used to live in a `while (isActive) { delay(6h) }` loop inside [CleanupService], which
 * meant it silently stopped the moment the process was killed and never came back on reboot, so the
 * "auto cleanup" switch only ever cleaned once. WorkManager persists the request across process
 * death and reboots, so the schedule now actually keeps running.
 */
class CleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(
        appContext,
        params,
    ) {
    companion object {
        private const val TAG = "CleanupWorker"

        private const val PERIODIC_WORK_NAME = "pigeon_post_periodic_cleanup"

        /**
         * Runs once a day. Cleanup is cheap, it only walks a directory and issues a single delete
         * query, so a daily pass is enough to keep logs and records from growing without bound.
         */
        private const val CLEANUP_INTERVAL_HOURS = 24L

        /**
         * Arm the daily cleanup pass. Safe to call from any component: the work is unique, so repeated
         * calls collapse into the already scheduled request instead of piling up jobs.
         */
        fun enqueue(context: Context) {
            try {
                val request =
                    PeriodicWorkRequestBuilder<CleanupWorker>(CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS)
                        .setConstraints(Constraints.NONE)
                        .setBackoffCriteria(
                            BackoffPolicy.LINEAR,
                            1,
                            TimeUnit.HOURS,
                        ).build()

                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)

                PigeonLogger.info(TAG, "Periodic cleanup scheduled, running every $CLEANUP_INTERVAL_HOURS hours")
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "Failed to schedule periodic cleanup", e)
            }
        }

        /** Stop the schedule, used when the user turns auto cleanup off. */
        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
                PigeonLogger.info(TAG, "Cleanup schedule canceled")
            } catch (e: Exception) {
                PigeonLogger.error(TAG, "Failed to cancel cleanup schedule", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext

        return try {
            val preferencesRepository = PreferencesRepository(context)

            val recordsEnabled = preferencesRepository.autoCleanupRecordsEnabled.first()
            val recordsDays = preferencesRepository.autoCleanupRecordsDays.first()
            val logsEnabled = preferencesRepository.autoCleanupLogsEnabled.first()
            val logsDays = preferencesRepository.autoCleanupLogsDays.first()

            if (!recordsEnabled && !logsEnabled) {
                PigeonLogger.debug(TAG, "Auto cleanup disabled for both records and logs, releasing schedule")
                cancel(context)
                return Result.success()
            }

            // Keep the daily request armed even when nothing needed deleting, otherwise a pass that
            // has nothing to do would be the last one that ever runs.
            enqueue(context)

            if (recordsEnabled) {
                CleanupActions.cleanupRecords(context, recordsDays)
            }
            if (logsEnabled) {
                CleanupActions.cleanupLogs(context, logsDays)
            }

            Result.success()
        } catch (e: Exception) {
            // Reading the preferences failed, so it is worth another attempt rather than silently
            // skipping this pass.
            PigeonLogger.error(TAG, "Cleanup pass failed, will retry", e)
            Result.retry()
        }
    }
}
