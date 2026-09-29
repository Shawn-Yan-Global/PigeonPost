package com.octopus.pigeon.post.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.repository.DebugAuth
import com.octopus.pigeon.post.data.repository.DebugUnlockState
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.ui.spacing.AppSpacing

/**
 * The password prompt for the debug menu.
 *
 * Every way in goes through this, not just the version number. An entry point
 * that skips it is a way in for anyone who can see it, which is the whole
 * reason the prompt exists. The input is checked against the stored hash and
 * never against a plaintext literal.
 */
@Composable
fun DebugPasswordPrompt(
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val expectedHash by remember { PreferencesRepository(context).debugPasswordHash }
        .collectAsState(initial = DebugAuth.DEFAULT_PASSWORD_HASH)

    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val incorrect = stringResource(R.string.debug_password_incorrect)

    AlertDialog(
        onDismissRequest = {
            input = ""
            error = null
            onDismiss()
        },
        title = { Text(stringResource(R.string.debug_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    singleLine = true,
                    isError = error != null,
                    visualTransformation =
                        if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    label = { Text(stringResource(R.string.debug_password_label)) },
                    trailingIcon = {
                        // Worth having here: the prompt rejects a wrong password
                        // but never echoes it, so without this there is no way
                        // to tell a typo from a mistyped capital.
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector =
                                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription =
                                    stringResource(
                                        if (visible) R.string.hide_password else R.string.show_password,
                                    ),
                            )
                        }
                    },
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (DebugAuth.matches(input, expectedHash)) {
                        input = ""
                        error = null
                        // Unlocking here rather than at each call site, so a new
                        // way into the menu cannot ask for the password and then
                        // forget to remember it.
                        DebugUnlockState.unlock()
                        onUnlocked()
                    } else {
                        error = incorrect
                    }
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    input = ""
                    error = null
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
