package com.octopus.pigeon.post.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.model.MatchLogic
import com.octopus.pigeon.post.data.model.TemplateConfig
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.ui.component.ConfirmSaveDialog
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel
import com.octopus.ui.spacing.AppSpacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    viewModel: MainViewModel,
    onOpenServiceStatus: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val templateConfig by viewModel.templateConfig.collectAsState()
    val preferencesRepository = remember { PreferencesRepository(context) }

    // Get default email template with proper i18n support
    val defaultEmailTemplate = stringResource(R.string.email_template_default)

    var keywords by remember { mutableStateOf(templateConfig.keywords.joinToString("\n")) }
    var caseSensitive by remember { mutableStateOf(templateConfig.caseSensitive) }
    var matchLogic by remember { mutableStateOf(templateConfig.matchLogic) }
    var emailTemplate by remember(templateConfig.emailTemplate, defaultEmailTemplate) {
        mutableStateOf(
            templateConfig.emailTemplate.ifEmpty { defaultEmailTemplate },
        )
    }
    var useTemplate by remember { mutableStateOf(templateConfig.useTemplate) }
    var noFilterMode by remember { mutableStateOf(false) }
    var showNoFilterWarning by remember { mutableStateOf(false) }
    var showSavedServiceStoppedDialog by remember { mutableStateOf(false) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    var pendingConfig by remember { mutableStateOf<TemplateConfig?>(null) }
    val serviceEnabled by viewModel.serviceEnabled.collectAsState()

    // Load no-filter mode settings
    LaunchedEffect(Unit) {
        noFilterMode = preferencesRepository.noFilterForwardMode.first()
    }

    LaunchedEffect(templateConfig, defaultEmailTemplate) {
        keywords = templateConfig.keywords.joinToString("\n")
        caseSensitive = templateConfig.caseSensitive
        matchLogic = templateConfig.matchLogic
        emailTemplate = templateConfig.emailTemplate.ifEmpty { defaultEmailTemplate }
        useTemplate = templateConfig.useTemplate
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
    ) {
        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Text(
                        text = stringResource(R.string.keyword_settings),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                    )

                    Text(
                        text = stringResource(R.string.keyword_settings_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AppSpacing.large),
                    )

                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text(stringResource(R.string.keywords_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        enabled = !noFilterMode,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                // Only dismiss the keyboard. Persisting here would bypass the
                                // confirmation the Save button asks for.
                                focusManager.clearFocus()
                            }),
                    )

                    if (noFilterMode) {
                        Text(
                            text = stringResource(R.string.no_filter_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = AppSpacing.small),
                        )
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Text(
                        text = stringResource(R.string.matching_settings),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                    )

                    // No-filter forward mode
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = if (noFilterMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = AppSpacing.large),
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.medium),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (noFilterMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.small))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.no_filter_forward_mode),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (noFilterMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        if (noFilterMode) {
                                            stringResource(R.string.no_filter_mode_desc_enabled)
                                        } else {
                                            stringResource(
                                                R.string.no_filter_mode_desc_disabled,
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = noFilterMode,
                                    onCheckedChange = { newValue ->
                                        if (newValue) {
                                            showNoFilterWarning = true
                                        } else {
                                            scope.launch {
                                                preferencesRepository.setNoFilterForwardMode(false)
                                                noFilterMode = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = caseSensitive,
                            onCheckedChange = { caseSensitive = it },
                            enabled = !noFilterMode,
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                        Text(
                            stringResource(R.string.case_sensitive),
                            color = if (noFilterMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.large))

                    Text(
                        text = stringResource(R.string.matching_logic),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                        color = if (noFilterMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = matchLogic == MatchLogic.ANY,
                                onClick = { matchLogic = MatchLogic.ANY },
                                enabled = !noFilterMode,
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.small))
                            Text(
                                stringResource(R.string.contains_any_keyword),
                                color = if (noFilterMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = matchLogic == MatchLogic.ALL,
                                onClick = { matchLogic = MatchLogic.ALL },
                                enabled = !noFilterMode,
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.small))
                            Text(
                                stringResource(R.string.contains_all_keywords),
                                color = if (noFilterMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Text(
                        text = stringResource(R.string.current_template),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                    )

                    if (templateConfig.keywords.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_keywords_set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text =
                                stringResource(
                                    R.string.keywords_label,
                                    templateConfig.keywords.joinToString(", "),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.matching_logic_label,
                                    if (templateConfig.matchLogic == MatchLogic.ANY) {
                                        stringResource(R.string.contains_any)
                                    } else {
                                        stringResource(
                                            R.string.contains_all,
                                        )
                                    },
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.case_sensitive_label,
                                    if (templateConfig.caseSensitive) {
                                        stringResource(R.string.yes)
                                    } else {
                                        stringResource(
                                            R.string.no,
                                        )
                                    },
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = useTemplate,
                            onCheckedChange = { useTemplate = it },
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                        Text(
                            text = stringResource(R.string.use_email_template),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    if (useTemplate) {
                        Spacer(modifier = Modifier.height(AppSpacing.large))

                        Text(
                            text = stringResource(R.string.email_template_settings),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = AppSpacing.small),
                        )

                        Text(
                            text = stringResource(R.string.template_placeholders_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = AppSpacing.large),
                        )

                        OutlinedTextField(
                            value = emailTemplate,
                            onValueChange = { emailTemplate = it },
                            label = { Text(stringResource(R.string.email_template_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 6,
                            maxLines = 10,
                        )

                        Spacer(modifier = Modifier.height(AppSpacing.small))
                        val defaultEmailTemplateSms =
                            stringResource(R.string.default_email_template_sms)
                        OutlinedButton(
                            onClick = {
                                emailTemplate = defaultEmailTemplateSms
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.use_default_template))
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.small))
                        val defaultEmailTemplateVerification =
                            stringResource(R.string.default_email_template_verification)
                        OutlinedButton(
                            onClick = {
                                emailTemplate =
                                    defaultEmailTemplateVerification
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.verification_code_template))
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    // Hold the pending config until the user confirms, so an accidental tap
                    // cannot overwrite a working configuration.
                    pendingConfig =
                        templateConfig.copy(
                            keywords = keywords.split("\n").filter { it.isNotBlank() },
                            caseSensitive = caseSensitive,
                            matchLogic = matchLogic,
                            emailTemplate = emailTemplate,
                            useTemplate = useTemplate,
                        )
                    showSaveConfirmDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(modifier = Modifier.width(AppSpacing.small))
                Text(stringResource(R.string.save_settings))
            }
        }
    }

    if (showSaveConfirmDialog) {
        val pending = pendingConfig
        ConfirmSaveDialog(
            title = stringResource(R.string.confirm_save_title),
            message = stringResource(R.string.confirm_save_template_message),
            onConfirm = {
                showSaveConfirmDialog = false
                if (pending != null) {
                    viewModel.updateTemplateConfig(pending)
                    if (!serviceEnabled) {
                        showSavedServiceStoppedDialog = true
                    }
                }
            },
            onDismiss = {
                showSaveConfirmDialog = false
                pendingConfig = null
            },
        )
    }

    // Saved settings while service stopped warning dialog
    if (showSavedServiceStoppedDialog) {
        AlertDialog(
            onDismissRequest = { showSavedServiceStoppedDialog = false },
            title = { Text(stringResource(R.string.template_saved_title)) },
            text = { Text(stringResource(R.string.template_saved_service_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSavedServiceStoppedDialog = false
                        onOpenServiceStatus()
                    },
                ) {
                    Text(stringResource(R.string.template_saved_enable_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavedServiceStoppedDialog = false }) {
                    Text(stringResource(R.string.template_saved_dismiss))
                }
            },
        )
    }

    // No-filter mode warning dialog
    if (showNoFilterWarning) {
        AlertDialog(
            onDismissRequest = { showNoFilterWarning = false },
            title = { Text(stringResource(R.string.security_warning)) },
            text = {
                Text(stringResource(R.string.no_filter_warning_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            preferencesRepository.setNoFilterForwardMode(true)
                            noFilterMode = true
                            showNoFilterWarning = false
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.continue_enable),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNoFilterWarning = false },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
