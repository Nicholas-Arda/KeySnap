package com.example.capsule

import android.content.Context
import androidx.compose.runtime.Composable
import com.example.data.RunRecord
import com.example.data.WhatsNewRelease
import com.example.service.bridge.BridgeTrigger
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Public-source build. The key capsule is a KeySnap Pro feature whose source is kept private;
 * the Pro build replaces this file with the real implementation (see `keysnap.edition` in
 * app/build.gradle.kts). Everything here is a no-op so the free core builds and runs on its own.
 */
object CapsuleHooks {
    const val AVAILABLE = false

    fun whatsNewRelease(versionCode: Long): WhatsNewRelease? = null

    fun enabledFlow(context: Context): Flow<Boolean> = flowOf(false)

    fun isEnabled(context: Context): Boolean = false

    fun onRun(context: Context, record: RunRecord, appResults: List<Boolean?>, bridgeReport: List<Pair<String, Boolean>>?) = Unit

    fun onBridgeFeedback(context: Context, trigger: BridgeTrigger) = Unit

    @Composable
    fun Intro(themeMode: ThemeMode, onClose: () -> Unit, onExplorePro: () -> Unit) = Unit

    @Composable
    fun Editor(savedKeyLabels: Map<Int, String>, themeMode: ThemeMode, onClose: () -> Unit) = Unit
}
