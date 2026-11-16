package com.example.ui.components

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Call inside full-screen Dialog content with decorFitsSystemWindows=false and the default
 * usePlatformDefaultWidth=true. Compose 1.7's custom-width path caps content at screenHeightDp
 * (which can exclude the navigation bar) and overwrites our MATCH_PARENT height after layout.
 * The platform measurement path instead uses the full window bounds set here, including both bars.
 */
@Composable
@Suppress("DEPRECATION")
fun EdgeToEdgeDialogWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val light = MaterialTheme.colorScheme.background.luminance() > .5f
    SideEffect {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        window.attributes = window.attributes.apply {
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // Without it the decor paints legacy black bands behind both bars (seen on Android 16).
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}
