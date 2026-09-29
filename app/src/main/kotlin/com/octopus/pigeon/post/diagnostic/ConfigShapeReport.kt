package com.octopus.pigeon.post.diagnostic

/**
 * The configuration, with every value that identifies someone removed.
 *
 * This is the whole point of the diagnostic package. The user has a problem and
 * the person helping them needs to know how the app is configured, but the user
 * does not want to hand over their mail password to get an answer. So this
 * reports the *shape* of the configuration: which switches are on, how many
 * keywords there are, which port, whether TLS is on. Enough to diagnose a
 * rejected login or a keyword that never matches, and nothing that would be
 * damaging if the file ended up forwarded to the wrong person.
 *
 * Anything that could identify the user, or reveal the content of their mail, is
 * reduced to a count or a boolean here. The counts still carry the information
 * needed to explain "your password is wrong" versus "no keywords are set".
 */
data class ConfigShapeReport(
    val smtpServer: String,
    val smtpPort: Int,
    val sslEnabled: Boolean,
    val starttlsEnabled: Boolean,
    val smtpAuthEnabled: Boolean,
    val senderConfigured: Boolean,
    val recipientCount: Int,
    val passwordConfigured: Boolean,
    val subjectConfigured: Boolean,
    val keywordCount: Int,
    val hasHtmlTemplate: Boolean,
    val activeScheduleEnabled: Boolean,
    val activeWindowDescription: String,
    val recordRetentionDays: Int,
    val autoCleanupEnabled: Boolean,
    val notificationEnabled: Boolean,
    val silentBuild: Boolean,
    val currentMode: String,
) {
    /**
     * Renders the report.
     *
     * The server host is included because a wrong host is one of the most common
     * causes of a send failing and it is not personal data, but the addresses
     * themselves are not. Only the count of recipients appears.
     */
    fun toText(): String =
        buildString {
            appendLine("PigeonPost diagnostic package")
            appendLine()
            appendLine("Configuration shape")
            appendLine("  Values that identify the user are omitted on purpose.")
            appendLine()
            appendLine("Mail")
            appendLine("  server: $smtpServer")
            appendLine("  port: $smtpPort")
            appendLine("  ssl: $sslEnabled")
            appendLine("  starttls: $starttlsEnabled")
            appendLine("  auth: $smtpAuthEnabled")
            appendLine("  sender address: ${if (senderConfigured) "configured" else "not configured"}")
            appendLine("  recipient addresses: $recipientCount configured")
            appendLine("  password: ${if (passwordConfigured) "configured" else "not configured"}")
            appendLine("  subject: ${if (subjectConfigured) "configured" else "not configured"}")
            appendLine("  html template: $hasHtmlTemplate")
            appendLine()
            appendLine("Matching")
            appendLine("  keywords: $keywordCount configured")
            appendLine()
            appendLine("Schedule")
            appendLine("  active schedule: $activeScheduleEnabled")
            appendLine("  active window: $activeWindowDescription")
            appendLine()
            appendLine("Storage")
            appendLine("  auto cleanup: $autoCleanupEnabled")
            appendLine("  retention: $recordRetentionDays days")
            appendLine()
            appendLine("Build")
            appendLine("  silent build: $silentBuild")
            appendLine("  current mode: $currentMode")
        }
}
