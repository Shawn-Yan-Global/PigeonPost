package com.octopus.pigeon.post.diagnostic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * The diagnostic report says whether the active window covers right now, and
 * says it without disclosing when the window is. These tests pin the boundary
 * behaviour, because an off-by-one here produces the worst kind of report: one
 * that confidently claims forwarding should be happening when it cannot.
 */
class ActiveWindowTest {
    @Test
    fun `a daytime window covers a moment in the middle`() {
        assertTrue(within(8, 0, 22, 30, 12, 0))
    }

    @Test
    fun `both ends of a daytime window are inside`() {
        assertTrue(within(8, 0, 22, 30, 8, 0))
        assertTrue(within(8, 0, 22, 30, 22, 30))
    }

    @Test
    fun `just outside a daytime window is outside`() {
        assertFalse(within(8, 0, 22, 30, 7, 59))
        assertFalse(within(8, 0, 22, 30, 22, 31))
    }

    /**
     * A window whose end is not after its start wraps past midnight. Reading that
     * as an empty range would report "forwarding off" for an overnight schedule
     * that is genuinely active.
     */
    @Test
    fun `an overnight window covers both sides of midnight`() {
        assertTrue(within(22, 0, 6, 0, 23, 0))
        assertTrue(within(22, 0, 6, 0, 3, 0))
        assertTrue(within(22, 0, 6, 0, 0, 0))
        assertTrue(within(22, 0, 6, 0, 6, 0))
    }

    @Test
    fun `an overnight window excludes the daytime gap`() {
        assertFalse(within(22, 0, 6, 0, 12, 0))
        assertFalse(within(22, 0, 6, 0, 21, 59))
    }

    @Test
    fun `a single instant window covers only that minute`() {
        assertTrue(within(9, 0, 9, 0, 9, 0))
        assertFalse(within(9, 0, 9, 0, 9, 1))
        assertFalse(within(9, 0, 9, 0, 8, 59))
    }

    @Test
    fun `the whole day window covers everything`() {
        assertTrue(within(0, 0, 23, 59, 0, 0))
        assertTrue(within(0, 0, 23, 59, 12, 0))
        assertTrue(within(0, 0, 23, 59, 23, 59))
    }

    /**
     * DataStore can hold an out-of-range value if it was written by an older
     * build or corrupted. An invalid window must report "outside" rather than
     * throw, because this runs while assembling a package the user is trying to
     * send for help.
     */
    @Test
    fun `an invalid window is reported as outside instead of throwing`() {
        assertFalse(within(-1, 0, 22, 0, 12, 0))
        assertFalse(within(8, 0, 25, 0, 12, 0))
        assertFalse(within(8, 70, 22, 0, 12, 0))
    }

    private fun within(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        nowHour: Int,
        nowMinute: Int,
    ): Boolean =
        DiagnosticReportCollector.isWithinActiveWindow(
            startHour,
            startMinute,
            endHour,
            endMinute,
            LocalTime.of(nowHour, nowMinute),
        )
}
