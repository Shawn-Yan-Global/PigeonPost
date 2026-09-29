package com.octopus.locale

import android.app.Activity
import android.content.Context

/**
 * Interface for managing application locale changes
 * Supports both Activity and Application level locale management
 */
interface AppLocaleManager {
    /**
     * Apply locale to a context (for Activities and other components)
     *
     * @param context Context to apply locale to
     * @param languageCode Language code (e.g., "en", "zh")
     * @param countryCode Optional country code (e.g., "US", "CN")
     * @return Context with applied locale
     */
    fun applyLocale(
        context: Context,
        languageCode: String,
        countryCode: String? = null,
    ): Context

    /**
     * Apply locale in attachBaseContext (for Activities and Application)
     * Should be called in attachBaseContext() method
     *
     * @param context Base context from attachBaseContext
     * @param languageCode Language code to apply
     * @param countryCode Optional country code
     * @return Context with applied locale
     */
    fun attachBaseContextWithLocale(
        context: Context,
        languageCode: String,
        countryCode: String? = null,
    ): Context

    /**
     * Get saved language code from persistent storage
     * This is synchronous and safe to call from attachBaseContext
     *
     * @param context Context to read preferences
     * @return Saved language code or default
     */
    fun getSavedLanguageCode(context: Context): String

    /**
     * Save language code to persistent storage
     * This is synchronous and should be called alongside DataStore updates
     *
     * @param context Context to write preferences
     * @param languageCode Language code to save
     */
    fun saveLanguageCode(
        context: Context,
        languageCode: String,
    )

    /**
     * Restart the app to apply language changes
     * Uses ProcessPhoenix for clean restart
     *
     * @param activity Activity context to restart from
     */
    fun restartApp(activity: Activity)
}
