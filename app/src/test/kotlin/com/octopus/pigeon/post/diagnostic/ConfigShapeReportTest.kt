package com.octopus.pigeon.post.diagnostic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the promise the diagnostic package makes: that sending it does not hand
 * over the user's mail account.
 *
 * The whole feature is only worth having if the user can send the file without
 * reading it first. A field that quietly gets added to the report and then
 * interpolated straight into the text would break that invisibly, because
 * nothing about the UI changes when it happens. So the sensitive values are put
 * in here as bait and the output is checked for them.
 */
class ConfigShapeReportTest {
    private val report =
        ConfigShapeReport(
            smtpServer = "smtp.example.com",
            smtpPort = 465,
            sslEnabled = true,
            starttlsEnabled = false,
            smtpAuthEnabled = true,
            senderConfigured = true,
            recipientCount = 2,
            passwordConfigured = true,
            subjectConfigured = true,
            keywordCount = 3,
            hasHtmlTemplate = true,
            activeScheduleEnabled = true,
            activeWindowDescription = "08:00 to 22:30",
            recordRetentionDays = 7,
            autoCleanupEnabled = true,
            notificationEnabled = true,
            silentBuild = false,
            currentMode = "forwarding on",
        )

    @Test
    fun `report states the shape of the mail settings`() {
        val text = report.toText()

        // The values that make a send work, which are safe and necessary.
        assertTrue(text.contains("smtp.example.com"))
        assertTrue(text.contains("465"))
        assertTrue(text.contains("ssl: true"))
        assertTrue(text.contains("recipient addresses: 2 configured"))
        assertTrue(text.contains("keywords: 3 configured"))
        assertTrue(text.contains("08:00 to 22:30"))
        assertTrue(text.contains("retention: 7 days"))
    }

    @Test
    fun `report never contains a value that identifies the user`() {
        val text = report.toText()

        // These are the placeholders the real values would have to arrive as.
        // If any of them end up in the text, the shape reporting has regressed
        // into copying the config.
        for (
        forbidden in
        listOf(
            SECRET_SENDER,
            SECRET_RECIPIENT,
            SECRET_PASSWORD,
            SECRET_SUBJECT,
            SECRET_KEYWORD,
        )
        ) {
            assertFalse(
                "diagnostic report leaked a configured value: $forbidden",
                text.contains(forbidden),
            )
        }
    }

    @Test
    fun `report says configured without saying what is configured`() {
        val text = report.toText()

        // A boolean is the useful part. "password: configured" tells support the
        // field is filled in, which is what they need to know, and nothing more.
        assertTrue(text.contains("sender address: configured"))
        assertTrue(text.contains("password: configured"))
        assertTrue(text.contains("subject: configured"))
    }

    @Test
    fun `report distinguishes not configured from configured`() {
        val empty =
            report.copy(
                senderConfigured = false,
                recipientCount = 0,
                passwordConfigured = false,
                subjectConfigured = false,
                keywordCount = 0,
                hasHtmlTemplate = false,
            )

        val text = empty.toText()

        assertTrue(text.contains("sender address: not configured"))
        assertTrue(text.contains("password: not configured"))
        assertTrue(text.contains("recipient addresses: 0 configured"))
        assertTrue(text.contains("keywords: 0 configured"))
        assertTrue(text.contains("html template: false"))
    }

    private companion object {
        const val SECRET_SENDER = "sender-address@example.com"
        const val SECRET_RECIPIENT = "recipient-address@example.com"
        const val SECRET_PASSWORD = "mail-password-value"
        const val SECRET_SUBJECT = "the email subject line"
        const val SECRET_KEYWORD = "verification-code"
    }
}
