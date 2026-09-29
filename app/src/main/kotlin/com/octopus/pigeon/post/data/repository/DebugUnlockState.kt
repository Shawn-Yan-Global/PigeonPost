package com.octopus.pigeon.post.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whether the debug menu has been unlocked on this install.
 *
 * Asking for the password on every visit is how a gate gets abandoned rather
 * than respected, so one correct password is remembered from then on. It
 * survives the app being killed, which is the point of storing it.
 *
 * Two things clear it:
 *  - [lock], called when the password is changed, so the next visit has to use
 *    the new one
 *  - the user clearing the app's data, or uninstalling
 *
 * A one-line tradeoff worth stating: while this is on, anyone holding an
 * unlocked phone can get into the debug menu, including the control that hides
 * the launcher icon, without knowing the password. That is what was asked
 * for. If it ever needs to be tighter, dropping the stored flag and keeping it
 * in memory is a one-file change.
 *
 * Seeded from DataStore by [attach] at startup, so the UI has the right answer
 * from the first frame rather than flashing the prompt on the way in.
 */
object DebugUnlockState {
    private val unlocked = MutableStateFlow(false)

    val isUnlocked: StateFlow<Boolean> = unlocked.asStateFlow()

    private var repository: PreferencesRepository? = null

    /** Starts mirroring the stored value. Safe to call more than once. */
    fun attach(preferencesRepository: PreferencesRepository) {
        if (repository != null) return
        repository = preferencesRepository
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            preferencesRepository.debugUnlocked.collect { stored ->
                unlocked.value = stored
            }
        }
    }

    fun unlock() {
        unlocked.value = true
        persist(true)
    }

    fun lock() {
        unlocked.value = false
        persist(false)
    }

    private fun persist(value: Boolean) {
        val target = repository ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            target.setDebugUnlocked(value)
        }
    }
}
