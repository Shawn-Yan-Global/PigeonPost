package com.octopus.pigeon.post.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import com.octopus.pigeon.post.R
import com.octopus.pigeon.post.ui.base.BaseActivity
import com.octopus.pigeon.post.ui.screen.ForwardRecordScreen
import com.octopus.pigeon.post.ui.theme.PigeonPostTheme
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel

class ForwardRecordsActivity : BaseActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PigeonPostTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = getString(R.string.forward_records)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = getString(R.string.back),
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
                    ForwardRecordScreen(paddingValues, viewModel = viewModel)
                }
            }
        }
    }
}
