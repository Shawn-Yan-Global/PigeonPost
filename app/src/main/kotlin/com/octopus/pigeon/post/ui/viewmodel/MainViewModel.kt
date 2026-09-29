package com.octopus.pigeon.post.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.data.database.AppDatabase
import com.octopus.pigeon.post.data.model.ActiveTimeConfig
import com.octopus.pigeon.post.data.model.EmailConfig
import com.octopus.pigeon.post.data.model.SmsRecord
import com.octopus.pigeon.post.data.model.TemplateConfig
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import com.octopus.pigeon.post.data.repository.SmsRepository
import com.octopus.pigeon.post.service.EmailService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val smsRepository = SmsRepository(database.smsRecordDao())
    val preferencesRepository = PreferencesRepository(application)
    private val emailService = EmailService(application)

    private val _recentRecords = MutableStateFlow<List<SmsRecord>>(emptyList())
    val recentRecords: StateFlow<List<SmsRecord>> = _recentRecords.asStateFlow()

    private val _templateConfig = MutableStateFlow(TemplateConfig())
    val templateConfig: StateFlow<TemplateConfig> = _templateConfig.asStateFlow()

    private val _emailConfig = MutableStateFlow(EmailConfig())
    val emailConfig: StateFlow<EmailConfig> = _emailConfig.asStateFlow()

    private val _serviceEnabled = MutableStateFlow(false)
    val serviceEnabled: StateFlow<Boolean> = _serviceEnabled.asStateFlow()

    private val _activeTimeConfig = MutableStateFlow(ActiveTimeConfig())
    val activeTimeConfig: StateFlow<ActiveTimeConfig> = _activeTimeConfig.asStateFlow()

    private val _testEmailResult = MutableStateFlow<TestEmailResult?>(null)
    val testEmailResult: StateFlow<TestEmailResult?> = _testEmailResult.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            smsRepository.getRecentRecords().collect { records ->
                _recentRecords.value = records
            }
        }

        viewModelScope.launch {
            preferencesRepository.templateConfig.collect { config ->
                _templateConfig.value = config
            }
        }

        viewModelScope.launch {
            preferencesRepository.emailConfig.collect { config ->
                _emailConfig.value = config
            }
        }

        viewModelScope.launch {
            preferencesRepository.serviceEnabled.collect { enabled ->
                _serviceEnabled.value = enabled
            }
        }

        viewModelScope.launch {
            preferencesRepository.activeTimeConfig.collect { config ->
                _activeTimeConfig.value = config
            }
        }
    }

    fun updateTemplateConfig(config: TemplateConfig) {
        viewModelScope.launch {
            preferencesRepository.updateTemplateConfig(config)
        }
    }

    fun updateEmailConfig(config: EmailConfig) {
        viewModelScope.launch {
            preferencesRepository.updateEmailConfig(config)
        }
    }

    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setServiceEnabled(enabled)
        }
    }

    fun updateActiveTimeConfig(config: ActiveTimeConfig) {
        viewModelScope.launch {
            preferencesRepository.updateActiveTimeConfig(config)
        }
    }

    fun sendTestEmail() {
        viewModelScope.launch {
            _testEmailResult.value = TestEmailResult.Loading

            val config = _emailConfig.value
            val result = emailService.sendTestEmail(config)

            _testEmailResult.value =
                if (result.isSuccess) {
                    TestEmailResult.Success
                } else {
                    TestEmailResult.Error(
                        result.exceptionOrNull()?.message
                            ?: getApplication<Application>().getString(R.string.unknown_error),
                    )
                }
        }
    }

    fun clearTestEmailResult() {
        _testEmailResult.value = null
    }

    fun retryForwardSms(smsRecord: SmsRecord) {
        viewModelScope.launch {
            val config = _emailConfig.value
            val templateConfig = _templateConfig.value
            val result = emailService.sendSmsEmail(config, smsRecord, templateConfig)

            smsRepository.updateForwardStatus(
                id = smsRecord.id,
                success = result.isSuccess,
                errorMessage = result.exceptionOrNull()?.message,
            )
        }
    }

    fun retryForwardSmsWithFilter(smsRecord: SmsRecord) {
        viewModelScope.launch {
            val config = _emailConfig.value
            val templateConfig = _templateConfig.value
            val noFilterMode = preferencesRepository.noFilterForwardMode.first()

            // Check if should forward based on filter rules
            var shouldForward = false

            if (noFilterMode) {
                // No-filter mode - always forward
                shouldForward = true
            } else {
                // Check if SMS matches template
                val smsMatchingService =
                    com.octopus.pigeon.post.service
                        .SmsMatchingService()
                shouldForward = smsMatchingService.isSmsMatchTemplate(smsRecord.content, templateConfig)
            }

            if (shouldForward) {
                // Send email
                val result = emailService.sendSmsEmail(config, smsRecord, templateConfig)

                smsRepository.updateForwardStatus(
                    id = smsRecord.id,
                    success = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message,
                )
            } else {
                // Update status to indicate it didn't match filter
                smsRepository.updateForwardStatus(
                    id = smsRecord.id,
                    success = false,
                    errorMessage = getApplication<Application>().getString(R.string.sms_not_match_filter),
                )
            }
        }
    }
}

sealed class TestEmailResult {
    object Loading : TestEmailResult()

    object Success : TestEmailResult()

    data class Error(
        val message: String,
    ) : TestEmailResult()
}
