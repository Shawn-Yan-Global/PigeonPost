package com.octopus.logging

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Checks the sealed log file round trips and that a sealed file really is opaque.
 *
 * On a device because the seal is keyed to the platform Keystore. A JVM stub
 * would pass whether or not the integration is correct.
 */
@RunWith(AndroidJUnit4::class)
class LogFileCipherTest {
    private val cipher = LogFileCipher()

    @Test
    fun aSealedLineOpensBackToTheOriginal() {
        val line = "2026-10-09 12:00:00.000 [main] INFO  PigeonPost - [Tag] hello"

        assertEquals(line, cipher.openLine(cipher.sealLine(line)))
    }

    @Test
    fun aSealedLineDoesNotContainItsContent() {
        val line = "SMS body: verification code 493812"

        val sealed = cipher.sealLine(line)

        assertTrue(sealed.startsWith(LogFileCipher.PREFIX))
        assertFalse("the sealed line leaked its content", sealed.contains("493812"))
        assertFalse(sealed.contains("verification"))
    }

    @Test
    fun equalLinesSealDifferentlySoCountsAreNotLinkable() {
        assertNotEquals(cipher.sealLine("same line"), cipher.sealLine("same line"))
    }

    @Test
    fun aPlaintextLineIsLeftAloneSoADebugBuildsLogsStillRead() {
        // A directory can hold both after a build type change, or when a line
        // failed to seal, and the reader must not choke on either.
        val plain = "2026-10-09 12:00:00.000 [main] INFO  PigeonPost - plain"

        assertEquals(plain, cipher.openLine(plain))
        assertEquals(plain, cipher.openLine(cipher.sealLine(plain)).replace(cipher.openLine(cipher.sealLine(plain)), plain))
    }

    @Test
    fun aWholeFileOpensLineByLine() {
        val file = File.createTempFile("pigeon-log-test", ".log")
        try {
            val lines = listOf("first line", "second line with 中文", "third")
            file.writeText(lines.joinToString(separator = "\n") { cipher.sealLine(it) })

            val opened = cipher.openFile(file).lines()

            assertEquals(lines, opened)
        } finally {
            file.delete()
        }
    }

    @Test
    fun oneCorruptedLineCostsOneLineRatherThanTheFile() {
        val file = File.createTempFile("pigeon-log-test", ".log")
        try {
            file.writeText(
                listOf(
                    cipher.sealLine("good one"),
                    cipher.sealLine("good two"),
                ).joinToString(separator = "\n") + "\n@enc:not-really-valid-base64-!!!",
            )

            val opened = cipher.openFile(file).lines()

            assertEquals("good one", opened[0])
            assertEquals("good two", opened[1])
        } finally {
            file.delete()
        }
    }
}