package com.octopus.logging

private const val FILE_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"

private const val CONSOLE_PATTERN = "%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"

/**
 * Builds the Log4j configuration that [PigeonLogger] hands to Log4j at runtime.
 *
 * The XML declaration must be the very first thing in the document. Assembling
 * this from a raw string plus `trimIndent()` appears to do that but does not:
 * interpolation happens before `trimIndent()` runs, so once the multi-line
 * appender fragments are spliced in, the shared indent can no longer be detected
 * and nothing is trimmed. The declaration then ends up preceded by spaces, SAX
 * throws "processing instructions must not start with xml", and Log4j quietly
 * falls back to a default configuration with no file appender. Every log line
 * then goes to logcat and none of them ever reach the file, with no exception
 * raised anywhere in this class.
 *
 * The lines are therefore appended explicitly, which cannot drift.
 */
internal fun buildLog4jConfigXml(
    fileLoggingEnabled: Boolean,
    logFilePath: String?,
): String {
    val fileAppenderAttached = fileLoggingEnabled && logFilePath != null
    val lines =
        mutableListOf(
            """<?xml version="1.0" encoding="UTF-8"?>""",
            """<Configuration status="WARN">""",
            """    <Appenders>""",
        )
    if (fileAppenderAttached) {
        lines += """        <File name="FileAppender" fileName="${logFilePath.escapeXmlAttribute()}">"""
        lines += """            <PatternLayout pattern="$FILE_PATTERN"/>"""
        lines += """        </File>"""
    }
    if (fileLoggingEnabled) {
        lines += """        <Console name="Console" target="SYSTEM_OUT">"""
        lines += """            <PatternLayout pattern="$CONSOLE_PATTERN"/>"""
        lines += """        </Console>"""
    }
    lines += """    </Appenders>"""
    lines += """    <Loggers>"""
    lines += """        <Root level="${if (fileLoggingEnabled) "DEBUG" else "OFF"}">"""
    if (fileAppenderAttached) {
        lines += """            <AppenderRef ref="FileAppender"/>"""
    }
    if (fileLoggingEnabled) {
        lines += """            <AppenderRef ref="Console"/>"""
    }
    lines += """        </Root>"""
    lines += """    </Loggers>"""
    lines += """</Configuration>"""
    return lines.joinToString(separator = "\n", postfix = "\n")
}

private fun String.escapeXmlAttribute(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace("\"", "&quot;")
