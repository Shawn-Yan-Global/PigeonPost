package com.octopus.locale

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Base ComponentActivity with locale awareness
 * Automatically applies locale changes from a Flow-based language provider
 *
 * Usage:
 * ```kotlin
 * class MyActivity : LocaleAwareComponentActivity(
 *     languageProvider = { context -> myDataStore.languageFlow }
 * ) {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         // Your activity code
 *     }
 * }
 * ```
 *
 * @param languageProvider Function that provides a Flow of language codes for the given context
 * @param localeManager Optional custom locale manager (defaults to AppLocaleManagerImpl)
 */
open class LocaleAwareComponentActivity(
    private val languageProvider: (Context) -> Flow<String>,
    private val localeManager: AppLocaleManager = AppLocaleManagerImpl,
) : ComponentActivity() {
    private val localeDelegate = LocaleDelegate(localeManager)

    /**
     * Apply locale in attachBaseContext
     * Reads language synchronously from SharedPreferences (used by LocaleHelper)
     */
    override fun attachBaseContext(newBase: Context) {
        val contextWithLocale = localeDelegate.attachBaseContext(newBase)
        super.attachBaseContext(contextWithLocale)
    }

    /**
     * Observe language changes and recreate activity when language changes
     * Subclasses can override this to customize behavior
     */
    protected open fun observeLanguageChanges() {
        lifecycleScope.launch {
            languageProvider(this@LocaleAwareComponentActivity).collect { newLanguage ->
                val currentLanguage = localeManager.getSavedLanguageCode(this@LocaleAwareComponentActivity)
                if (newLanguage != currentLanguage) {
                    // Language changed, save and recreate activity
                    localeManager.saveLanguageCode(this@LocaleAwareComponentActivity, newLanguage)
                    recreate()
                }
            }
        }
    }
}
