package com.octopus.pigeon.post.diagnostic

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.octopus.logging.PigeonLogger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Deletes the generated diagnostic packages.
 *
 * The bundle is meant to exist only for the length of the share, and it holds a
 * description of the user's setup, so it should not be left lying in the cache.
 * But it also cannot be deleted the moment the share sheet opens: the mail
 * client has to return from the share intent, read the attachment, and only then
 * upload it, and deleting the file at that point makes the send fail with an
 * error the user cannot explain.
 *
 * There is no callback for "the mail was sent", so this runs on a delay instead.
 * Ten minutes is long enough for a mail client to read and upload the file, and
 * short enough that the file does not survive. If the user closes the share
 * sheet without sending, the file is removed on the same schedule, which is the
 * outcome that was wanted anyway.
 *
 * A sweep on every start covers the case where the process is killed before the
 * delayed work runs, which on a device that is low on memory is not rare.
 */
class DiagnosticCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val removed = sweepDirectory(File(applicationContext.cacheDir, DiagnosticPackageBuilder.OUTPUT_DIR))
        PigeonLogger.info(TAG, "Diagnostic cleanup removed $removed file(s)")
        return Result.success()
    }

    companion object {
        private const val TAG = "DiagnosticCleanup"
        private const val WORK_NAME = "diagnostic_cleanup"

        /** Long enough for a mail client to read and upload the attachment. */
        private val DELAY = 10L

        fun schedule(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<DiagnosticCleanupWorker>()
                    .setInitialDelay(DELAY, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiresStorageNotLow(true)
                            .build(),
                    ).build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * Removes everything in the diagnostic output directory.
         *
         * Run on every app start as well as on the delay, so a package that
         * outlived the worker is still cleaned up.
         */
        fun sweep(context: Context): Int = sweepDirectory(File(context.cacheDir, DiagnosticPackageBuilder.OUTPUT_DIR))

        private fun sweepDirectory(directory: File): Int {
            if (!directory.isDirectory) return 0
            var removed = 0
            directory.listFiles()?.forEach { file ->
                if (file.delete()) removed++
            }
            directory.delete()
            return removed
        }
    }
}
