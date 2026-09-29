package com.octopus.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class Log4jConfigXmlTest {
    /**
     * The failure this guards against was silent: the declaration was indented,
     * SAX threw, Log4j swallowed it and fell back to a default configuration, and
     * the log file was simply never created. Asserting the first character is the
     * cheapest way to keep that from coming back.
     */
    @Test
    fun `declaration starts the document so the parser accepts it`() {
        val xml = buildLog4jConfigXml(fileLoggingEnabled = true, logFilePath = "/data/user/0/app/files/logs/2026-09-28.log")

        assertTrue(xml.startsWith("<?xml"))
        assertTrue(parse(xml))
    }

    @Test
    fun `logging enabled attaches a file appender that writes to the given path`() {
        val xml = buildLog4jConfigXml(fileLoggingEnabled = true, logFilePath = "/data/logs/2026-09-28.log")

        assertTrue(xml.contains("""<File name="FileAppender" fileName="/data/logs/2026-09-28.log">"""))
        assertTrue(xml.contains("""<AppenderRef ref="FileAppender"/>"""))
        assertTrue(xml.contains("""<Root level="DEBUG">"""))
    }

    @Test
    fun `logging disabled switches the root logger off and attaches no appender`() {
        val xml = buildLog4jConfigXml(fileLoggingEnabled = false, logFilePath = "/data/logs/2026-09-28.log")

        assertTrue(xml.contains("""<Root level="OFF">"""))
        assertFalse(xml.contains("FileAppender"))
        assertFalse(xml.contains("Console"))
        assertTrue(parse(xml))
    }

    /**
     * The silent flavor reaches here with file logging already off, so a null
     * path must not resurrect an appender.
     */
    @Test
    fun `null log file does not attach a file appender`() {
        val xml = buildLog4jConfigXml(fileLoggingEnabled = true, logFilePath = null)

        assertFalse(xml.contains("""<File name="FileAppender""""))
        assertTrue(xml.contains("""<AppenderRef ref="Console"/>"""))
        assertTrue(parse(xml))
    }

    @Test
    fun `paths with xml metacharacters are escaped`() {
        val xml = buildLog4jConfigXml(fileLoggingEnabled = true, logFilePath = """/data/a&b/"<x>".log""")

        assertTrue(parse(xml))
        assertFalse(xml.contains("""a&b/"""))
        assertTrue(xml.contains("""a&amp;b/"""))
    }

    private fun parse(xml: String): Boolean =
        runCatching {
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray()))
            true
        }.getOrDefault(false)

    @Test
    fun `appenders are balanced`() {
        val xml = buildLog4jConfigXml(fileLoggingEnabled = true, logFilePath = "/data/logs/a.log")

        assertEquals(countOccurrences(xml, "<File "), countOccurrences(xml, "</File>"))
        assertEquals(countOccurrences(xml, "<Console "), countOccurrences(xml, "</Console>"))
        assertEquals(countOccurrences(xml, "<Configuration"), countOccurrences(xml, "</Configuration>"))
    }

    private fun countOccurrences(
        haystack: String,
        needle: String,
    ): Int = haystack.split(needle).size - 1
}
