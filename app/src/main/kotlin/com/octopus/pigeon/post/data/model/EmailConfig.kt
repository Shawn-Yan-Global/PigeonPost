package com.octopus.pigeon.post.data.model

/**
 * Email configuration for SMTP settings and message formatting
 * Note: Default values for emailSubject should be provided by PreferencesRepository
 * to support dynamic internationalization
 */
data class EmailConfig(
    val smtpServer: String = "",
    val smtpPort: Int = 587,
    val senderEmail: String = "",
    val password: String = "",
    val recipientEmail: String = "",
    val emailSubject: String = "", // Default will be set by PreferencesRepository with i18n support
)
