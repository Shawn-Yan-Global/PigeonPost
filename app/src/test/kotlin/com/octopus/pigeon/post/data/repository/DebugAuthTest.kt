package com.octopus.pigeon.post.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The password gate is only a gate if it actually works.
 *
 * The bug this covers was a property declared after the one that used it, so
 * the very first call into the object read a null array and threw. It compiled
 * cleanly, every test that did not touch the object passed, and the menu was
 * unreachable in practice because the prompt crashed the activity instead of
 * opening. Nothing about a unit test run would have found it on its own.
 */
class DebugAuthTest {
    @Test
    fun `the default verifier is present on first touch`() {
        // Touching the class is enough to run every property initializer. The
        // injected value is checked for shape rather than against a password:
        // the app no longer knows the plaintext, and neither should a test.
        val hash = DebugAuth.DEFAULT_PASSWORD_HASH
        assertEquals(64, hash.length)
        assertTrue("expected hex, got $hash", hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun `hashing is stable across calls`() {
        assertEquals(DebugAuth.hash("hunter2"), DebugAuth.hash("hunter2"))
        assertEquals(64, DebugAuth.hash("hunter2").length)
    }

    @Test
    fun `a password matches its own hash`() {
        assertTrue(DebugAuth.matches("correct horse", DebugAuth.hash("correct horse")))
    }

    @Test
    fun `a wrong password does not match`() {
        val stored = DebugAuth.hash("correct horse")
        assertFalse(DebugAuth.matches("correct hors", stored))
        assertFalse(DebugAuth.matches("Correct horse", stored))
        assertFalse(DebugAuth.matches("", stored))
    }

    @Test
    fun `a length mismatch is rejected without throwing`() {
        // matches bails out early on a different length, so a malformed stored
        // value must not turn into an index error.
        assertFalse(DebugAuth.matches("anything", "abc"))
    }

    @Test
    fun `different passwords hash differently`() {
        assertNotEquals(DebugAuth.hash("one"), DebugAuth.hash("two"))
    }

    @Test
    fun `random ids are hex and the requested length`() {
        val id = DebugAuth.randomId(8)
        assertEquals(16, id.length)
        assertTrue(id.all { it in "0123456789abcdef" })
        assertNotEquals(id, DebugAuth.randomId(8))
    }
}
