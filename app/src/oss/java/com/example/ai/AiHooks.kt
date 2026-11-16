package com.example.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.WhatsNewFeature
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.MainViewModel

object AiHooks {
    const val AVAILABLE = false

    @Composable
    fun contentClearance(): Dp = 0.dp

    fun whatsNewFeatures(versionCode: Long): List<WhatsNewFeature> = emptyList()

    class Session(@Suppress("UNUSED_PARAMETER") context: android.content.Context) {
        fun onCommitted(@Suppress("UNUSED_PARAMETER") id: String) = Unit
    }

    @Composable
    fun AskBar(
        viewModel: MainViewModel,
        visible: Boolean,
        open: Boolean,
        collapsed: Boolean,
        onOpenChange: (Boolean) -> Unit,
        themeMode: ThemeMode,
        oledMode: Boolean,
        onExplorePro: () -> Unit,
        modifier: Modifier = Modifier,
        onOpenModels: () -> Unit = {},
    ) = Unit

    @Composable
    fun ModelSettings(onOpenModels: () -> Unit = {}) = Unit

    @Composable
    fun ModelsOverlay(open: Boolean, onDismiss: () -> Unit, isPro: Boolean) = Unit
}
