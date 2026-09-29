package com.octopus.pigeon.post.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.octopus.pigeon.post.data.model.ActiveTimeConfig
import com.octopus.pigeon.post.data.model.EmailConfig
import com.octopus.pigeon.post.data.model.MatchLogic
import com.octopus.pigeon.post.data.model.TemplateConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pigeon_post_preferences")

/**
 * Repository for managing application preferences using DataStore
 * Handles all configuration data persistence
 */
class PreferencesRepository(
    private val context: Context,
) {
    private object PreferencesKeys {
        val KEYWORDS = stringSetPreferencesKey("keywords")
        val CASE_SENSITIVE = booleanPreferencesKey("case_sensitive")
        val MATCH_LOGIC = stringPreferencesKey("match_logic")
        val EMAIL_TEMPLATE = stringPreferencesKey("email_template")
        val USE_TEMPLATE = booleanPreferencesKey("use_template")
        val SMTP_SERVER = stringPreferencesKey("smtp_server")
        val SMTP_PORT = intPreferencesKey("smtp_port")
        val SENDER_EMAIL = stringPreferencesKey("sender_email")
        val PASSWORD = stringPreferencesKey("password")
        val RECIPIENT_EMAIL = stringPreferencesKey("recipient_email")
        val EMAIL_SUBJECT = stringPreferencesKey("email_subject")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val ACTIVE_TIME_ENABLED = booleanPreferencesKey("active_time_enabled")
        val ACTIVE_TIME_START_HOUR = intPreferencesKey("active_time_start_hour")
        val ACTIVE_TIME_START_MINUTE = intPreferencesKey("active_time_start_minute")
        val ACTIVE_TIME_END_HOUR = intPreferencesKey("active_time_end_hour")
        val ACTIVE_TIME_END_MINUTE = intPreferencesKey("active_time_end_minute")

        // Enhanced feature settings
        val NO_FILTER_FORWARD_MODE = booleanPreferencesKey("no_filter_forward_mode")
        val SMS_RECEIVED_NOTIFICATION_ENABLED =
            booleanPreferencesKey("sms_received_notification_enabled")
        val SMS_FORWARDED_NOTIFICATION_ENABLED =
            booleanPreferencesKey("sms_forwarded_notification_enabled")

        // Record auto cleanup settings
        val AUTO_CLEANUP_RECORDS_ENABLED = booleanPreferencesKey("auto_cleanup_records_enabled")
        val AUTO_CLEANUP_RECORDS_DAYS = intPreferencesKey("auto_cleanup_records_days")

        // Log auto cleanup settings
        val AUTO_CLEANUP_LOGS_ENABLED = booleanPreferencesKey("auto_cleanup_logs_enabled")
        val AUTO_CLEANUP_LOGS_DAYS = intPreferencesKey("auto_cleanup_logs_days")

        /** Salted SHA-256 of the debug menu password. See [DebugAuth]. */
        val DEBUG_PASSWORD_HASH = stringPreferencesKey("debug_password_hash")
        val DEBUG_UNLOCKED = booleanPreferencesKey("debug_unlocked")

        // App language settings (for UI, not logs)
        val APP_LANGUAGE = stringPreferencesKey("app_language")
    }

    val templateConfig: Flow<TemplateConfig> =
        context.dataStore.data.map { preferences ->
            TemplateConfig(
                keywords = preferences[PreferencesKeys.KEYWORDS]?.toList() ?: emptyList(),
                caseSensitive = preferences[PreferencesKeys.CASE_SENSITIVE] ?: false,
                matchLogic =
                    preferences[PreferencesKeys.MATCH_LOGIC]?.let {
                        MatchLogic.valueOf(it)
                    } ?: MatchLogic.ANY,
                emailTemplate =
                    preferences[PreferencesKeys.EMAIL_TEMPLATE]
                        ?: "",
                // Empty default, UI layer will provide i18n default
                useTemplate = preferences[PreferencesKeys.USE_TEMPLATE] ?: false,
            )
        }

    val emailConfig: Flow<EmailConfig> =
        context.dataStore.data.map { preferences ->
            EmailConfig(
                smtpServer = preferences[PreferencesKeys.SMTP_SERVER] ?: "",
                smtpPort = preferences[PreferencesKeys.SMTP_PORT] ?: 587,
                senderEmail = preferences[PreferencesKeys.SENDER_EMAIL] ?: "",
                password = preferences[PreferencesKeys.PASSWORD] ?: "",
                recipientEmail = preferences[PreferencesKeys.RECIPIENT_EMAIL] ?: "",
                emailSubject =
                    preferences[PreferencesKeys.EMAIL_SUBJECT]
                        ?: "",
                // Empty default, UI layer will provide i18n default
            )
        }

    val serviceEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SERVICE_ENABLED] ?: false
        }

    val activeTimeConfig: Flow<ActiveTimeConfig> =
        context.dataStore.data.map { preferences ->
            ActiveTimeConfig(
                isEnabled = preferences[PreferencesKeys.ACTIVE_TIME_ENABLED] ?: false,
                startHour = preferences[PreferencesKeys.ACTIVE_TIME_START_HOUR] ?: 8,
                startMinute = preferences[PreferencesKeys.ACTIVE_TIME_START_MINUTE] ?: 0,
                endHour = preferences[PreferencesKeys.ACTIVE_TIME_END_HOUR] ?: 9,
                endMinute = preferences[PreferencesKeys.ACTIVE_TIME_END_MINUTE] ?: 0,
            )
        }

    suspend fun updateTemplateConfig(config: TemplateConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEYWORDS] = config.keywords.toSet()
            preferences[PreferencesKeys.CASE_SENSITIVE] = config.caseSensitive
            preferences[PreferencesKeys.MATCH_LOGIC] = config.matchLogic.name
            preferences[PreferencesKeys.EMAIL_TEMPLATE] = config.emailTemplate
            preferences[PreferencesKeys.USE_TEMPLATE] = config.useTemplate
        }
    }

    suspend fun updateEmailConfig(config: EmailConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMTP_SERVER] = config.smtpServer
            preferences[PreferencesKeys.SMTP_PORT] = config.smtpPort
            preferences[PreferencesKeys.SENDER_EMAIL] = config.senderEmail
            preferences[PreferencesKeys.PASSWORD] = config.password
            preferences[PreferencesKeys.RECIPIENT_EMAIL] = config.recipientEmail
            preferences[PreferencesKeys.EMAIL_SUBJECT] = config.emailSubject
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVICE_ENABLED] = enabled
        }
    }

    suspend fun updateActiveTimeConfig(config: ActiveTimeConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_TIME_ENABLED] = config.isEnabled
            preferences[PreferencesKeys.ACTIVE_TIME_START_HOUR] = config.startHour
            preferences[PreferencesKeys.ACTIVE_TIME_START_MINUTE] = config.startMinute
            preferences[PreferencesKeys.ACTIVE_TIME_END_HOUR] = config.endHour
            preferences[PreferencesKeys.ACTIVE_TIME_END_MINUTE] = config.endMinute
        }
    }

    // Enhanced feature flow configurations
    val noFilterForwardMode: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.NO_FILTER_FORWARD_MODE] ?: false
        }

    val smsReceivedNotificationEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SMS_RECEIVED_NOTIFICATION_ENABLED] ?: true
        }

    val smsForwardedNotificationEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SMS_FORWARDED_NOTIFICATION_ENABLED] ?: true
        }

    val autoCleanupRecordsEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.AUTO_CLEANUP_RECORDS_ENABLED] ?: false
        }

    val autoCleanupRecordsDays: Flow<Int> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.AUTO_CLEANUP_RECORDS_DAYS] ?: 7
        }

    val autoCleanupLogsEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.AUTO_CLEANUP_LOGS_ENABLED] ?: false
        }

    val autoCleanupLogsDays: Flow<Int> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.AUTO_CLEANUP_LOGS_DAYS] ?: 7
        }

    // App language settings flow (for UI, not logs)
    val appLanguage: Flow<String> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.APP_LANGUAGE]
                ?: Locale.getDefault().language // Default to English
        }

    // Enhanced feature update methods
    suspend fun setNoFilterForwardMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NO_FILTER_FORWARD_MODE] = enabled
        }
    }

    suspend fun setSmsReceivedNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_RECEIVED_NOTIFICATION_ENABLED] = enabled
        }
    }

    suspend fun setSmsForwardedNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_FORWARDED_NOTIFICATION_ENABLED] = enabled
        }
    }

    suspend fun setAutoCleanupRecords(
        enabled: Boolean,
        days: Int,
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CLEANUP_RECORDS_ENABLED] = enabled
            preferences[PreferencesKeys.AUTO_CLEANUP_RECORDS_DAYS] = days
        }
    }

    suspend fun setAutoCleanupLogs(
        enabled: Boolean,
        days: Int,
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CLEANUP_LOGS_ENABLED] = enabled
            preferences[PreferencesKeys.AUTO_CLEANUP_LOGS_DAYS] = days
        }
    }

    /**
     * Stored as a salted SHA-256 hash rather than the password itself, so the
     * plaintext cannot be recovered by unpacking the APK.
     *
     * This is a speed bump against a curious user, not a security boundary:
     * the verifier is embedded in the binary, so a determined reader can brute
     * force it offline. It protects nothing that matters to an attacker who
     * already has the APK.
     */
    val debugPasswordHash: Flow<String> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DEBUG_PASSWORD_HASH] ?: DebugAuth.DEFAULT_PASSWORD_HASH
        }

    suspend fun setDebugPasswordHash(hash: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEBUG_PASSWORD_HASH] = hash
        }
    }

    /**
     * Whether the debug menu has been unlocked on this install.
     *
     * Sticks around, so the password is asked once rather than on every visit.
     */
    val debugUnlocked: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DEBUG_UNLOCKED] ?: false
        }

    suspend fun setDebugUnlocked(unlocked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEBUG_UNLOCKED] = unlocked
        }
    }

    // App language settings function (for UI, not logs)
    suspend fun setAppLanguage(language: String) {
        // Save to DataStore for consistency
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LANGUAGE] = language
        }
        // Also save to SharedPreferences for attachBaseContext access
        // This uses LocaleHelper to ensure consistency across the app
        com.octopus.locale.LocaleHelper
            .saveLanguageCode(context, language)
    }
}
