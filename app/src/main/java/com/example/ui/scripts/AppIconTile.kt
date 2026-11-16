package com.example.ui.scripts

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.data.InstalledAppsRepository
import com.example.ui.components.InnerShape

private val TileSize = 36.dp

/**
 * An app's launcher icon in a fixed 36 dp slot, so rows do not shift when it arrives. The letter
 * tile shows until the icon is decoded (off the main thread, cached by [InstalledAppsRepository])
 * and cross-fades into it. A package the app cannot load, because it is not installed or not
 * visible through the manifest's `<queries>`, keeps the letter.
 */
@Composable
internal fun AppIconTile(packageName: String, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { InstalledAppsRepository(context) }
    val sizePx = with(LocalDensity.current) { TileSize.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packageName, sizePx) {
        value = null
        if (packageName.isNotEmpty()) value = repository.icon(packageName, sizePx)
    }
    Crossfade(icon, modifier.size(TileSize), tween(150), label = "appIcon") { bitmap ->
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, Modifier.size(TileSize))
        } else {
            val colors = MaterialTheme.colorScheme
            Box(Modifier.size(TileSize).background(colors.primary.copy(alpha = .12f), InnerShape), contentAlignment = Alignment.Center) {
                Text(label.firstOrNull()?.uppercaseChar()?.toString() ?: "•", color = colors.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
