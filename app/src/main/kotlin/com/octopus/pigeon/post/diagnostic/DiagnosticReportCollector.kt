package com.octopus.pigeon.post.diagnostic

import android.content.Context
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.service.SmtpTransport
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Reads the live configuration and reduces it to a [ConfigShapeReport].
 *
 * Every value that could identify the user is dropped on the way in, so the
 * report is safe to send without the user having to read it first. That is
 * deliberate: a report the user has to vet defeats the purpose, because they
 * cannot tell what is safe without knowing what the app stores.
 */
object DiagnosticReportCollector {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun collect(context: Context): ConfigShapeReport {
        val repository = PreferencesRepository(context)
        val email = repository.emailConfig.first()
        val template = repository.templateConfig.first()
        val activeTime = repository.activeTimeConfig.first()
        val cleanupDays = repository.autoCleanupRecordsDays.first()
        val cleanupEnabled = repository.autoCleanupRecordsEnabled.first()
        val smsReceived = repository.smsReceivedNotificationEnabled.first()
        val smsForwarded = repository.smsForwardedNotificationEnabled.first()
        val serviceEnabled = repository.serviceEnabled.first()

        return ConfigShapeReport(
            smtpServer = email.smtpServer.ifBlank { "(not configured)" },
            smtpPort = email.smtpPort,
            sslEnabled = SmtpTransport.SSL_ENABLED,
            starttlsEnabled = SmtpTransport.STARTTLS_ENABLED,
            smtpAuthEnabled = SmtpTransport.AUTH_ENABLED,
            senderConfigured = email.senderEmail.isNotBlank(),
            recipientCount =
                email.recipientEmail
                    .split(',', ';', '\n')
                    .count { it.isNotBlank() },
            passwordConfigured = email.password.isNotBlank(),
            subjectConfigured = email.emailSubject.isNotBlank(),
            keywordCount = template.keywords.count { it.isNotBlank() },
            hasHtmlTemplate = template.useTemplate && template.emailTemplate.isNotBlank(),
            activeScheduleEnabled = activeTime.isEnabled,
            activeWindowDescription = windowDescription(activeTime.startHour, activeTime.startMinute, activeTime.endHour, activeTime.endMinute),
            recordRetentionDays = cleanupDays,
            autoCleanupEnabled = cleanupEnabled,
            notificationEnabled = smsReceived || smsForwarded,
            silentBuild = com.octopus.pigeon.post.BuildConfig.SILENT,
            currentMode =
                when {
                    !serviceEnabled -> "forwarding off"
                    else -> "forwarding on"
                },
        )
    }

    /**
     * The active window, as a range of clock times.
     *
     * The hours are included because an active window that does not cover the
     * current time is a common reason for "it never forwards", and a clock range
     * is not identifying. The specific rule the user set is not included.
     */
    private fun windowDescription(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
    ): String =
        runCatching {
            val start = LocalTime.of(startHour, startMinute).format(timeFormat)
            val end = LocalTime.of(endHour, endMinute).format(timeFormat)
            "$start to $end"
        }.getOrElse { "invalid window ($startHour:$startMinute to $endHour:$endMinute)" }
}
