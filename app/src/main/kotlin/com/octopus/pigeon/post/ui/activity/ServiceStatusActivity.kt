package com.octopus.pigeon.post.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.permission.PermissionManager
import com.octopus.pigeon.post.service.PermissionMonitorService
import com.octopus.pigeon.post.service.SmsKeepAliveWorker
import com.octopus.pigeon.post.service.SmsMonitorService
import com.octopus.pigeon.post.ui.base.BaseActivity
import com.octopus.pigeon.post.ui.screen.KeepAliveGuidanceCard
import com.octopus.pigeon.post.ui.screen.PermissionStatusCard
import com.octopus.pigeon.post.ui.theme.PigeonPostTheme
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel
import com.octopus.ui.spacing.AppSpacing

class ServiceStatusActivity : BaseActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                startSmsMonitorService()
            }
            updatePermissionMonitorService()
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PigeonPostTheme {
                val serviceEnabled by viewModel.serviceEnabled.collectAsState()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    stringResource(R.string.service_permission_status_title),
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back),
                                    )
                                }
                            },
                            colors =
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                        )
                    },
                ) { paddingValues ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = AppSpacing.small),
                    ) {
                        PermissionStatusCard(
                            hasPermissions = PermissionManager.hasAllPermissions(this@ServiceStatusActivity),
                            serviceEnabled = serviceEnabled,
                            onRequestPermissions = { requestPermissions() },
                            onStartService = { startSmsMonitorService() },
                            onStopService = { stopSmsMonitorService() },
                        )

                        // Explains and fixes the "works on day one, silent on day two" failure
                        KeepAliveGuidanceCard(
                            modifier = Modifier.padding(horizontal = AppSpacing.large),
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val missingPermissions = PermissionManager.getMissingPermissions(this)
        if (missingPermissions.isNotEmpty()) {
            PigeonLogger.info(TAG, "Requesting permissions: ${missingPermissions.joinToString()}")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun updatePermissionMonitorService() {
        val permissionIntent = Intent(this, PermissionMonitorService::class.java)
        if (PermissionManager.hasAllPermissions(this)) {
            stopService(permissionIntent)
        } else {
            startService(permissionIntent)
        }
    }

    private fun startSmsMonitorService() {
        if (PermissionManager.hasAllPermissions(this)) {
            PigeonLogger.info(TAG, "Starting SMS monitoring service")
            val intent = Intent(this, SmsMonitorService::class.java)
            startForegroundService(intent)
            SmsKeepAliveWorker.enqueue(this)

            viewModel.setServiceEnabled(true)
            updatePermissionMonitorService()
        }
    }

    private fun stopSmsMonitorService() {
        PigeonLogger.info(TAG, "Stopping SMS monitoring service")
        // Route the stop through the service so it can persist the disabled flag before shutting down.
        // Calling stopService() directly leaves a stale "enabled" flag that makes it restart itself.
        startService(
            Intent(this, SmsMonitorService::class.java).apply {
                action = SmsMonitorService.ACTION_STOP_SERVICE
            },
        )
        SmsKeepAliveWorker.cancel(this)

        val permissionIntent = Intent(this, PermissionMonitorService::class.java)
        stopService(permissionIntent)

        viewModel.setServiceEnabled(false)
        updatePermissionMonitorService()
    }

    companion object {
        private val TAG = ServiceStatusActivity::class.java.simpleName
    }
}
