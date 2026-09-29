package com.octopus.logging.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.octopus.locale.LocaleAwareComponentActivity
import com.octopus.logging.ui.screen.LogCleanupConfig
import com.octopus.logging.ui.screen.LogManagementScreen
import kotlinx.coroutines.flow.Flow

/**
 * Log management activity - concrete implementation
 * Uses LoggingConfig to get theme, language provider and cleanup config
 */
class LogManagementActivity :
    LocaleAwareComponentActivity(
        languageProvider = { context -> LoggingConfig.getLanguageProvider(context) },
    ) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoggingConfig.provideTheme {
                LogManagementScreen(
                    onBackClick = { finish() },
                    onOpenLogFiles = {
                        startActivity(Intent(this, LogFilesActivity::class.java))
                    },
                    cleanupConfig = LoggingConfig.getCleanupConfig(this),
                )
            }
        }
    }
}

/**
 * Configuration interface for logging module
 * App module should initialize this at startup
 */
object LoggingConfig {
    private var themeProvider: (@Composable (content: @Composable () -> Unit) -> Unit)? = null
    private var languageProviderFactory: ((Context) -> Flow<String>)? = null
    private var cleanupConfigFactory: ((Context) -> LogCleanupConfig)? = null

    fun initialize(
        theme: @Composable (content: @Composable () -> Unit) -> Unit,
        languageProvider: (Context) -> Flow<String>,
        cleanupConfig: (Context) -> LogCleanupConfig,
    ) {
        themeProvider = theme
        languageProviderFactory = languageProvider
        cleanupConfigFactory = cleanupConfig
    }

    @Composable
    internal fun provideTheme(content: @Composable () -> Unit) {
        val provider = themeProvider
        if (provider != null) {
            provider(content)
        } else {
            // Fallback to MaterialTheme if not configured
            MaterialTheme {
                content()
            }
        }
    }

    internal fun getLanguageProvider(context: Context): Flow<String> =
        languageProviderFactory?.invoke(context)
            ?: throw IllegalStateException("LoggingConfig not initialized. Call LoggingConfig.initialize() in Application.onCreate()")

    internal fun getCleanupConfig(context: Context): LogCleanupConfig =
        cleanupConfigFactory?.invoke(context)
            ?: throw IllegalStateException("LoggingConfig not initialized. Call LoggingConfig.initialize() in Application.onCreate()")
}
