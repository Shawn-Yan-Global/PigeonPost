package com.octopus.locale

import android.app.Activity
import android.content.Context

/**
 * Extension functions for easy locale management
 * Provides convenient methods without requiring inheritance
 */

// Apply locale in Application.attachBaseContext

/**
 *
 * Usage:
 * ```kotlin
 * class MyApplication : Application() {
 *     override fun attachBaseContext(base: Context) {
 *         super.attachBaseContext(base.withLocale())
 *     }
 * }
 * ```
 */
fun Context.withLocale(localeManager: AppLocaleManager = AppLocaleManagerImpl): Context = LocaleDelegate(localeManager).attachBaseContext(this)

/**
 * Apply locale with custom language provider
 *
 * Usage:
 * ```kotlin
 * override fun attachBaseContext(base: Context) {
 *     super.attachBaseContext(base.withLocale { ctx ->
 *         // Read from your custom source
 *         "zh"
 *     })
 * }
 * ```
 */
fun Context.withLocale(
    localeManager: AppLocaleManager = AppLocaleManagerImpl,
    languageProvider: (Context) -> String,
): Context = LocaleDelegate(localeManager).attachBaseContext(this, languageProvider)

/**
 * Save language and restart app
 * Convenient method for language change
 *
 * Usage:
 * ```kotlin
 * // In ViewModel or Activity
 * suspend fun changeLanguage(languageCode: String) {
 *     context.setLanguageAndRestart(activity, languageCode)
 * }
 * ```
 */
fun Context.setLanguageAndRestart(
    activity: Activity,
    languageCode: String,
    localeManager: AppLocaleManager = AppLocaleManagerImpl,
) {
    localeManager.saveLanguageCode(this, languageCode)
    localeManager.restartApp(activity)
}

/**
 * Get current saved language code
 *
 * Usage:
 * ```kotlin
 * val currentLanguage = context.getSavedLanguage()
 * ```
 */
fun Context.getSavedLanguage(localeManager: AppLocaleManager = AppLocaleManagerImpl): String = localeManager.getSavedLanguageCode(this)
