package com.octopus.locale

import android.app.Activity
import android.content.Context
import androidx.annotation.MainThread
import com.jakewharton.processphoenix.ProcessPhoenix

/**
 * Implementation of AppLocaleManager
 * Handles locale changes for Activities and Application
 */
object AppLocaleManagerImpl : AppLocaleManager {
    override fun applyLocale(
        context: Context,
        languageCode: String,
        countryCode: String?,
    ): Context = LocaleHelper.applyLocale(context, languageCode, countryCode)

    override fun attachBaseContextWithLocale(
        context: Context,
        languageCode: String,
        countryCode: String?,
    ): Context = LocaleHelper.applyLocale(context, languageCode, countryCode)

    override fun getSavedLanguageCode(context: Context): String = LocaleHelper.getSavedLanguageCode(context)

    override fun saveLanguageCode(
        context: Context,
        languageCode: String,
    ) {
        LocaleHelper.saveLanguageCode(context, languageCode)
    }

    @MainThread
    override fun restartApp(activity: Activity) {
        try {
            ProcessPhoenix.triggerRebirth(activity)
        } catch (e: Exception) {
            activity.finishAffinity()
            val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            if (intent != null) {
                activity.startActivity(intent)
            }
        }
    }
}
