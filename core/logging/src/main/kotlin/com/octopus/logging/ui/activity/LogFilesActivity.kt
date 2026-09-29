package com.octopus.logging.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.octopus.locale.LocaleAwareComponentActivity
import com.octopus.logging.ui.screen.LogFilesScreen

/**
 * Log files activity - concrete implementation
 * Uses LoggingConfig for theme and language
 */
class LogFilesActivity :
    LocaleAwareComponentActivity(
        languageProvider = { context -> LoggingConfig.getLanguageProvider(context) },
    ) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LoggingConfig.provideTheme {
                LogFilesScreen(
                    onBack = { finish() },
                    onFileSelected = { file ->
                        val intent = Intent(this, LogPreviewActivity::class.java)
                        intent.putExtra(LogPreviewActivity.EXTRA_LOG_PATH, file.path)
                        startActivity(intent)
                    },
                )
            }
        }
    }
}
