package com.octopus.pigeon.post.service

import com.octopus.logging.PigeonLogRedactor
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.data.model.MatchLogic
import com.octopus.pigeon.post.data.model.TemplateConfig

/**
 * SMS matching service for keyword filtering and verification code extraction
 * Handles template matching logic and pattern matching
 */
class SmsMatchingService {
    private val TAG = SmsMatchingService::class.java.simpleName

    fun isSmsMatchTemplate(
        smsContent: String,
        templateConfig: TemplateConfig,
    ): Boolean {
        if (templateConfig.keywords.isEmpty()) {
            return false
        }

        val contentToCheck =
            if (templateConfig.caseSensitive) {
                smsContent
            } else {
                smsContent.lowercase()
            }

        val keywordsToCheck =
            if (templateConfig.caseSensitive) {
                templateConfig.keywords
            } else {
                templateConfig.keywords.map { it.lowercase() }
            }

        return when (templateConfig.matchLogic) {
            MatchLogic.ANY -> {
                keywordsToCheck.any { keyword ->
                    contentToCheck.contains(keyword)
                }
            }
            MatchLogic.ALL -> {
                keywordsToCheck.all { keyword ->
                    contentToCheck.contains(keyword)
                }
            }
        }
    }

    // Alias method for test interface
    fun matchKeywords(
        smsContent: String,
        templateConfig: TemplateConfig,
    ): Boolean {
        PigeonLogger.debug(
            TAG,
            "Starting keyword matching, content digest: " +
                PigeonLogRedactor.contentTag(smsContent) +
                ", length: ${smsContent.length}",
        )
        PigeonLogger.debug(
            TAG,
            "Starting keyword matching, content length: ${smsContent.length}, keyword count: ${templateConfig.keywords.size}",
        )
        val result = isSmsMatchTemplate(smsContent, templateConfig)
        PigeonLogger.debug(TAG, "Keyword matching result: $result")
        return result
    }

    // Extract verification code
    fun extractVerificationCode(content: String): String? {
        PigeonLogger.debug(TAG, "Starting verification code extraction")

        // Common verification code patterns
        val patterns =
            listOf(
                "(?:验证码|验证码是|验证码为|动态码|动态密码|校验码)[:：]?\\s*([0-9]{4,8})",
                "([0-9]{4,8})(?:\\s*(?:为您的|是您的)?验证码)",
                "\\b([0-9]{4,8})\\b.*(?:验证码|动态码|校验码)",
                "(?:code|Code|CODE)[:：]?\\s*([0-9]{4,8})",
            )

        for (pattern in patterns) {
            val regex = Regex(pattern)
            val matchResult = regex.find(content)
            if (matchResult != null) {
                val code = matchResult.groupValues[1]
                PigeonLogger.debug(TAG, "Extracted verification code: $code, using pattern: $pattern")
                return code
            }
        }

        PigeonLogger.debug(TAG, "No verification code extracted")
        return null
    }
}
