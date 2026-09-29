package com.octopus.pigeon.post.service

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * Template service for parsing email templates and extracting verification codes
 * Handles placeholder replacement and pattern matching
 */
class TemplateService {
    companion object {
        private val CODE_PATTERNS =
            listOf(
                Pattern.compile("验证码[：:](\\d{4,6})"),
                Pattern.compile("码[：:](\\d{4,6})"),
                Pattern.compile("(\\d{4,6})"),
                Pattern.compile("验证码是(\\d{4,6})"),
                Pattern.compile("码是(\\d{4,6})"),
            )
    }

    /**
     * Parse email template and replace placeholders
     */
    fun parseTemplate(
        template: String,
        sender: String,
        content: String,
        receiveTime: LocalDateTime,
    ): String {
        var result = template

        // Replace basic placeholders
        result = result.replace("{sender}", sender)
        result = result.replace("{content}", content)
        result = result.replace("{time}", formatTime(receiveTime))

        // Extract and replace verification code
        val code = extractVerificationCode(content)
        result = result.replace("{code}", code ?: "Verification code not found")

        return result
    }

    /**
     * Extract verification code from SMS
     */
    private fun extractVerificationCode(content: String): String? {
        for (pattern in CODE_PATTERNS) {
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    /**
     * Format time
     */
    private fun formatTime(time: LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return time.format(formatter)
    }
}
