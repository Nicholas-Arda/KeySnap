package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.example.data.AppLocale
import com.example.data.KeyMappingRepository
import com.example.data.shouldConsumeKey
import com.example.service.RunFailureNotifier
import com.example.service.DiagnosticRecorder
import com.example.ui.MainScreen
import com.example.ui.components.BottomBarClearance
import com.example.ui.settings.RecordingIndicator
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.resolveThemeMode
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
        requestFastestRefreshRate()
        setContent {
            val storedMode by viewModel.themeMode.collectAsState()
            val followSystem by viewModel.themeFollowSystem.collectAsState()
            val oled by viewModel.oledMode.collectAsState()
            val themeMode = resolveThemeMode(storedMode, followSystem, isSystemInDarkTheme())
            MyApplicationTheme(themeMode = themeMode, oled = oled) {
                Box(Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                    RecordingIndicator(
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = BottomBarClearance + 8.dp),
                    )
                }
            }
        }
        // A recreated activity has already handled the intent it was started with; rotating the
        // phone must not import the same share again either.
        if (savedInstanceState == null) {
            openScriptFrom(intent)
            importShared(intent)
        }
    }

    // singleTask: a notification tapped, or a shortcut shared in, while the app is already open arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openScriptFrom(intent)
        importShared(intent)
    }

    /** ACTION_SEND from any app is untrusted; the view model validates it and asks before anything is saved. */
    private fun importShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        // getCharSequenceExtra: some apps send styled text, which getStringExtra returns as null.
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        when {
            text != null -> viewModel.importShortcutText(text)
            stream != null -> viewModel.importShortcutUri(stream)
        }
    }

    private fun openScriptFrom(intent: Intent?) {
        val id = intent?.getStringExtra(RunFailureNotifier.EXTRA_SCRIPT_ID) ?: return
        intent.removeExtra(RunFailureNotifier.EXTRA_SCRIPT_ID)
        viewModel.openScriptFromNotification(id)
    }

    // ColorOS's default display setting runs apps that ask for nothing at 90 Hz on a 120 Hz panel,
    // which made every animation here visibly less fluid. Ask for the fastest mode at the current
    // resolution; the system can still lower it (battery saver, OEM policy).
    private fun requestFastestRefreshRate() {
        val display = display ?: return
        val current = display.mode
        val fastest = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.modeId }
    }

    override fun onResume() {
        super.onResume()
        if (DiagnosticRecorder.recording) DiagnosticRecorder.checkTimeout()
        viewModel.refreshServiceStatus()
        viewModel.refreshPurchases()
    }

    // Foreground key events are intercepted for real-time monitoring and recording. Down and up
    // must reach the view model separately but decide identically, so both delegate here.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        consumes(keyCode, KeyEvent.ACTION_DOWN, event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        consumes(keyCode, KeyEvent.ACTION_UP, event) || super.onKeyUp(keyCode, event)

    private fun consumes(keyCode: Int, action: Int, event: KeyEvent?): Boolean {
        // Read before dispatch: the release that ends a recording clears the flag.
        val recording = viewModel.isRecordingKey.value
        val isMatched = viewModel.onDirectActivityKeyEvent(keyCode, action)
        val config = viewModel.config.value
        val consumed = shouldConsumeKey(viewModel.scripts.value, keyCode, viewModel.isMappingEnabled.value, config.consumeOriginalEvent) ||
            (recording && config.consumeOriginalEvent)
        if (DiagnosticRecorder.recording) {
            DiagnosticRecorder.keyEvent(
                path = DiagnosticRecorder.InputPath.ACTIVITY,
                stage = DiagnosticRecorder.Stage.ENTRY,
                keyCode = keyCode,
                isDown = action == KeyEvent.ACTION_DOWN,
                repeatCount = event?.repeatCount,
                flags = event?.flags,
                source = event?.source,
                deviceId = event?.deviceId,
                matched = isMatched,
                consumed = consumed,
            )
        }
        return consumed
    }
}

