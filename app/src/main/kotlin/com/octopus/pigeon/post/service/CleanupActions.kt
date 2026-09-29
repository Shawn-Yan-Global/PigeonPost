package com.octopus.pigeon.post.service

import android.content.Context
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.data.database.AppDatabase
import com.octopus.pigeon.post.data.repository.SmsRepository
import java.time.LocalDateTime

/**
 * Shared cleanup implementations for forwarded records and log files.
 *
 * Both [CleanupService] (user triggered) and [CleanupWorker] (scheduled) run the same logic, so the
 * two entry points can never drift apart.
 */
object CleanupActions {
    private const val TAG = "CleanupActions"

    /** Delete forwarded records older than [days] days. */
    suspend fun cleanupRecords(
        context: Context,
        days: Int,
    ) {
        try {
            val smsRepository = SmsRepository(AppDatabase.getDatabase(context).smsRecordDao())
            val cutoffTime = LocalDateTime.now().minusDays(days.toLong())
            val deletedCount = smsRepository.deleteOldRecords(cutoffTime)
            PigeonLogger.info(TAG, "Record cleanup completed, deleted $deletedCount records older than $days days")
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Record cleanup failed", e)
        }
    }

    /**
     * Delete log files older than [days] days and return how many were removed.
     *
     * Delegates to [PigeonLogger] instead of deleting files directly, because it reuses the currently
     * open log file. Removing that file behind log4j's back would leave the appender writing to a
     * stale file descriptor, so new log lines would vanish.
     */
    suspend fun cleanupLogs(
        context: Context,
        days: Int,
    ): Int =
        try {
            val deleted = PigeonLogger.clearLogs(days)
            PigeonLogger.info(TAG, "Log cleanup completed, deleted $deleted log files older than $days days")
            deleted
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Log cleanup failed", e)
            0
        }
}
