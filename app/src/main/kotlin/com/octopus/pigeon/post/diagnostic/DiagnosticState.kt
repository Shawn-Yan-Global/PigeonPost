package com.octopus.pigeon.post.diagnostic

import java.io.File

/**
 * Where the diagnostic package screen is in its cycle.
 *
 * Building is worth distinguishing from failing: the work happens on disk and
 * takes a moment, and without a separate state the button would look like
 * nothing happened when the user tapped it.
 */
sealed interface DiagnosticState {
    /** Nothing generated yet. */
    data object Idle : DiagnosticState

    /** Collecting config and writing the archive. */
    data object Building : DiagnosticState

    /**
     * The archive exists and is ready to be shared.
     *
     * [password] is held so the screen can tell the user what to type if the
     * mail app asks. It is the applicationId, not a secret.
     */
    data class Ready(
        val archive: File,
        val password: String,
    ) : DiagnosticState

    /** Building threw; the message is in the log, not shown, since it can be technical. */
    data object Failed : DiagnosticState
}
