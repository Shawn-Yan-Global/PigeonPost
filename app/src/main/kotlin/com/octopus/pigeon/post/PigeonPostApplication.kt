package com.octopus.pigeon.post

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import com.octopus.locale.withLocale
import com.octopus.logging.PigeonLogger
import com.octopus.logging.ui.activity.LoggingConfig
import com.octopus.logging.ui.screen.LogCleanupConfig
import com.octopus.pigeon.post.data.database.AppDatabase
import com.octopus.pigeon.post.data.repository.DebugUnlockState
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.diagnostic.DiagnosticCleanupWorker
import com.octopus.pigeon.post.service.CleanupActions
import com.octopus.pigeon.post.service.CleanupWorker
import com.octopus.pigeon.post.service.SmsKeepAliveWorker
import com.octopus.pigeon.post.ui.theme.PigeonPostTheme
import com.octopus.pigeon.post.util.PigeonPostNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Main Application class with locale support using delegation
 * No inheritance required - uses extension function for locale management
 */
class PigeonPostApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun attachBaseContext(base: Context) {
        // Apply locale using extension function - no inheritance needed!
        super.attachBaseContext(base.withLocale())
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize logging system
        // The silent build writes no log files at all: a plaintext on-disk
        // archive of every forwarded message is exactly the kind of leak this
        // variant exists to avoid.
        PigeonLogger.initialize(this, fileLoggingEnabled = !BuildConfig.SILENT)
        PigeonLogger.info(TAG, "Hello world, I am Pigeon Post!")
        PigeonLogger.info(TAG, "Application started with locale: ${resources.configuration.locales[0]}")

        // Initialize logging module configuration
        initializeLoggingConfig()

        // Initialize notification channels early to ensure they exist when SMS arrives
        // This is critical for notifications to work even when app is killed and receives SMS
        PigeonPostNotificationManager.initializeChannels(this)
        PigeonLogger.info(TAG, "Notification channels initialized at app startup")

        // Note: Language is already applied in attachBaseContext via withLocale()
        // No need to apply it again here to avoid conflicts

        // A diagnostic package that outlived its cleanup worker, which happens
        // whenever the process is killed before the delayed work can run.
        DiagnosticCleanupWorker.sweep(this)

        // Read the stored debug unlock before any screen can ask for it.
        DebugUnlockState.attach(PreferencesRepository(this))

        CoroutineScope(Dispatchers.Default).launch {
            // Auto-restart service if it was enabled before app restart (e.g., after language change)
            restartServiceIfNeeded()

            val repository = PreferencesRepository(this@PigeonPostApplication)

            // Arm the WorkManager safety net that restores the foreground service if it gets killed
            if (repository.serviceEnabled.first()) {
                SmsKeepAliveWorker.enqueue(this@PigeonPostApplication)
                PigeonLogger.info(TAG, "Keep-alive worker scheduled at app startup")
            }

            // Make sure the cleanup schedule matches the stored preference, which also repairs the
            // schedule after a reinstall or an app upgrade dropped the pending work.
            val cleanupEnabled =
                repository.autoCleanupRecordsEnabled.first() ||
                    repository.autoCleanupLogsEnabled.first()

            if (cleanupEnabled) {
                CleanupWorker.enqueue(this@PigeonPostApplication)
            } else {
                CleanupWorker.cancel(this@PigeonPostApplication)
            }
        }
    }

    /**
     * Initialize logging module configuration
     */
    private fun initializeLoggingConfig() {
        LoggingConfig.initialize(
            theme = @Composable { content ->
                PigeonPostTheme {
                    content()
                }
            },
            languageProvider = { context ->
                PreferencesRepository(context).appLanguage
            },
            cleanupConfig = { context ->
                object : LogCleanupConfig {
                    private val preferencesRepository = PreferencesRepository(context)

                    override suspend fun getAutoCleanupEnabled(): Boolean = preferencesRepository.autoCleanupLogsEnabled.first()

                    override suspend fun getAutoCleanupDays(): Int = preferencesRepository.autoCleanupLogsDays.first()

                    override suspend fun setAutoCleanup(
                        enabled: Boolean,
                        days: Int,
                    ) {
                        preferencesRepository.setAutoCleanupLogs(enabled, days)
                        // Keep the WorkManager schedule in sync with the stored preference, otherwise
                        // the toggle would only be a flag that nothing ever reads.
                        if (enabled) {
                            CleanupWorker.enqueue(context)
                        } else if (!preferencesRepository.autoCleanupRecordsEnabled.first()) {
                            // Only stop the schedule when neither logs nor records want cleanup,
                            // they share the same worker.
                            CleanupWorker.cancel(context)
                        }
                    }

                    override suspend fun triggerImmediateCleanup(days: Int): Int =
                        // Run it inline rather than through CleanupService: this is a one-off user
                        // action, and awaiting it lets the screen report how many files went away
                        // instead of guessing from a file count that may not have changed yet.
                        CleanupActions.cleanupLogs(this@PigeonPostApplication, days)
                }
            },
        )
        PigeonLogger.info(TAG, "Logging module configuration initialized")
    }

    /**
     * Auto-restart SMS monitoring service if it was enabled before app restart
     * This ensures service continues running after language changes or app restarts
     */
    private suspend fun restartServiceIfNeeded() {
        try {
            val repository = PreferencesRepository(this)
            val isServiceEnabled = repository.serviceEnabled.first()

            if (isServiceEnabled) {
                if (!com.octopus.pigeon.post.service.SmsMonitorService
                        .isAutoRestartAllowed()
                ) {
                    PigeonLogger.warn(TAG, "Auto restart disabled, skipping app-start service restart")
                    return
                }
                PigeonLogger.info(TAG, "Service was enabled, restarting SMS monitor service after app restart")
                // startForegroundService so the service has a valid budget to post its notification
                val serviceIntent = Intent(this, com.octopus.pigeon.post.service.SmsMonitorService::class.java)
                startForegroundService(serviceIntent)
                PigeonLogger.info(TAG, "SMS monitor service restarted successfully")
            } else {
                PigeonLogger.debug(TAG, "Service was not enabled, skipping auto-restart")
            }
        } catch (e: Exception) {
            PigeonLogger.error(TAG, "Failed to auto-restart service", e)
        }
    }

    companion object {
        private val TAG = PigeonPostApplication::class.java.simpleName
    }
}
