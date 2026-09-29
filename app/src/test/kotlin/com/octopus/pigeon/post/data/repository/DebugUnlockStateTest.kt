package com.octopus.pigeon.post.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugUnlockStateTest {
    @Test
    fun `starts locked`() {
        // Shared across tests in one JVM, so reset rather than assume.
        DebugUnlockState.lock()
        assertFalse(DebugUnlockState.isUnlocked.value)
    }

    @Test
    fun `unlock then lock round trips`() {
        DebugUnlockState.lock()
        DebugUnlockState.unlock()
        assertTrue(DebugUnlockState.isUnlocked.value)
        DebugUnlockState.lock()
        assertFalse(DebugUnlockState.isUnlocked.value)
    }

    @Test
    fun `unlocking twice stays unlocked`() {
        DebugUnlockState.unlock()
        DebugUnlockState.unlock()
        assertTrue(DebugUnlockState.isUnlocked.value)
        DebugUnlockState.lock()
    }
}
