package com.octopus.pigeon.post.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.octopus.locale.AppLocaleManagerImpl
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.ui.base.BaseActivity
import com.octopus.pigeon.post.ui.screen.LanguageSettingsScreen
import com.octopus.pigeon.post.ui.theme.PigeonPostTheme
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LanguageSettingsActivity : BaseActivity() {
    private val viewModel: MainViewModel by viewModels()

    companion object {
        private val TAG = LanguageSettingsActivity::class.java.simpleName
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PigeonPostTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_language)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = getString(R.string.back),
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(),
                        )
                    },
                ) { paddingValues ->
                    val scope = rememberCoroutineScope()
                    val (showConfirmDialog, setShowConfirmDialog) = remember { mutableStateOf(false) }
                    val (selectedLanguageCode, setSelectedLanguageCode) =
                        remember {
                            mutableStateOf(
                                "",
                            )
                        }
                    val (selectedLanguageName, setSelectedLanguageName) =
                        remember {
                            mutableStateOf(
                                "",
                            )
                        }

                    val onSelect: (String) -> Unit = { code ->
                        scope.launch {
                            // Read the stored language on every tap instead of relying on a value
                            // captured in composition, so a tap that lands before the first
                            // collection cannot slip through. Re-picking the language that is
                            // already in use changes nothing, so there is nothing to confirm.
                            if (code == viewModel.preferencesRepository.appLanguage.first()) {
                                PigeonLogger.info(TAG, "Language $code is already active, ignoring selection")
                                return@launch
                            }

                            setSelectedLanguageCode(code)
                            setSelectedLanguageName(
                                when (code) {
                                    "en" -> "English"
                                    "zh" -> "中文"
                                    else -> code
                                },
                            )
                            setShowConfirmDialog(true)
                        }
                    }

                    if (showConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { setShowConfirmDialog(false) },
                            title = { Text(stringResource(R.string.confirm_language_change)) },
                            text = {
                                Text(
                                    stringResource(
                                        R.string.language_change_message,
                                        selectedLanguageName,
                                    ),
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            // Save language to both DataStore and SharedPreferences
                                            viewModel.preferencesRepository.setAppLanguage(
                                                selectedLanguageCode,
                                            )
                                            // Restart app - language will be applied in attachBaseContext
                                            AppLocaleManagerImpl.restartApp(this@LanguageSettingsActivity)
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.confirm))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { setShowConfirmDialog(false) }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            },
                        )
                    }

                    LanguageSettingsScreen(
                        paddingValues,
                        viewModel = viewModel,
                        onSelectLanguage = onSelect,
                    )
                }
            }
        }
    }
}
