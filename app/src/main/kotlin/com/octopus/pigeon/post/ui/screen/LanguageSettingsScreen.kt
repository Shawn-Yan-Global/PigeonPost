package com.octopus.pigeon.post.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.octopus.locale.LocaleHelper
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel
import com.octopus.ui.spacing.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    paddingValues: PaddingValues,
    viewModel: MainViewModel,
    supportedLanguages: List<Pair<String, String>> =
        listOf(
            "en" to "English",
            "zh" to "中文",
        ),
    onSelectLanguage: (String) -> Unit,
) {
    val context = LocalContext.current

    // Seed the state with the synchronously readable value so the active language is highlighted
    // on the very first frame. Waiting for the flow would briefly highlight the wrong entry.
    val current by viewModel.preferencesRepository.appLanguage
        .collectAsState(initial = LocaleHelper.getSavedLanguageCode(context))

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(AppSpacing.large),
    ) {
        supportedLanguages.forEach { (code, label) ->
            val isActive = current == code

            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (isActive) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppSpacing.small)
                        // The language already in use cannot be re-selected, so make it inert
                        // instead of letting a tap do nothing.
                        .clickable(enabled = !isActive) { onSelectLanguage(code) },
            ) {
                Text(
                    text = if (isActive) "✓ $label" else label,
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (isActive) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.padding(AppSpacing.large),
                )
            }
        }
    }
}
