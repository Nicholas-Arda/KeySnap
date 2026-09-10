package com.example

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.data.AppLocale
import com.example.data.KeyMappingRepository
import com.example.data.resolveConsumeForScript
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    // The in-app language picker: Android 11/12 have no LocaleManager, so the activity wraps its
    // own base context. On 13+ AppLocale.set already told the platform, and this agrees with it.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = KeyMappingRepository.getInstance(applicationContext)
                return MainViewModel(repo, applicationContext) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            MyApplicationTheme(themeMode = themeMode) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshServiceStatus()
    }

    // Foreground key events are intercepted for real-time monitoring and recording. Down and up
    // must reach the view model separately but decide identically, so both delegate here.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        consumes(keyCode, KeyEvent.ACTION_DOWN) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        consumes(keyCode, KeyEvent.ACTION_UP) || super.onKeyUp(keyCode, event)

    private fun consumes(keyCode: Int, action: Int): Boolean {
        val handled = viewModel.onDirectActivityKeyEvent(keyCode, action)
        val config = viewModel.config.value
        val boundToConsumingScript = viewModel.isMappingEnabled.value &&
            viewModel.scripts.value.any { script ->
                script.enabled &&
                    script.triggers.any { it.keyCodes.singleOrNull() == keyCode } &&
                    resolveConsumeForScript(script, config.consumeOriginalEvent)
            }
        return boundToConsumingScript || (handled && config.consumeOriginalEvent)
    }
}

