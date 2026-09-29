package com.octopus.pigeon.post.service

import android.content.Context
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.model.EmailConfig
import com.octopus.pigeon.post.data.model.SmsRecord
import com.octopus.pigeon.post.data.model.TemplateConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.PrintStream
import java.time.format.DateTimeFormatter
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Email service for sending SMS forwarding and test emails
 * Handles SMTP configuration and email template processing
 */
class EmailService(
    private val context: Context,
) {
    private val TAG = EmailService::class.java.simpleName
    private val templateService = TemplateService()

    // ------------------------------
    // Logging helpers
    // ------------------------------
    private fun logInfo(messageEn: String) {
        PigeonLogger.info(TAG, messageEn)
    }

    private fun logDebug(messageEn: String) {
        PigeonLogger.debug(TAG, messageEn)
    }

    private fun logError(
        recipient: String,
        exception: Exception,
    ) {
        PigeonLogger.error(
            TAG,
            "Email sending failed to $recipient: ${exception.message}",
            exception,
        )
    }

    // ------------------------------
    // Email config validation
    // ------------------------------

    /**
     * Validates email configuration and returns error message if invalid
     * @return null if valid, error message string if invalid
     */
    private fun validateEmailConfig(emailConfig: EmailConfig): String? =
        when {
            emailConfig.smtpServer.isBlank() ->
                context.getString(R.string.email_config_smtp_server_empty)

            emailConfig.senderEmail.isBlank() ->
                context.getString(R.string.email_config_sender_email_empty)

            emailConfig.recipientEmail.isBlank() ->
                context.getString(R.string.email_config_recipient_email_empty)

            emailConfig.password.isBlank() ->
                context.getString(R.string.email_config_password_empty)

            emailConfig.emailSubject.isBlank() ->
                context.getString(R.string.email_config_subject_empty)

            else -> null
        }

    // ------------------------------
    // Common helper: build session
    // ------------------------------
    private fun createSession(emailConfig: EmailConfig): Session {
        val properties =
            Properties().apply {
                put("mail.smtp.host", emailConfig.smtpServer)
                put("mail.smtp.port", emailConfig.smtpPort.toString())
                SmtpTransport.applyTo(this)
            }

        logDebug("Email properties: $properties")

        return Session
            .getInstance(
                properties,
                object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication = PasswordAuthentication(emailConfig.senderEmail, emailConfig.password)
                },
            ).apply {
                debug = true
                debugOut = PrintStream(SmtpDebugSink())
            }
    }

    // ------------------------------
    // Common helper: send message with retry
    // ------------------------------
    private suspend fun sendEmailMessage(
        session: Session,
        message: MimeMessage,
    ): Result<Unit> {
        val maxRetries = 3
        val retryDelayMs = 2000L // 2 seconds between retries

        repeat(maxRetries) { attempt ->
            try {
                logDebug(
                    "Attempt ${attempt + 1}/$maxRetries: Starting email sending to: ${message.allRecipients.joinToString()}",
                )

                Transport.send(message)

                logInfo(
                    "✅ Email sent successfully to ${message.allRecipients.joinToString()} on attempt ${attempt + 1}",
                )
                return Result.success(Unit)
            } catch (e: Exception) {
                val isLastAttempt = attempt == maxRetries - 1
                val errorMessage = e.message ?: "Unknown error"

                // Check if it's a port or connection error
                val isConnectionError =
                    errorMessage.contains("port", ignoreCase = true) ||
                        errorMessage.contains("connection", ignoreCase = true) ||
                        errorMessage.contains("timeout", ignoreCase = true) ||
                        errorMessage.contains("refused", ignoreCase = true)

                if (isConnectionError && !isLastAttempt) {
                    logInfo(
                        "⚠️ Connection error on attempt ${attempt + 1}, retrying in ${retryDelayMs}ms: $errorMessage",
                    )
                    kotlinx.coroutines.delay(retryDelayMs)
                    // Continue to next retry
                } else if (isLastAttempt) {
                    // Last attempt failed
                    logError(message.allRecipients.joinToString(), e)
                    return Result.failure(e)
                } else {
                    // Non-connection error, fail immediately
                    logError(message.allRecipients.joinToString(), e)
                    return Result.failure(e)
                }
            }
        }

        // Should not reach here, but just in case
        return Result.failure(Exception("Failed to send email after $maxRetries attempts"))
    }

    // ------------------------------
    // Send SMS Email
    // ------------------------------
    suspend fun sendSmsEmail(
        emailConfig: EmailConfig,
        smsRecord: SmsRecord,
        templateConfig: TemplateConfig,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            // Validate email configuration
            val validationError = validateEmailConfig(emailConfig)
            if (validationError != null) {
                logError("Email config validation", Exception(validationError))
                return@withContext Result.failure(Exception(validationError))
            }

            logInfo(
                "Starting SMS email sending, sender: ${smsRecord.sender}, content length: ${smsRecord.content.length}",
            )

            logDebug(
                "Email config - SMTP server: ${emailConfig.smtpServer}:${emailConfig.smtpPort}",
            )
            logDebug(
                "Template config - Use template: ${templateConfig.useTemplate}",
            )

            val session = createSession(emailConfig)

            val message =
                MimeMessage(session).apply {
                    setFrom(InternetAddress(emailConfig.senderEmail))
                    setRecipients(
                        Message.RecipientType.TO,
                        InternetAddress.parse(emailConfig.recipientEmail),
                    )
                    subject = emailConfig.emailSubject

                    val emailBody =
                        if (templateConfig.useTemplate) {
                            templateService.parseTemplate(
                                template = templateConfig.emailTemplate,
                                sender = smsRecord.sender,
                                content = smsRecord.content,
                                receiveTime = smsRecord.receivedTime,
                            )
                        } else {
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            val receivedTime = smsRecord.receivedTime.format(formatter)
                            buildString {
                                appendLine(
                                    context.getString(R.string.sms_email_new_sms),
                                )
                                appendLine()
                                appendLine(
                                    context.getString(R.string.sms_email_sender, smsRecord.sender),
                                )
                                appendLine(
                                    context.getString(R.string.sms_email_received_time, receivedTime),
                                )
                                appendLine(context.getString(R.string.sms_email_content))
                                appendLine()
                                appendLine(smsRecord.content)
                            }
                        }

                    setText(emailBody, "UTF-8")
                }

            sendEmailMessage(session, message)
        }

    // ------------------------------
    // Send Test Email
    // ------------------------------
    suspend fun sendTestEmail(emailConfig: EmailConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            // Validate email configuration
            val validationError = validateEmailConfig(emailConfig)
            if (validationError != null) {
                logError("Email config validation", Exception(validationError))
                return@withContext Result.failure(Exception(validationError))
            }

            logInfo("Starting test email sending")
            logDebug(
                "Test email config - SMTP: ${emailConfig.smtpServer}:${emailConfig.smtpPort}, sender: ${emailConfig.senderEmail}, recipient: ${emailConfig.recipientEmail}",
            )

            val session = createSession(emailConfig)

            val message =
                MimeMessage(session).apply {
                    setFrom(InternetAddress(emailConfig.senderEmail))
                    setRecipients(
                        Message.RecipientType.TO,
                        InternetAddress.parse(emailConfig.recipientEmail),
                    )
                    subject = context.getString(R.string.test_email_subject)
                    setText(
                        context.getString(R.string.test_email_body),
                        "UTF-8",
                    )
                }

            sendEmailMessage(session, message)
        }
}

/**
 * Receives the raw SMTP conversation from JavaMail and forwards it to the log
 * with the sensitive parts removed.
 *
 * JavaMail's own debug output is not safe to log as is. In one pass it contains
 * the base64 `AUTH` exchange, which is the password one decode away; the
 * `MAIL FROM` and `RCPT TO` lines, which are the real mail addresses; and
 * everything the client writes after `DATA`, which is the forwarded message
 * itself. Anyone with the log file would otherwise hold all three.
 *
 * Protocol chatter is genuinely useful when a send fails, so it is kept, and
 * only the values that identify someone are replaced.
 */
private class SmtpDebugSink : OutputStream() {
    private companion object {
        const val TAG = "EmailService"
    }

    private val buffer = StringBuilder()

    /**
     * Once the server or client has said `DATA`, the bytes that follow are the
     * message body rather than protocol, so the line is dropped instead of
     * being trimmed line by line. Reset by the terminating dot of the message.
     */
    private var inMessageBody = false

    override fun write(b: Int) {
        val c = b.toChar()
        if (c != '\n') {
            buffer.append(c)
            return
        }
        if (buffer.isNotEmpty()) {
            emit(buffer.toString())
            buffer.setLength(0)
        }
    }

    private fun emit(line: String) {
        val trimmed = line.trimStart()

        if (inMessageBody) {
            // A single "." on its own line ends the body.
            if (trimmed == ".") inMessageBody = false
            PigeonLogger.debug(TAG, "<message body omitted>")
            return
        }

        when {
            // The base64 credential pair. Never log this, not even truncated.
            trimmed.startsWith("AUTH", ignoreCase = true) ->
                PigeonLogger.debug(TAG, "AUTH <redacted>")

            // Real addresses, in both the SMTP envelope and the headers.
            trimmed.startsWith("MAIL FROM", ignoreCase = true) ->
                PigeonLogger.debug(TAG, "MAIL FROM <redacted>")

            trimmed.startsWith("RCPT TO", ignoreCase = true) ->
                PigeonLogger.debug(TAG, "RCPT TO <redacted>")

            trimmed.startsWith("To:", ignoreCase = true) ||
                trimmed.startsWith("From:", ignoreCase = true) ||
                trimmed.startsWith("Subject:", ignoreCase = true) ->
                PigeonLogger.debug(TAG, "${trimmed.substringBefore(':')}: <redacted>")

            trimmed.equals("DATA", ignoreCase = true) -> {
                inMessageBody = true
                PigeonLogger.debug(TAG, "DATA")
            }

            // Unauthenticated peers announce themselves here.
            trimmed.startsWith("EHLO", ignoreCase = true) ||
                trimmed.startsWith("HELO", ignoreCase = true) ->
                PigeonLogger.debug(TAG, trimmed)

            else -> PigeonLogger.debug(TAG, trimmed)
        }
    }
}
