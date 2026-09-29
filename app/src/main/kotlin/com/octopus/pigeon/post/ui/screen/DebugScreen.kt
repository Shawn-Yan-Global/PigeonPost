package com.octopus.pigeon.post.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.repository.DebugAuth
import com.octopus.pigeon.post.data.repository.DebugUnlockState
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.diagnostic.DiagnosticPackageBuilder
import com.octopus.pigeon.post.diagnostic.DiagnosticReportCollector
import com.octopus.pigeon.post.diagnostic.DiagnosticShareLauncher
import com.octopus.pigeon.post.diagnostic.DiagnosticState
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel
import com.octopus.pigeon.post.util.HideIconController
import com.octopus.pigeon.post.util.launcherHideInstructions
import com.octopus.ui.spacing.AppSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subject = stringResource(R.string.diagnostic_subject)

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showHideGuide by remember { mutableStateOf(false) }
    var showConfirmDisable by remember { mutableStateOf(false) }
    var confirmWord by remember { mutableStateOf("") }
    var diagnosticState by remember { mutableStateOf<DiagnosticState>(DiagnosticState.Idle) }
    var showAdvancedSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.debug_panel),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                // Icon hiding, the support package and the password change are
                // things you do once, not things you scroll through while
                // testing. They live behind an overflow so the panel below keeps
                // the full height it had before.
                actions = {
                    IconButton(onClick = { showAdvancedSheet = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.debug_more_options),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            )
        },
    ) { paddingValues ->
        // The panel gets the whole screen, as it did before the launcher and
        // support tools were added. SmsTestScreen is a LazyColumn and scrolls
        // itself, so it is not wrapped in a second scroll container.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            SmsTestScreen(viewModel = viewModel)
        }
    }

    if (showAdvancedSheet) {
        AdvancedToolsSheet(
            onDismiss = { showAdvancedSheet = false },
            onShowHideGuide = {
                showAdvancedSheet = false
                showHideGuide = true
            },
            onShowConfirmDisable = {
                showAdvancedSheet = false
                showConfirmDisable = true
            },
            onShowPasswordDialog = {
                showAdvancedSheet = false
                showPasswordDialog = true
            },
            diagnosticState = diagnosticState,
            onCreatePackage = {
                scope.launch {
                    diagnosticState = DiagnosticState.Building
                    diagnosticState =
                        runCatching {
                            val report = DiagnosticReportCollector.collect(context)
                            val archive = DiagnosticPackageBuilder.build(context, report)
                            DiagnosticPackageBuilder.passwordFor(context) to archive
                        }.fold(
                            onSuccess = { DiagnosticState.Ready(it.second, it.first) },
                            onFailure = { DiagnosticState.Failed },
                        )
                }
            },
            onSendPackage = { archive ->
                DiagnosticShareLauncher.launch(
                    context = context,
                    archive = archive,
                    subject = subject,
                )
                diagnosticState = DiagnosticState.Idle
            },
        )
    }

    if (showHideGuide) {
        AlertDialog(
            onDismissRequest = { showHideGuide = false },
            title = { Text(stringResource(R.string.hide_mode_a_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    launcherHideInstructions(context).forEach { step ->
                        Text(text = step, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = stringResource(R.string.hide_mode_a_caveat),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHideGuide = false }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }

    if (showConfirmDisable) {
        val expected = stringResource(R.string.hide_mode_b_confirm_word)
        AlertDialog(
            onDismissRequest = {
                showConfirmDisable = false
                confirmWord = ""
            },
            title = { Text(stringResource(R.string.hide_mode_b_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(
                        text = stringResource(R.string.hide_mode_b_risk),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.hide_mode_b_restore_hint, expected),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    OutlinedTextField(
                        value = confirmWord,
                        onValueChange = { confirmWord = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.hide_mode_b_confirm_label, expected)) },
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = confirmWord.trim() == expected,
                    onClick = {
                        HideIconController.disableLauncherEntry(context)
                        showConfirmDisable = false
                        confirmWord = ""
                    },
                ) {
                    Text(stringResource(R.string.hide_mode_b_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmDisable = false
                        confirmWord = ""
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showPasswordDialog) {
        ChangeDebugPasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onChanged = { showPasswordDialog = false },
        )
    }
}

/** Rotates the debug password. Requires the current one, so the gate holds. */
@Composable
private fun ChangeDebugPasswordDialog(
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { PreferencesRepository(context) }
    val expectedHash by remember { repository.debugPasswordHash }
        .collectAsState(initial = DebugAuth.DEFAULT_PASSWORD_HASH)

    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var currentVisible by remember { mutableStateOf(false) }
    var nextVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val incorrect = stringResource(R.string.debug_password_incorrect)
    val mismatch = stringResource(R.string.debug_change_password_mismatch)
    val tooShort = stringResource(R.string.debug_change_password_too_short)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debug_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                OutlinedTextField(
                    value = current,
                    onValueChange = {
                        current = it
                        error = null
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.debug_change_password_current)) },
                    visualTransformation =
                        if (currentVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { passwordVisibilityIcon(currentVisible) { currentVisible = it } },
                )
                OutlinedTextField(
                    value = next,
                    onValueChange = {
                        next = it
                        error = null
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.debug_change_password_new)) },
                    visualTransformation =
                        if (nextVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { passwordVisibilityIcon(nextVisible) { nextVisible = it } },
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = {
                        confirm = it
                        error = null
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.debug_change_password_confirm)) },
                    visualTransformation =
                        if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { passwordVisibilityIcon(confirmVisible) { confirmVisible = it } },
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        !DebugAuth.matches(current, expectedHash) ->
                            error = incorrect

                        next.length < 4 -> error = tooShort
                        next != confirm -> error = mismatch
                        else ->
                            scope.launch {
                                repository.setDebugPasswordHash(DebugAuth.hash(next))
                                // Changing the password ends the session that
                                // unlocked it, so the next entry has to use the
                                // new one.
                                DebugUnlockState.lock()
                                onChanged()
                            }
                    }
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * The one-off tools, behind the overflow action.
 *
 * Hiding the launcher icon, building a support package and rotating the debug
 * password are each something a person does once and then never again. They are
 * long, they carry warnings worth reading, and none of them is part of testing
 * the forward. Leaving them in the scroll meant the panel that developers
 * actually use got a screenful of prose pushed into it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedToolsSheet(
    onDismiss: () -> Unit,
    onShowHideGuide: () -> Unit,
    onShowConfirmDisable: () -> Unit,
    onShowPasswordDialog: () -> Unit,
    diagnosticState: DiagnosticState,
    onCreatePackage: () -> Unit,
    onSendPackage: (java.io.File) -> Unit,
) {
    val context = LocalContext.current
    val subject = stringResource(R.string.diagnostic_subject)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.large)
                    .padding(bottom = AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
        ) {
            Text(
                text = stringResource(R.string.debug_launcher_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // Method 1: reversible, launcher driven. Nothing is changed by the
            // app, so there is nothing that can be lost.
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
                ) {
                    Text(
                        text = stringResource(R.string.hide_mode_a_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.hide_mode_a_summary),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = onShowHideGuide,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.hide_mode_a_instructions))
                    }
                }
            }

            // Method 2: the app disables its own launcher entry. One way only.
            Card {
                Column(
                    modifier = Modifier.padding(AppSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
                ) {
                    Text(
                        text = stringResource(R.string.hide_mode_b_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.hide_mode_b_summary),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val launcherEnabled = HideIconController.isLauncherEntryEnabled(context)
                    OutlinedButton(
                        onClick = onShowConfirmDisable,
                        enabled = launcherEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.hide_mode_b_action))
                    }
                    // Once the entry is gone there is no way back into this
                    // screen, so the way out has to be on screen too.
                    if (!launcherEnabled) {
                        Text(
                            text = stringResource(R.string.hide_mode_b_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val command = HideIconController.restoreAdbCommand(context)
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.hide_restore_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.hide_restore_summary),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = command,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        // The command has to reach a terminal, so a copy button
                        // is the difference between usable and not.
                        TextButton(
                            onClick = { copyToClipboard(context, command) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.copy))
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.debug_support_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.debug_support_summary),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onCreatePackage,
                enabled = diagnosticState !is DiagnosticState.Building,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (diagnosticState is DiagnosticState.Building) {
                            R.string.debug_support_building
                        } else {
                            R.string.debug_support_action
                        },
                    ),
                )
            }
            when (val state = diagnosticState) {
                is DiagnosticState.Failed ->
                    Text(
                        text = stringResource(R.string.debug_support_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                is DiagnosticState.Ready ->
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        Text(
                            text = stringResource(R.string.debug_support_ready),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.debug_support_password, state.password),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        OutlinedButton(
                            onClick = { onSendPackage(state.archive) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.debug_support_send))
                        }
                    }

                DiagnosticState.Idle, DiagnosticState.Building -> Unit
            }

            HorizontalDivider()

            OutlinedButton(
                onClick = onShowPasswordDialog,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.debug_change_password))
            }
        }
    }
}

/**
 * Puts [text] on the system clipboard.
 *
 * Deliberately uses the framework clipboard rather than Compose's, which is
 * deprecated and whose replacement wants an opt-in.
 */
private fun copyToClipboard(
    context: Context,
    text: String,
) {
    context
        .getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("command", text))
}

/**
 * The eye on a password field. Each field gets its own toggle, so showing the
 * new password does not reveal the current one.
 */
@Composable
private fun passwordVisibilityIcon(
    visible: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    IconButton(onClick = { onToggle(!visible) }) {
        Icon(
            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription =
                stringResource(if (visible) R.string.hide_password else R.string.show_password),
        )
    }
}
