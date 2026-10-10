package com.octopus.pigeon.post.diagnostic

import android.content.Context
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.service.SmtpTransport
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/**
 * Reads the live configuration and reduces it to a [ConfigShapeReport].
 *
 * Every value that could identify the user is dropped on the way in, so the
 * report is safe to send without the user having to read it first. That is
 * deliberate: a report the user has to vet defeats the purpose, because they
 * cannot tell what is safe without knowing what the app stores.
 */
object DiagnosticReportCollector {
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
            currentlyWithinActiveWindow = isWithinActiveWindow(activeTime.startHour, activeTime.startMinute, activeTime.endHour, activeTime.endMinute, LocalTime.now()),
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
     * Whether [now] falls inside the configured window.
     *
     * Reported instead of the window itself: "forwarding never happens" is almost
     * always the window not covering the current moment, and answering that needs
     * only the yes or no. The start and end hours are the most identifying values
     * the app stores, so they stay out of the report.
     */
    internal fun isWithinActiveWindow(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        now: LocalTime,
    ): Boolean {
        val start =
            runCatching { LocalTime.of(startHour, startMinute) }.getOrNull()
                ?: return false
        val end =
            runCatching { LocalTime.of(endHour, endMinute) }.getOrNull()
                ?: return false
        val minuteOfDay = now.hour * 60 + now.minute
        val startMinuteOfDay = start.hour * 60 + start.minute
        val endMinuteOfDay = end.hour * 60 + end.minute
        // A window whose end is not after its start wraps past midnight, which is
        // what makes "22:00 to 06:00" mean an overnight window rather than an
        // empty one.
        return if (startMinuteOfDay <= endMinuteOfDay) {
            minuteOfDay in startMinuteOfDay..endMinuteOfDay
        } else {
            minuteOfDay >= startMinuteOfDay || minuteOfDay <= endMinuteOfDay
        }
    }
}
