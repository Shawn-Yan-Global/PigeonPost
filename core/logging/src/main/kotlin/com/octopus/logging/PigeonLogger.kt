package com.octopus.logging

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.octopus.logging.model.LogContent
import com.octopus.logging.model.LogFileInfo
import com.octopus.logging.model.LogStats
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enhanced logging system with multi-language support
 * Supports date-based log rotation and language selection
 */
object PigeonLogger {
    private const val TAG = "PigeonLogger"
    private const val LOG_ARCHIVE_NAME = "pigeon_post_logs.zip"
    private const val MAX_LOG_FILE_SIZE = 1024 * 1024 // 1MB

    private lateinit var logger: Logger
    private lateinit var logDir: File
    private var currentLogFile: File? = null
    private var fileLoggingEnabled = true

    /**
     * Whether log files on disk are sealed with a Keystore key.
     *
     * Off for debug builds so a developer can read them directly, on for release
     * builds where the file sits on somebody else's phone until they share it.
     * Driven by BuildConfig.ENCRYPT_LOGS so the choice is a build decision
     * rather than a runtime one.
     */
    private var encryptLogFiles = false

    private val logCipher = LogFileCipher()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun initialize(
        context: Context,
        fileLoggingEnabled: Boolean = true,
        encryptLogFiles: Boolean = false,
    ) {
        try {
            this.fileLoggingEnabled = fileLoggingEnabled
            this.encryptLogFiles = encryptLogFiles

            if (fileLoggingEnabled) {
                logDir = File(context.filesDir, "logs")
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }

                // today log
                currentLogFile = getTodayLogFile()
            }

            // config Log4j
            configureLog4j(context)

            logger = LogManager.getLogger("PigeonPost")

            Log.d(TAG, "Logging system initialized successfully, current log file: ${currentLogFile?.absolutePath}")
            info("Logging system initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Logging system initialization failed", e)
        }
    }

    private fun getTodayLogFile(): File {
        val today = dateFormat.format(Date())
        return File(logDir, "$today.log")
    }

    private fun checkAndRotateLog() {
        // Nothing to rotate when file logging is off, and logDir is never
        // created in that mode.
        if (!fileLoggingEnabled) return

        val todayFile = getTodayLogFile()

        // If date changed, switch to new log file
        if (currentLogFile != todayFile) {
            currentLogFile = todayFile
            try {
                configureLog4j(null) // Reconfigure Log4j
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reconfigure Log4j when switching log file", e)
            }
        }

        // If current file is too large, create a new file with timestamp
        currentLogFile?.let { file ->
            if (file.exists() && file.length() > MAX_LOG_FILE_SIZE) {
                val timestamp = SimpleDateFormat("HH-mm-ss", Locale.getDefault()).format(Date())
                val today = dateFormat.format(Date())
                val newFile = File(logDir, "${today}_$timestamp.log")
                currentLogFile = newFile
                try {
                    configureLog4j(null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reconfigure Log4j during log file rotation", e)
                }
            }
        }
    }

    private fun configureLog4j(context: Context?) {
        try {
            // When file logging is off there is no log file to attach to, and the silent
            // build additionally wants nothing written at all, so the whole root
            // logger is switched off. That also keeps SMS content out of logcat.
            val configContent =
                buildLog4jConfigXml(
                    fileLoggingEnabled = fileLoggingEnabled,
                    // When the file is sealed line by line below, Log4j must not
                    // also write it in the clear. It keeps the console appender for
                    // logcat; the file is written by [appendToLogFile].
                    logFilePath = currentLogFile?.absolutePath?.takeUnless { encryptLogFiles },
                )

            val configFile =
                if (context != null) {
                    File(context.filesDir, "log4j2.xml")
                } else {
                    File(logDir.parent, "log4j2.xml")
                }
            configFile.writeText(configContent)

            val configSource = ConfigurationSource(FileInputStream(configFile))
            Configurator.initialize(null, configSource)

            verifyAppendersApplied()
        } catch (e: Exception) {
            Log.e(TAG, "Log4j configuration failed", e)
        }
    }

    /**
     * Log4j does not fail loudly on a bad configuration. It logs the parse error
     * to its own status logger and carries on with a default configuration, so
     * the try/catch above never sees it and the caller has no way to tell that
     * nothing is being written. Checking the appenders that actually ended up on
     * the context turns that silent failure into a visible one.
     */
    private fun verifyAppendersApplied() {
        val expected = mutableSetOf<String>()
        if (fileLoggingEnabled) {
            expected += "Console"
            // Log4j is only given a File appender when the file is *not* being
            // sealed here, so expecting one while encrypting would report a
            // failure for the working arrangement. See [configureLog4j].
            if (currentLogFile != null && !encryptLogFiles) {
                expected += "FileAppender"
            }
        }
        if (expected.isEmpty()) {
            return
        }
        val applied =
            runCatching {
                (LogManager.getContext(false) as? LoggerContext)
                    ?.configuration
                    ?.appenders
                    ?.keys
                    .orEmpty()
            }.getOrDefault(emptySet())
        val missing = expected - applied
        if (missing.isNotEmpty()) {
            val message =
                "Log4j did not attach ${missing.joinToString()}; this build writes its log file " +
                    "separately, so check whether lines are reaching it. Status logger output explains why."
            // Release builds strip android.util.Log, so reporting through it would hide
            // exactly the failure this is here to catch. Write it straight to the file
            // instead: that path does not depend on Log4j working.
            currentLogFile?.let { file ->
                runCatching {
                    file.appendText("${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())} $message\n")
                }
            }
            Log.e(TAG, message)
        }
    }

    /**
     * Appends one already-formatted line to the current log file.
     *
     * Log4j only writes the file when it is not being sealed here; see
     * [configureLog4j]. Each line is written in one call so a partially written
     * line cannot be produced by a concurrent writer.
     */

    /**
     * One log line in the same shape Log4j would have written, so a file looks
     * identical whether it came from the file appender or from here.
     */
    private fun formatLine(
        level: String,
        message: String,
        throwable: Throwable?,
    ): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        // Uppercased to match Log4j's %-5level, which the stats screen counts
        // against. A lowercase "info" here would make every count come back zero.
        val base =
            "$stamp [${Thread.currentThread().name}] %-5s PigeonPost - $message".format(
                level.uppercase(),
            )
        if (throwable == null) return base
        return base + "\n" + Log.getStackTraceString(throwable)
    }

    private fun appendToLogFile(formatted: String) {
        if (!fileLoggingEnabled || !encryptLogFiles) return
        val file = currentLogFile ?: return
        runCatching {
            file.appendText(logCipher.sealLine(formatted) + "\n")
        }.onFailure {
            Log.e(TAG, "Failed to append to the log file", it)
        }
    }

    // Logging methods
    fun debug(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        checkAndRotateLog()
        val logMessage = "[$tag] $message"
        if (encryptLogFiles) appendToLogFile(formatLine("debug", logMessage, throwable))
        Log.d(TAG, logMessage, throwable)
        if (::logger.isInitialized) {
            if (throwable != null) {
                logger.debug(logMessage, throwable)
            } else {
                logger.debug(logMessage)
            }
        }
    }

    fun info(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        checkAndRotateLog()
        val logMessage = "[$tag] $message"
        if (encryptLogFiles) appendToLogFile(formatLine("info", logMessage, throwable))
        Log.i(TAG, logMessage, throwable)
        if (::logger.isInitialized) {
            if (throwable != null) {
                logger.info(logMessage, throwable)
            } else {
                logger.info(logMessage)
            }
        }
    }

    fun warn(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        checkAndRotateLog()
        val logMessage = "[$tag] $message"
        if (encryptLogFiles) appendToLogFile(formatLine("warn", logMessage, throwable))
        Log.w(TAG, logMessage, throwable)
        if (::logger.isInitialized) {
            if (throwable != null) {
                logger.warn(logMessage, throwable)
            } else {
                logger.warn(logMessage)
            }
        }
    }

    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        checkAndRotateLog()
        val logMessage = "[$tag] $message"
        if (encryptLogFiles) appendToLogFile(formatLine("error", logMessage, throwable))
        Log.e(TAG, logMessage, throwable)
        if (::logger.isInitialized) {
            if (throwable != null) {
                logger.error(logMessage, throwable)
            } else {
                logger.error(logMessage)
            }
        }
    }

    fun debug(
        message: String,
        throwable: Throwable? = null,
    ) = debug("General", message, throwable)

    fun info(
        message: String,
        throwable: Throwable? = null,
    ) = info("General", message, throwable)

    fun warn(
        message: String,
        throwable: Throwable? = null,
    ) = warn("General", message, throwable)

    private fun error(
        message: String,
        throwable: Throwable? = null,
    ) = error("General", message, throwable)

    // Get log file information
    fun getLogFileInfo(): LogFileInfo? =
        currentLogFile?.let { logFile ->
            if (logFile.exists()) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                LogFileInfo(
                    path = logFile.absolutePath,
                    size = logFile.length(),
                    lastModified = sdf.format(Date(logFile.lastModified())),
                    exists = true,
                )
            } else {
                null
            }
        }

    // Get all log files list
    fun getAllLogFiles(): List<LogFileInfo> =
        try {
            if (!::logDir.isInitialized || !logDir.exists()) {
                emptyList()
            } else {
                logDir
                    .listFiles { file -> file.isFile && file.name.endsWith(".log") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { file ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        LogFileInfo(
                            path = file.absolutePath,
                            size = file.length(),
                            lastModified = sdf.format(Date(file.lastModified())),
                            exists = true,
                        )
                    } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get log files list", e)
            emptyList()
        }

    // Filter log files by date range
    fun getLogFilesByDateRange(
        startDate: String,
        endDate: String,
    ): List<LogFileInfo> =
        getAllLogFiles().filter { logInfo ->
            try {
                val fileName = File(logInfo.path).nameWithoutExtension
                val fileDate =
                    if (fileName.contains("_")) {
                        fileName.substringBefore("_")
                    } else {
                        fileName
                    }
                fileDate in startDate..endDate
            } catch (_: Exception) {
                false
            }
        }

    // Export logs
    fun exportLogs(
        context: Context,
        dateRange: Pair<String, String>? = null,
    ): Intent? {
        return try {
            if (!::logDir.isInitialized || !logDir.exists()) {
                error("Log directory does not exist, cannot export")
                return null
            }

            // creat zip
            val zipFile = File(context.cacheDir, LOG_ARCHIVE_NAME)
            createLogZip(zipFile, context, dateRange)

            // creat share Intent
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile,
                )

            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "PigeonPost Application Logs")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "PigeonPost application log files, generated at: ${
                            SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.getDefault(),
                            ).format(Date())
                        }" +
                            "\n\nThe archive is password protected. Password: ${context.packageName}",
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            info("Log export successful, file size: ${zipFile.length()} bytes")
            Intent.createChooser(shareIntent, "Export Logs")
        } catch (e: Exception) {
            error("Log export failed", e)
            null
        }
    }

    private fun createLogZip(
        zipFile: File,
        context: Context? = null,
        dateRange: Pair<String, String>? = null,
    ) {
        val password = context?.packageName?.toCharArray()
        ZipOutputStream(zipFile.outputStream(), password).use { zip ->
            val logFiles =
                if (dateRange != null) {
                    getLogFilesByDateRange(dateRange.first, dateRange.second)
                } else {
                    getAllLogFiles()
                }

            // Add log files.
            //
            // Opened here, on the device that wrote them, because the seal is
            // keyed to that device's Keystore: a sealed file copied to another
            // phone, or attached to a bug report, is undecryptable. Decrypting at
            // export is what lets the archive stay readable to whoever debugs the
            // problem, while the file on disk stays sealed the whole time.
            logFiles.forEach { logInfo ->
                val file = File(logInfo.path)
                if (file.exists()) {
                    zip.putNextEntry(encryptedParameters(file.name))
                    val text = readableLines(file).joinToString(separator = "\n")
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }

            // Add system information
            val systemInfo = getSystemInfo(context)
            zip.putNextEntry(encryptedParameters("system_info.txt"))
            zip.write(systemInfo.toByteArray())
            zip.closeEntry()
        }
    }

    /**
     * Entries are ZipCrypto encrypted, matching the diagnostic package.
     *
     * Log files hold redacted but still real operational detail, and an export is
     * the one artefact most likely to be attached to an email to someone else.
     * ZipCrypto rather than AES so the system file manager and ordinary desktop
     * unzip tools can open it without extra software. The goal is that a bundle
     * forwarded by accident is not casually readable, not that it withstands
     * someone who specifically wants the contents.
     */
    private fun encryptedParameters(entryName: String): ZipParameters =
        ZipParameters().apply {
            fileNameInZip = entryName
            compressionMethod = CompressionMethod.DEFLATE
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.ZIP_STANDARD
        }

    private fun getSystemInfo(context: Context? = null): String =
        buildString {
            appendLine("=== System Information ===")
            appendLine(
                "Generated at: ${
                    SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault(),
                    ).format(Date())
                }",
            )
            appendLine("Android version: ${android.os.Build.VERSION.RELEASE}")
            appendLine("API level: ${android.os.Build.VERSION.SDK_INT}")
            appendLine("Device model: ${android.os.Build.MODEL}")
            appendLine("Device manufacturer: ${android.os.Build.MANUFACTURER}")
            appendLine("App version: ${getAppVersion(context)}")
            appendLine()
        }

    private fun getAppVersion(context: Context? = null): String =
        try {
            context?.let {
                val packageInfo = it.packageManager.getPackageInfo(it.packageName, 0)
                "${packageInfo.versionName} (${packageInfo.longVersionCode})"
            } ?: "Unknown version"
        } catch (e: Exception) {
            "Failed to get version"
        }

    // Clear logs

    /**
     * Delete log files and report how many were actually removed.
     *
     * @param olderThanDays when null every log file is removed, otherwise only files that were last
     *   written more than [olderThanDays] days ago. Returns the number of deleted files, which lets
     *   callers tell "nothing was old enough" apart from "the cleanup failed".
     */
    fun clearLogs(olderThanDays: Int? = null): Int =
        try {
            info("Starting log files cleanup")

            var cleared = 0
            val cutoffTime =
                if (olderThanDays != null) {
                    System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
                } else {
                    Long.MAX_VALUE // Clear all files
                }

            // Clear files in log directory
            if (::logDir.isInitialized && logDir.exists()) {
                logDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".log")) {
                        if (olderThanDays == null || file.lastModified() < cutoffTime) {
                            if (file.delete()) {
                                cleared++
                                Log.i(TAG, "Cleaned log file: ${file.name}")
                            }
                        }
                    }
                }
            }

            // Reinitialize current log file
            if (cleared > 0 || olderThanDays == null) {
                currentLogFile = getTodayLogFile()
                if (!currentLogFile!!.exists()) {
                    currentLogFile!!.createNewFile()
                }
                info("Log files cleaned, current log file: ${currentLogFile?.name}")
            }

            cleared
        } catch (e: Exception) {
            error("Log cleanup failed", e)
            0
        }

    /**
     * The log file's lines, opened if the file is sealed.
     *
     * Debug builds write plaintext and release builds write sealed lines, and a
     * directory can hold both after a build type change, so each line is opened
     * on its own rather than deciding once for the whole file.
     */
    private fun readableLines(file: File): List<String> =
        runCatching {
            file.readLines().map { logCipher.openLine(it) }
        }.onFailure {
            Log.e(TAG, "Failed to read ${file.name}", it)
        }.getOrDefault(emptyList())

    // Log statistics information
    fun getLogStats(logFilePath: String? = null): LogStats =
        try {
            val targetFile = logFilePath?.let { File(it) } ?: currentLogFile

            if (targetFile == null || !targetFile.exists()) {
                LogStats(0, 0, 0, 0, 0)
            } else {
                // Read through the cipher so counts match what the viewer shows
                // rather than counting base64 blobs.
                val lines = readableLines(targetFile)

                LogStats(
                    totalLines = lines.size,
                    debugCount = lines.count { it.contains("DEBUG") },
                    infoCount = lines.count { it.contains("INFO") },
                    warnCount = lines.count { it.contains("WARN") },
                    errorCount = lines.count { it.contains("ERROR") },
                )
            }
        } catch (e: Exception) {
            error("Failed to get log statistics", e)
            LogStats(0, 0, 0, 0, 0)
        }

    // Read log content (paginated)
    fun readLogContent(
        logFilePath: String,
        page: Int = 0,
        pageSize: Int = 100,
    ): LogContent {
        return try {
            val file = File(logFilePath)
            if (!file.exists()) {
                return LogContent(emptyList(), 0, 0, 0)
            }

            val lines = readableLines(file)
            val totalLines = lines.size
            val totalPages = (totalLines + pageSize - 1) / pageSize
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, totalLines)

            val pageLines =
                if (startIndex < totalLines) {
                    lines.subList(startIndex, endIndex)
                } else {
                    emptyList()
                }

            LogContent(pageLines, page, totalPages, totalLines)
        } catch (e: Exception) {
            error("Failed to read log content", e)
            LogContent(emptyList(), 0, 0, 0)
        }
    }
}
