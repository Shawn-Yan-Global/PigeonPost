package com.octopus.pigeon.post.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.octopus.pigeon.post.ui.base.BaseActivity
import com.octopus.pigeon.post.ui.screen.DebugScreen
import com.octopus.pigeon.post.ui.theme.PigeonPostTheme
import com.octopus.pigeon.post.ui.viewmodel.MainViewModel

class DebugActivity : BaseActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PigeonPostTheme {
                DebugScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                )
            }
        }
    }
}
