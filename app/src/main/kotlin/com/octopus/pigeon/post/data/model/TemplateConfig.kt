package com.octopus.pigeon.post.data.model

/**
 * Template configuration for SMS filtering and email formatting
 * Note: Default values for emailTemplate should be provided by PreferencesRepository
 * to support dynamic internationalization
 */
data class TemplateConfig(
    val keywords: List<String> = emptyList(),
    val caseSensitive: Boolean = false,
    val matchLogic: MatchLogic = MatchLogic.ANY,
    val emailTemplate: String = "", // Default will be set by PreferencesRepository with i18n support
    val useTemplate: Boolean = false,
)

/**
 * Logic for matching keywords in SMS content
 */
enum class MatchLogic {
    ANY, // Match any of the keywords
    ALL, // Must match all keywords
}
