package com.octopus.pigeon.post.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.model.EmailConfig
import com.octopus.pigeon.post.ui.component.ConfirmSaveDialog
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel
import com.octopus.pigeon.post.ui.viewmodel.TestEmailResult
import com.octopus.ui.spacing.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailConfigScreen(viewModel: MainViewModel) {
    val emailConfig by viewModel.emailConfig.collectAsState()
    val testEmailResult by viewModel.testEmailResult.collectAsState()

    // Get default email subject with proper i18n support
    val defaultEmailSubject = stringResource(R.string.sms_forward_notification)

    var smtpServer by remember { mutableStateOf(emailConfig.smtpServer) }
    var smtpPort by remember { mutableStateOf(emailConfig.smtpPort.toString()) }
    var senderEmail by remember { mutableStateOf(emailConfig.senderEmail) }
    var password by remember { mutableStateOf(emailConfig.password) }
    var recipientEmail by remember { mutableStateOf(emailConfig.recipientEmail) }
    var emailSubject by remember(emailConfig.emailSubject, defaultEmailSubject) {
        mutableStateOf(
            emailConfig.emailSubject.ifEmpty { defaultEmailSubject },
        )
    }
    var showGuide by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    var pendingConfig by remember { mutableStateOf<EmailConfig?>(null) }
    var sendTestAfterSave by remember { mutableStateOf(false) }

    LaunchedEffect(emailConfig, defaultEmailSubject) {
        smtpServer = emailConfig.smtpServer
        smtpPort = emailConfig.smtpPort.toString()
        senderEmail = emailConfig.senderEmail
        password = emailConfig.password
        recipientEmail = emailConfig.recipientEmail
        emailSubject = emailConfig.emailSubject.ifEmpty { defaultEmailSubject }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
    ) {
        // Common email configuration
        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Text(
                        text = stringResource(R.string.common_email_config),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                    )

                    Text(
                        text = stringResource(R.string.email_type_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AppSpacing.large),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                    ) {
                        OutlinedButton(
                            onClick = {
                                smtpServer = "smtp.qq.com"
                                smtpPort = "${com.octopus.pigeon.post.util.SmtpPorts.STARTTLS}"
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.qq_email))
                        }

                        OutlinedButton(
                            onClick = {
                                smtpServer = "smtp.163.com"
                                smtpPort = "${com.octopus.pigeon.post.util.SmtpPorts.STARTTLS}"
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.netease_163_email))
                        }
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                    ) {
                        OutlinedButton(
                            onClick = {
                                smtpServer = "smtp.office365.com"
                                smtpPort = "${com.octopus.pigeon.post.util.SmtpPorts.STARTTLS}"
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Outlook")
                        }

                        OutlinedButton(
                            onClick = {
                                smtpServer = "smtp.gmail.com"
                                smtpPort = "${com.octopus.pigeon.post.util.SmtpPorts.STARTTLS}"
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Gmail")
                        }
                    }
                }
            }
        }

        // Configuration guide
        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.padding(end = AppSpacing.small),
                            )
                            Text(
                                text = stringResource(R.string.email_config_guide),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }

                        IconButton(onClick = { showGuide = !showGuide }) {
                            Icon(
                                if (showGuide) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription =
                                    if (showGuide) {
                                        stringResource(R.string.collapse_guide)
                                    } else {
                                        stringResource(
                                            R.string.expand_guide,
                                        )
                                    },
                            )
                        }
                    }

                    if (!showGuide) {
                        Text(
                            text = stringResource(R.string.guide_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = AppSpacing.extraSmall),
                        )
                    }

                    AnimatedVisibility(visible = showGuide) {
                        EmailGuideContent()
                    }
                }
            }
        }

        // SMTP server configuration
        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Text(
                        text = stringResource(R.string.smtp_server_config),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                    )

                    OutlinedTextField(
                        value = smtpServer,
                        onValueChange = { smtpServer = it },
                        label = { Text(stringResource(R.string.smtp_server_address)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Next,
                                keyboardType = KeyboardType.Uri,
                            ),
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    OutlinedTextField(
                        value = smtpPort,
                        onValueChange = { smtpPort = it },
                        label = { Text(stringResource(R.string.port)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Next,
                                keyboardType = KeyboardType.Number,
                            ),
                    )
                }
            }
        }

        // Email account configuration
        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Text(
                        text = stringResource(R.string.email_account_config),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                    )

                    OutlinedTextField(
                        value = senderEmail,
                        onValueChange = { senderEmail = it },
                        label = { Text(stringResource(R.string.sender_email)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Next,
                                keyboardType = KeyboardType.Email,
                            ),
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.password_auth_code)) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Next,
                            ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    OutlinedTextField(
                        value = recipientEmail,
                        onValueChange = { recipientEmail = it },
                        label = { Text(stringResource(R.string.recipient_email)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Next,
                                keyboardType = KeyboardType.Email,
                            ),
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    OutlinedTextField(
                        value = emailSubject,
                        onValueChange = { emailSubject = it },
                        label = { Text(stringResource(R.string.email_subject)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Done,
                            ),
                    )
                }
            }
        }

        // Test email function
        item {
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                ) {
                    Text(
                        text = stringResource(R.string.test_email),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                    )

                    Text(
                        text = stringResource(R.string.send_test_email_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AppSpacing.large),
                    )

                    when (testEmailResult) {
                        is TestEmailResult.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(AppSpacing.extraLarge),
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.small))
                                Text(stringResource(R.string.sending_test_email))
                            }
                        }

                        is TestEmailResult.Success -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        Icons.Default.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.small))
                                    Text(
                                        stringResource(R.string.test_email_success),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                Spacer(modifier = Modifier.height(AppSpacing.medium))

                                // A successful test leaves nothing on screen to act on, so
                                // without this the card is a dead end and a second test cannot
                                // be started at all. Mirrors the failure state below. The
                                // configuration was already saved to get here, so this resends
                                // directly instead of asking to save again.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.clearTestEmailResult()
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.done))
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.sendTestEmail()
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null,
                                        )
                                        Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                                        Text(stringResource(R.string.send_again))
                                    }
                                }
                            }
                        }

                        is TestEmailResult.Error -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.small))
                                    Text(
                                        stringResource(
                                            R.string.send_failed,
                                            (testEmailResult as TestEmailResult.Error).message,
                                        ),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }

                                Spacer(modifier = Modifier.height(AppSpacing.medium))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.clearTestEmailResult()
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.cancel))
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.sendTestEmail()
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null,
                                        )
                                        Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                        }

                        null -> {
                            Button(
                                onClick = {
                                    // The test email is sent with the stored configuration, so the
                                    // form has to be persisted first. Ask before overwriting it
                                    // instead of silently replacing a working setup.
                                    pendingConfig =
                                        EmailConfig(
                                            smtpServer = smtpServer,
                                            smtpPort = smtpPort.toIntOrNull() ?: 587,
                                            senderEmail = senderEmail,
                                            password = password,
                                            recipientEmail = recipientEmail,
                                            emailSubject = emailSubject,
                                        )
                                    sendTestAfterSave = true
                                    showSaveConfirmDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled =
                                    smtpServer.isNotBlank() &&
                                        senderEmail.isNotBlank() &&
                                        password.isNotBlank() &&
                                        recipientEmail.isNotBlank(),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(AppSpacing.small))
                                Text(stringResource(R.string.send_test_email))
                            }
                        }
                    }
                }
            }
        }

        // Save configuration button
        item {
            Button(
                onClick = {
                    // Hold the pending config until the user confirms, so an accidental tap
                    // cannot overwrite a working mail configuration.
                    pendingConfig =
                        EmailConfig(
                            smtpServer = smtpServer,
                            smtpPort = smtpPort.toIntOrNull() ?: 587,
                            senderEmail = senderEmail,
                            password = password,
                            recipientEmail = recipientEmail,
                            emailSubject = emailSubject,
                        )
                    showSaveConfirmDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(AppSpacing.small))
                Text(stringResource(R.string.save_config))
            }
        }
    }

    if (showSaveConfirmDialog) {
        val pending = pendingConfig
        val alsoSendTestEmail = sendTestAfterSave
        ConfirmSaveDialog(
            title = stringResource(R.string.confirm_save_title),
            message = stringResource(R.string.confirm_save_email_message),
            onConfirm = {
                showSaveConfirmDialog = false
                sendTestAfterSave = false
                pending?.let {
                    viewModel.updateEmailConfig(it)
                    if (alsoSendTestEmail) {
                        viewModel.sendTestEmail()
                    }
                }
                pendingConfig = null
            },
            onDismiss = {
                showSaveConfirmDialog = false
                sendTestAfterSave = false
                pendingConfig = null
            },
        )
    }
}

@Composable
fun EmailGuideContent() {
    var selectedGuide by remember { mutableStateOf("qq") }

    Column(
        modifier = Modifier.padding(top = AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
    ) {
        // Email type selection
        Text(
            text = stringResource(R.string.select_email_type),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = AppSpacing.small),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
        ) {
            OutlinedButton(
                onClick = { selectedGuide = "qq" },
                modifier = Modifier.weight(1f),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor =
                            if (selectedGuide == "qq") {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                    ),
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                Text(stringResource(R.string.qq_email))
            }

            OutlinedButton(
                onClick = { selectedGuide = "outlook" },
                modifier = Modifier.weight(1f),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor =
                            if (selectedGuide == "outlook") {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                    ),
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(modifier = Modifier.width(AppSpacing.extraSmall))
                Text("Outlook")
            }
        }

        // Configuration description content
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.large),
            ) {
                when (selectedGuide) {
                    "qq" -> QQEmailGuideContent()
                    "outlook" -> OutlookGuideContent()
                }
            }
        }
    }
}

@Composable
fun QQEmailGuideContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        Text(
            text = stringResource(R.string.qq_email_smtp_tutorial),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource(R.string.step_1_enable_smtp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.qq_smtp_step1_desc),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(R.string.step_2_get_auth_code),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.qq_smtp_step2_desc),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(R.string.step_3_fill_config),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.qq_smtp_config_desc),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun OutlookGuideContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        Text(
            text = stringResource(R.string.outlook_email_smtp_tutorial),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource(R.string.step_1_enable_2fa),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.outlook_smtp_2fa_desc),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(R.string.step_2_create_app_password),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.outlook_app_password_desc),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(R.string.step_3_fill_config),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.outlook_final_config_desc),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
