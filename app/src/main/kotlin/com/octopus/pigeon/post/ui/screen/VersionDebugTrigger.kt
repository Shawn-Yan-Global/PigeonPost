package com.octopus.pigeon.post.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.octopus.pigeon.post.R
import com.octopus.ui.spacing.AppSpacing
import kotlinx.coroutines.delay

/**
 * Drawer footer showing the app version, and one of the ways into the debug
 * menu. Three long presses, then the password.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VersionDebugTrigger(
    onUnlock: () -> Unit,
    unlocked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var versionName by remember { mutableStateOf("") }
    var versionCode by remember { mutableIntStateOf(0) }

    var longPressCount by remember { mutableIntStateOf(0) }
    var showPasswordPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = info.versionName.orEmpty()
            versionCode = info.longVersionCode.toInt()
        }
    }

    // The count resets if the user stops pressing, so a stray long press does
    // not unlock the menu much later.
    LaunchedEffect(longPressCount) {
        if (longPressCount in 1 until REQUIRED_LONG_PRESSES) {
            delay(TRIGGER_WINDOW_MS)
            longPressCount = 0
        }
    }

    if (showPasswordPrompt) {
        DebugPasswordPrompt(
            onUnlocked = {
                longPressCount = 0
                showPasswordPrompt = false
                onUnlock()
            },
            onDismiss = {
                longPressCount = 0
                showPasswordPrompt = false
            },
        )
    }

    // The whole row is the target, not just the glyphs. A version number is
    // small text in the corner of a sheet, and asking someone to land three
    // long presses on six pixels of descender is a good way to guarantee they
    // never find it at all.
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = {
                        longPressCount++
                        if (longPressCount >= REQUIRED_LONG_PRESSES) {
                            longPressCount = 0
                            // Still three long presses even when already
                            // unlocked. The footer doubles as the way in, and
                            // shortening that would hand the menu to anyone
                            // holding the phone.
                            if (unlocked) onUnlock() else showPasswordPrompt = true
                        }
                    },
                ).padding(vertical = AppSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_version_format, versionName, versionCode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val REQUIRED_LONG_PRESSES = 3
private const val TRIGGER_WINDOW_MS = 3_000L

/** The smallest comfortable long press target, and the accessibility floor. */
private val MIN_TOUCH_TARGET = 48.dp
