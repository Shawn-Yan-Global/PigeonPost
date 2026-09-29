package com.octopus.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Helper object for locale-related operations
 * Provides utility methods for language code retrieval and locale application
 */
object LocaleHelper {
    /**
     * SharedPreferences name for storing language preference
     */
    const val PREFS_NAME = "locale_prefs"

    /**
     * Key for language code in SharedPreferences
     */
    const val KEY_LANGUAGE = "app_language"

    /**
     * Get saved language code from SharedPreferences
     * This method is synchronous and can be called from attachBaseContext
     *
     * @param context Context to access SharedPreferences
     * @return Language code (e.g., "en", "zh")
     */
    fun getSavedLanguageCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, Locale.getDefault().language)
            ?: Locale.getDefault().language
    }

    /**
     * Save language code to SharedPreferences
     * This is synchronous and should be called alongside DataStore updates
     *
     * @param context Context to access SharedPreferences
     * @param languageCode Language code to save
     */
    fun saveLanguageCode(
        context: Context,
        languageCode: String,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageCode)
            .apply()
    }

    /**
     * Create a locale from language code
     *
     * @param languageCode Language code (e.g., "en", "zh")
     * @param countryCode Optional country code (e.g., "US", "CN")
     * @return Locale object
     */
    fun createLocale(
        languageCode: String,
        countryCode: String? = null,
    ): Locale =
        Locale
            .Builder()
            .setLanguage(languageCode)
            .apply {
                countryCode?.let { setRegion(it) }
            }.build()

    /**
     * Apply locale to a context
     * Creates a new context with the specified locale applied
     *
     * @param context Base context
     * @param languageCode Language code to apply
     * @param countryCode Optional country code
     * @return Context with applied locale
     */
    fun applyLocale(
        context: Context,
        languageCode: String,
        countryCode: String? = null,
    ): Context {
        val locale = createLocale(languageCode, countryCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}
