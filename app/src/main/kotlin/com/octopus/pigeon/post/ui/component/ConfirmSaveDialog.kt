package com.octopus.pigeon.post.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.octopus.pigeon.post.R

/**
 * Asks the user to confirm a settings change before it is persisted.
 *
 * Saving a settings form is not trivially reversible, and several of the screens in this app persist
 * to DataStore as soon as a button is pressed. Forcing a deliberate second tap prevents an
 * accidental tap from silently overwriting a working configuration.
 *
 * @param title Dialog title.
 * @param message Body explaining what will happen.
 * @param confirmLabel Label of the accepting button.
 * @param onConfirm Invoked only when the user accepts.
 * @param onDismiss Invoked when the user cancels or taps outside the dialog.
 */
@Composable
fun ConfirmSaveDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.confirm_save),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}
