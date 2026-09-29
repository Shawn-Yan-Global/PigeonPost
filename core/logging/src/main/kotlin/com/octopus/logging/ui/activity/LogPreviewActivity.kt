package com.octopus.logging.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import com.octopus.locale.LocaleAwareComponentActivity
import com.octopus.logging.model.LogFileInfo
import com.octopus.logging.ui.screen.LogPreviewScreen

/**
 * Log preview activity - concrete implementation
 * Uses LoggingConfig for theme and language
 */
class LogPreviewActivity :
    LocaleAwareComponentActivity(
        languageProvider = { context -> LoggingConfig.getLanguageProvider(context) },
    ) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logPath = intent.getStringExtra(EXTRA_LOG_PATH) ?: ""
        val logFile =
            LogFileInfo(
                path = logPath,
                size = 0L,
                lastModified = "",
                exists = true,
            )

        setContent {
            LoggingConfig.provideTheme {
                LogPreviewScreen(
                    logFile = logFile,
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_LOG_PATH = "log_path"
    }
}
