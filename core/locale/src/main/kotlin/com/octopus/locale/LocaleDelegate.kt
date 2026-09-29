package com.octopus.locale

import android.content.Context
import android.util.Log

/**
 * Locale management delegate
 * Handles locale application logic without requiring inheritance
 *
 * Usage in Application:
 * ```kotlin
 * class MyApplication : Application() {
 *     private val localeDelegate = LocaleDelegate()
 *
 *     override fun attachBaseContext(base: Context) {
 *         super.attachBaseContext(localeDelegate.attachBaseContext(base))
 *     }
 * }
 * ```
 *
 * Usage in Activity:
 * ```kotlin
 * class MyActivity : ComponentActivity() {
 *     private val localeDelegate = LocaleDelegate()
 *
 *     override fun attachBaseContext(newBase: Context) {
 *         super.attachBaseContext(localeDelegate.attachBaseContext(newBase))
 *     }
 * }
 * ```
 */
class LocaleDelegate(
    private val localeManager: AppLocaleManager = AppLocaleManagerImpl,
    private val tag: String = "LocaleDelegate",
) {
    /**
     * Handle attachBaseContext locale application
     * This method should be called in attachBaseContext before super call
     *
     * @param base Base context from attachBaseContext
     * @return Context with locale applied
     */
    fun attachBaseContext(base: Context): Context =
        try {
            val languageCode = localeManager.getSavedLanguageCode(base)
            Log.d(tag, "Applying locale: $languageCode")
            localeManager.attachBaseContextWithLocale(base, languageCode)
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply locale in attachBaseContext", e)
            base
        }

    /**
     * Handle attachBaseContext with custom language provider
     * Useful when you want to read from DataStore or other async sources
     *
     * @param base Base context
     * @param languageProvider Function that provides language code synchronously
     * @return Context with locale applied
     */
    fun attachBaseContext(
        base: Context,
        languageProvider: (Context) -> String,
    ): Context =
        try {
            val languageCode = languageProvider(base)
            Log.d(tag, "Applying locale from provider: $languageCode")
            localeManager.attachBaseContextWithLocale(base, languageCode)
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply locale with provider", e)
            base
        }
}
