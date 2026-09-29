package com.octopus.pigeon.post.ui.base

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.octopus.locale.withLocale
import com.octopus.pigeon.post.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Base Activity using delegation pattern for locale management
 * No need to extend LocaleAwareComponentActivity - uses extension function instead
 */
open class BaseActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // Apply locale using extension function with custom provider
        val context =
            newBase.withLocale { ctx ->
                // Read language from PreferencesRepository synchronously
                val repository = PreferencesRepository(ctx)
                runBlocking { repository.appLanguage.first() }
            }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "${this::class.java.simpleName} created with locale-aware context")
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    super.onResume(owner)
                    onPermissionStateChanged()
                }
            },
        )
    }

    protected open fun onPermissionStateChanged() {
        Log.d(TAG, "onPermissionStateChanged called in ${this::class.java.simpleName}")
    }

    companion object {
        private val TAG = BaseActivity::class.java.simpleName
    }
}
