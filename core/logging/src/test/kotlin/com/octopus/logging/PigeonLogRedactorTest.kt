package com.octopus.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the log governance rules.
 *
 * The property that matters: a release build's log file may be handed to a
 * stranger and must not be a place a message body can be recovered from. A round
 * trip is not enough to prove that, because the redaction path and the
 * pass-through path would both pass it, so the assertions here are about
 * absence, and one is explicitly about the flag that controls it.
 */
class PigeonLogRedactorTest {
    @After
    fun resetFlag() {
        PigeonLogRedactor.allowVerboseBodies(false)
    }

    @Test
    fun aSenderTagIsStableSoEventsStayCorrelatable() {
        assertEquals(PigeonLogRedactor.tag("+8613800138000"), PigeonLogRedactor.tag("+8613800138000"))
        assertEquals(PigeonLogRedactor.tag("+8613800138000"), PigeonLogRedactor.tag("  +8613800138000  "))
    }

    @Test
    fun aSenderTagDoesNotContainTheNumber() {
        val tag = PigeonLogRedactor.tag("+8613800138000")

        assertFalse(tag.contains("86138"))
        assertTrue(tag.startsWith("id:"))
    }

    @Test
    fun differentSendersGetDifferentTags() {
        assertTrue(PigeonLogRedactor.tag("+8613800138000") != PigeonLogRedactor.tag("+8613900139000"))
    }

    /**
     * The shape summary is what makes a keyword mismatch diagnosable without the
     * body, so it has to separate the cases that actually get reported.
     */
    @Test
    fun theShapeSeparatesACodeFromProse() {
        val code = PigeonLogRedactor.describe("【中国银行】验证码 493812")
        val prose = PigeonLogRedactor.describe("Your delivery is scheduled for tomorrow morning")

        assertTrue("a passcode should report its digit run: $code", code.contains("digit_run=6"))
        assertFalse("prose should not look like a passcode: $prose", prose.contains("digit_run"))
        assertTrue(code.contains("cjk="))
        assertTrue(prose.contains("latin="))
    }

    @Test
    fun theShapeNeverContainsTheBody() {
        val body = "您的验证码是 493812，请勿泄露"
        val shape = PigeonLogRedactor.describe(body)

        assertFalse(shape.contains("493812"))
        assertFalse(shape.contains("请勿泄露"))
        // The digit count survives, which is the point.
        assertTrue(shape.contains("digits=6"))
    }

    @Test
    fun anEmptyBodyIsDescribedAsEmpty() {
        assertEquals("empty", PigeonLogRedactor.describe(""))
        assertEquals("empty", PigeonLogRedactor.describe(null))
    }

    @Test
    fun aBodyIsRedactedByDefault() {
        val body = "code 493812"

        // The default has to be the safe one: a caller that forgets to configure
        // anything must not leak, so this is what an unconfigured build does.
        assertEquals(PigeonLogRedactor.describe(body), PigeonLogRedactor.body(body))
    }

    @Test
    fun verboseBodiesAreOnlyReturnedWhenExplicitlyAllowed() {
        val body = "code 493812"

        PigeonLogRedactor.allowVerboseBodies(true)
        assertEquals(body, PigeonLogRedactor.body(body))

        PigeonLogRedactor.allowVerboseBodies(false)
        assertEquals(PigeonLogRedactor.describe(body), PigeonLogRedactor.body(body))
    }

    /**
     * The flag widens what a development build shows; it must not be something a
     * shipped build can be talked into.
     */
    @Test
    fun theVerboseFlagDefaultsToOff() {
        assertFalse(PigeonLogRedactor.verboseBodiesAllowed())
    }
}
