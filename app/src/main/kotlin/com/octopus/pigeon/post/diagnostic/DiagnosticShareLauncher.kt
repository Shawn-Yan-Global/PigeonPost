package com.octopus.pigeon.post.diagnostic

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.octopus.logging.PigeonLogger
import java.io.File

/**
 * Hands the diagnostic package to a mail client.
 *
 * The app does not send the package itself. It has no SMTP account of the user's
 * to send from, and adding one would mean storing a second password in the app,
 * which is the opposite of what this feature is for. So the system share sheet
 * opens and the user picks their own account and recipient.
 *
 * The attachment is shared through [FileProvider] with a one-shot read grant, so
 * the receiving app can read that one file and cannot see anything else in the
 * app's storage.
 */
object DiagnosticShareLauncher {
    private const val TAG = "DiagnosticShare"

    fun launch(
        context: Context,
        archive: File,
        subject: String,
    ) {
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                archive,
            )

        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        val chooser =
            Intent.createChooser(intent, subject).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        context.startActivity(chooser)
        PigeonLogger.info(TAG, "Diagnostic package shared: ${archive.name}")

        // Not deleted here on purpose. The receiving app has not read it yet.
        DiagnosticCleanupWorker.schedule(context)
    }
}
