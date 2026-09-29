package com.octopus.pigeon.post.diagnostic

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.octopus.logging.PigeonLogger
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the password protected support bundle.
 *
 * The point of the bundle is that a user can hand it to someone else without
 * first having to decide whether they trust that person. So the archive is
 * encrypted, and so is everything in it that could identify the user: the
 * address the app sends from, the address it sends to, the mail password, the
 * match keywords, and the times it runs.
 *
 * What goes in instead is the *shape* of the configuration. A report that says
 * "TLS on, port 465, one recipient, three keywords" is enough to work out why a
 * forward is failing, and it says nothing about who the user is. See
 * [ConfigShapeReport] for the specifics.
 *
 * The archive goes to the cache directory, which the system may reclaim, and is
 * removed by [DiagnosticCleanupWorker] once the share has had time to be read.
 * It is never written to persistent storage.
 */
object DiagnosticPackageBuilder {
    private const val TAG = "DiagnosticPackage"

    /** Total cap on the log files copied in. */
    private const val MAX_LOG_BYTES = 512L * 1024L

    private const val COPY_BUFFER = 8 * 1024

    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    /**
     * Writes the bundle and returns the file to share.
     *
     * The password is the applicationId. That makes it identical for every
     * install of a given flavour, which means it is a speed bump against someone
     * who already has the file rather than a real secret, and it is chosen that
     * way on purpose: the user is expected to be able to read it out to support
     * over the phone.
     */
    fun build(
        context: Context,
        report: ConfigShapeReport,
    ): File {
        val outputDir = File(context.cacheDir, OUTPUT_DIR).apply { mkdirs() }

        // Anything left here is from a share whose cleanup never ran.
        outputDir.listFiles()?.forEach { it.delete() }

        val output = File(outputDir, "pigeonpost-diagnostic-${stamp.format(Date())}.zip")
        val password = passwordFor(context)

        ZipOutputStream(
            BufferedOutputStream(FileOutputStream(output)),
            password.toCharArray(),
        ).use { zip ->
            writeText(zip, ENTRY_REPORT, report.toText())
            writeText(zip, ENTRY_DEVICE, deviceInfo(context))
            writeText(zip, ENTRY_PERMISSIONS, permissionInfo(context))
            copyLogs(context, zip)
        }

        PigeonLogger.info(TAG, "Diagnostic package written: ${output.name}, ${output.length()} bytes")
        return output
    }

    /** The password the archive is protected with. Shown in the UI, never logged. */
    fun passwordFor(context: Context): String = context.packageName

    /**
     * ZipCrypto rather than AES, so the system file manager and every desktop
     * unzip tool can open the result without extra software. It is weak against
     * an attacker who specifically wants the contents, which is accepted here:
     * the goal is that a bundle which gets forwarded by accident is not readable
     * by whoever receives it, not that it withstands an analyst.
     */
    private fun parametersFor(entryName: String): ZipParameters =
        ZipParameters().apply {
            fileNameInZip = entryName
            compressionMethod = CompressionMethod.DEFLATE
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.ZIP_STANDARD
        }

    private fun writeText(
        zip: ZipOutputStream,
        entryName: String,
        text: String,
    ) {
        zip.putNextEntry(parametersFor(entryName))
        zip.write(text.toByteArray())
        zip.closeEntry()
    }

    /**
     * Copies the most recent log files in, newest first, until the size cap.
     *
     * These are the same files the user can read in the app, redacted where they
     * were written. Re-filtering them here instead would risk the two drifting
     * apart, and the copy would quietly start including something sensitive.
     */
    private fun copyLogs(
        context: Context,
        zip: ZipOutputStream,
    ) {
        val files =
            File(context.filesDir, LOG_DIR)
                .listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()

        if (files.isEmpty()) {
            writeText(zip, ENTRY_LOGS_README, NO_LOGS_TEXT)
            return
        }

        var total = 0L
        for (file in files) {
            if (total >= MAX_LOG_BYTES) {
                writeText(zip, ENTRY_LOGS_TRUNCATED, TRUNCATED_TEXT)
                break
            }
            val allowed = minOf(file.length(), MAX_LOG_BYTES - total)
            file.inputStream().use { input ->
                zip.putNextEntry(parametersFor("$ENTRY_LOGS/${file.name}"))
                copyLimited(zip, input, allowed)
                zip.closeEntry()
            }
            total += allowed
        }
    }

    private fun copyLimited(
        zip: ZipOutputStream,
        input: InputStream,
        limit: Long,
    ) {
        val buffer = ByteArray(COPY_BUFFER)
        var remaining = limit
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) break
            zip.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun deviceInfo(context: Context): String =
        buildString {
            appendLine("Device")
            appendLine("  manufacturer: ${Build.MANUFACTURER}")
            appendLine("  brand: ${Build.BRAND}")
            appendLine("  model: ${Build.MODEL}")
            appendLine("  device: ${Build.DEVICE}")
            appendLine("  product: ${Build.PRODUCT}")
            appendLine()
            appendLine("Android")
            appendLine("  release: ${Build.VERSION.RELEASE}")
            appendLine("  sdk: ${Build.VERSION.SDK_INT}")
            appendLine("  securityPatch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("  fingerprint: ${Build.FINGERPRINT}")
        }

    /**
     * Whether each declared permission is currently granted.
     *
     * Only the grant state, never the value behind a permission, so this cannot
     * disclose anything the permission exists to protect.
     */
    private fun permissionInfo(context: Context): String =
        buildString {
            appendLine("Permission status")
            val requested =
                context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                    .requestedPermissions
                    ?.sorted()
                    .orEmpty()
            if (requested.isEmpty()) {
                appendLine("  (no runtime permissions requested)")
            }
            for (permission in requested) {
                val state =
                    if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                        "granted"
                    } else {
                        "not granted"
                    }
                appendLine("  $permission: $state")
            }
        }

    private const val NO_LOGS_TEXT =
        "No log files were present when this package was generated.\n" +
            "That is expected if the app has not forwarded anything yet, or if\n" +
            "logging is disabled in this build.\n"

    private const val TRUNCATED_TEXT =
        "Older log files were left out: the 512 KB cap was reached.\n" +
            "The most recent files are included.\n"

    /** Directory under the app cache that holds generated packages. */
    const val OUTPUT_DIR = "diagnostic"

    /** Where the logger writes, relative to the app's files directory. */
    const val LOG_DIR = "logs"

    private const val ENTRY_REPORT = "report.txt"
    private const val ENTRY_DEVICE = "device.txt"
    private const val ENTRY_PERMISSIONS = "permissions.txt"
    private const val ENTRY_LOGS = "logs"
    private const val ENTRY_LOGS_README = "logs/NO_LOGS.txt"
    private const val ENTRY_LOGS_TRUNCATED = "logs/TRUNCATED.txt"
}
