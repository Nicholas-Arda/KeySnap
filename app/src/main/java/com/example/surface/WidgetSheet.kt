package com.example.surface

import android.app.Activity
import android.app.WallpaperManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.example.ui.components.MinTouchTarget
import io.github.nicholasarda.keysnap.R

/*
 * The pieces both widget style screens share: the widget shown over a wallpaper, and a sheet of
 * chips and swatches under it.
 */

/**
 * A wallpaper in the phone's own colours with a close button, for a widget preview to sit on.
 * Status bar icons turn light over it; the theme sets them on every recomposition, so this is a
 * SideEffect too, and the picker closes after these screens, so nothing restores them.
 */
@Composable
internal fun WallpaperScreen(backTag: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false }
    }
    Column(Modifier.fillMaxSize().background(rememberWallpaperBrush())) {
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(8.dp).size(48.dp).testTag(backTag)) {
            // A dark disc keeps the cross readable on a light wallpaper too.
            Box(Modifier.size(36.dp).background(Color.Black.copy(alpha = .28f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_back), tint = Color.White)
            }
        }
        content()
    }
}

/** A gradient in the phone's wallpaper colours (no permission needed), or a night blue without them. */
@Composable
private fun rememberWallpaperBrush(): Brush {
    val context = LocalContext.current
    return remember {
        val colors = runCatching { WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
        val first = colors?.primaryColor?.toArgb() ?: 0xFF5B78C4.toInt()
        val second = colors?.secondaryColor?.toArgb() ?: 0xFF26305A.toInt()
        Brush.radialGradient(
            listOf(Color(first), Color(ColorUtils.blendARGB(second, NIGHT, .3f)), Color(NIGHT)),
            center = Offset(0f, 0f),
            radius = 2600f,
        )
    }
}

private const val NIGHT = 0xFF131628.toInt()

/** The frosted sheet the options sit in, with its grab handle. */
@Composable
internal fun OptionsSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow.copy(alpha = .96f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).background(colors.outlineVariant, CircleShape))
        content()
    }
}

/** A light tick for a chip that changes the widget. */
@Composable
internal fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }
}

@Composable
internal fun SheetLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 2.dp, top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun ChipRow(spacing: Int = 8, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** One chip per phone look, each with a tiny ground and on-dot in that look's colours. */
@Composable
internal fun LookChips(looks: List<WidgetLook>, style: WidgetStyle, tagPrefix: String, onStyle: (WidgetStyle) -> Unit) {
    val context = LocalContext.current
    val tick = rememberTick()
    ChipRow {
        looks.forEach { look ->
            val spec = listSpec(context, look, style.accent)
            // The light looks keep the row white and colour only the knob, so their dot is the accent.
            val (ground, dot) = when (look) {
                WidgetLook.CLASSIC -> context.getColor(R.color.keysnap_blue) to context.getColor(R.color.white)
                WidgetLook.PIXEL -> spec.ground to spec.rowOn
                WidgetLook.NOTHING -> spec.ground to spec.thumbOn
                WidgetLook.ONE_UI, WidgetLook.HYPER_OS -> spec.ground to spec.trackOn
            }
            FilterChip(
                selected = style.look == look,
                onClick = { tick(); onStyle(style.copy(look = look)) },
                label = { Text(stringResource(look.titleRes)) },
                leadingIcon = {
                    Box(
                        Modifier.size(18.dp).background(Color(ground), RoundedCornerShape(5.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f), RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(7.dp).background(Color(dot), CircleShape)) }
                },
                modifier = Modifier.testTag("${tagPrefix}_${look.name.lowercase()}"),
            )
        }
    }
}

/** "Auto" and the [WidgetAccents] for a phone look's "on" colour, in one row that scrolls sideways. */
@Composable
internal fun AccentRow(style: WidgetStyle, onStyle: (WidgetStyle) -> Unit) {
    ChipRow(spacing = 4) {
        val onColor = stringResource(R.string.widget_style_on_color)
        Swatch(Brush.sweepGradient(AUTO_RAINBOW), style.accent == null, stringResource(R.string.widget_color_auto), "widget_accent_auto") {
            onStyle(style.copy(accent = null))
        }
        WidgetAccents.forEachIndexed { index, accent ->
            Swatch(solidBrush(accent), style.accent == accent, "$onColor ${index + 1}", "widget_accent_$index") {
                onStyle(style.copy(accent = accent))
            }
        }
    }
}

internal fun solidBrush(color: Int) = Brush.linearGradient(listOf(Color(color), Color(color)))

@Composable
internal fun Swatch(fill: Brush, selected: Boolean, description: String, tag: String, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, label = "swatch")
    val ring = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(MinTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .scale(scale)
                .then(if (selected) Modifier.border(2.dp, ring, CircleShape).padding(4.dp) else Modifier)
                .clip(CircleShape)
                .background(fill)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

@Composable
internal fun PartChip(label: String, icon: ImageVector, on: Boolean, tag: String? = null, onClick: () -> Unit) {
    val tick = rememberTick()
    FilterChip(
        selected = on,
        onClick = { tick(); onClick() },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = tag?.let { Modifier.testTag(it) } ?: Modifier,
    )
}

private val AUTO_RAINBOW = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77), Color(0xFF4D96FF), Color(0xFFC77DFF), Color(0xFFFF6B6B),
)
