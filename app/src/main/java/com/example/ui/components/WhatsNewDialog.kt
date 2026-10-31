package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.example.ui.home.KeySnapLockup
import com.example.ui.home.rememberReducedMotion
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.WhatsNewDestination
import com.example.data.WhatsNewFeature
import com.example.data.WhatsNewRelease
import com.example.data.whatsNewVersionLabel
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WhatsNewAccents
import com.example.ui.theme.whatsNewSurfaceColors
import io.github.nicholasarda.keysnap.BuildConfig
import io.github.nicholasarda.keysnap.R

/**
 * [logoOrigin] is the header lockup's on-screen bounds when the dialog was opened from it: that
 * lockup then flies into the dialog's corner and back out on dismiss. Null (auto-shown, opened
 * from Settings, or reduced motion) fades the dialog in with its lockup already in place.
 */
@Composable
fun WhatsNewDialog(
    release: WhatsNewRelease,
    themeMode: ThemeMode,
    onDismiss: () -> Unit,
    onOpenSettings: (WhatsNewDestination) -> Unit,
    versionName: String = BuildConfig.VERSION_NAME,
    logoOrigin: Rect? = null,
    /** The flying lockup is now drawn: the header may hide its own, with no empty frame between. */
    onLogoLifted: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val (surface, itemSurface) = whatsNewSurfaceColors(themeMode)
    // Insets read as zero inside the dialog window, which starts below the status bar yet is as
    // tall as the display, so read both bars here in the activity and clear them by hand. Without
    // this, at larger display sizes the Got it button sat under a 3-button navigation bar.
    val barsClearance = with(LocalDensity.current) {
        (WindowInsets.statusBars.getTop(this) + WindowInsets.navigationBars.getBottom(this)).toDp()
    }
    val origin = logoOrigin.takeUnless { rememberReducedMotion() }
    val flight = remember { Animatable(if (origin != null) 0f else 1f) }
    val enter = remember { Animatable(0f) }
    var anchor by remember { mutableStateOf<Offset?>(null) }
    var slot by remember { mutableStateOf<Rect?>(null) }
    var closing by remember { mutableStateOf(false) }
    var landed by remember { mutableStateOf(origin == null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(280)) }
    LaunchedEffect(slot != null) {
        // A light overshoot as it lands: the logo "snaps" into its corner.
        if (slot != null && origin != null) {
            flight.animateTo(1f, spring(dampingRatio = .6f, stiffness = 380f))
            landed = true
        }
    }
    fun close() {
        if (closing) return
        closing = true
        if (origin == null || slot == null) return onDismiss()
        landed = false
        scope.launch {
            launch { enter.animateTo(0f, tween(260)) }
            flight.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }
    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // The dim follows our own fade, and the window has no exit animation of its own: otherwise
        // the system fades the dim out again after the logo is already home, which reads as a stall.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(dialogWindow) {
            dialogWindow ?: return@LaunchedEffect
            if (origin != null) dialogWindow.setWindowAnimations(0)
            snapshotFlow { enter.value }.collect { dialogWindow.setDimAmount(DialogDim * it) }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp + barsClearance),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxWidth / LocalDensity.current.fontScale < 300.dp
            // Unmoved reference point for the flying lockup, which is drawn outside the card so the
            // card's rounded clip never cuts it off on the way in.
            Box(Modifier.align(Alignment.TopStart).size(0.dp).onGloballyPositioned { anchor = it.positionOnScreen() })
            Surface(
                modifier = Modifier.padding(horizontal = if (compact) 8.dp else 20.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight)
                    .graphicsLayer {
                        alpha = enter.value
                        translationY = (1f - enter.value) * 24.dp.toPx()
                    }
                    .testTag("whats_new_dialog"),
                shape = RoundedCornerShape(28.dp),
                color = surface,
                contentColor = colors.onSurface,
            ) {
                Column {
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(if (compact) 12.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column {
                            Box(
                                Modifier.width(if (compact) 150.dp else 196.dp)
                                    .onGloballyPositioned { slot = Rect(it.positionOnScreen(), it.size.toSize()) }
                                    // Hidden while its flying twin is on the way, so only one logo shows.
                                    .graphicsLayer { alpha = if (landed) 1f else 0f },
                            ) {
                                KeySnapLockup(themeMode, Modifier.width(if (compact) 150.dp else 196.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.whats_new_title), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.headlineSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), fontWeight = FontWeight.Bold)
                                    Text("🎉", style = MaterialTheme.typography.headlineSmall)
                                }
                                Text(
                                    stringResource(R.string.whats_new_version, whatsNewVersionLabel(versionName)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        if (release.highlightTitle != null && release.highlightDescription != null) {
                            Surface(shape = RoundedCornerShape(16.dp), color = colors.primary.copy(alpha = .14f), modifier = Modifier.testTag("whats_new_highlight")) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!compact) WhatsNewIconTile(Icons.Outlined.Celebration)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(release.highlightTitle), style = MaterialTheme.typography.titleSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), fontWeight = FontWeight.Bold)
                                        Text(stringResource(release.highlightDescription), style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (release.features.isNotEmpty()) {
                            WhatsNewSectionLabel(stringResource(R.string.whats_new_new))
                        }
                        release.features.forEachIndexed { index, feature ->
                            Surface(shape = RoundedCornerShape(16.dp), color = itemSurface) {
                                WhatsNewFeatureCard(feature, whatsNewAccent(index)) { onOpenSettings(feature.destination) }
                            }
                        }
                        if (release.fixes.isNotEmpty()) {
                            WhatsNewSectionLabel(stringResource(R.string.whats_new_fixed))
                            release.fixes.forEachIndexed { index, fix ->
                                WhatsNewFix(fix, compact, whatsNewAccent(release.features.size + index))
                            }
                        }
                    }
                    Button(
                        onClick = ::close,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp).fillMaxWidth().testTag("whats_new_got_it"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(stringResource(R.string.whats_new_got_it))
                    }
                }
            }
            val from = origin
            val to = slot
            val base = anchor
            if (!landed && from != null && to != null && base != null) {
                val density = LocalDensity.current
                LaunchedEffect(Unit) { onLogoLifted() }
                Box(
                    Modifier.align(Alignment.TopStart)
                        .size(with(density) { to.width.toDp() }, with(density) { to.height.toDp() })
                        .graphicsLayer {
                            val t = flight.value
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = lerp(from.left, to.left, t) - base.x
                            translationY = lerp(from.top, to.top, t) - base.y
                            scaleX = lerp(from.width / to.width, 1f, t)
                            scaleY = scaleX
                        },
                ) {
                    KeySnapLockup(themeMode, Modifier.width(with(density) { to.width.toDp() }))
                }
            }
        }
    }
}

@Composable
internal fun WhatsNewFix(fix: Int, compact: Boolean, accent: Color = MaterialTheme.colorScheme.primary) {
    Row(
        Modifier.fillMaxWidth().testTag("whats_new_fix_$fix"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) WhatsNewIconTile(fixIcon(fix), accent)
        Text(
            stringResource(fix),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
        )
    }
}

@Composable
private fun WhatsNewSectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun WhatsNewFeatureCard(feature: WhatsNewFeature, accent: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    val icon = when (feature.title) {
        R.string.whats_new_test_editors_title_120 -> Icons.Outlined.PlayArrow
        R.string.whats_new_reorder_shortcuts_title_120 -> Icons.Outlined.SwapVert
        R.string.whats_new_input_devices_title_120 -> Icons.Outlined.Keyboard
        else -> null
    } ?: when (feature.destination) {
        WhatsNewDestination.ADVANCED_MODE -> Icons.Outlined.RestartAlt
        WhatsNewDestination.APPEARANCE -> Icons.Outlined.Palette
        WhatsNewDestination.ABOUT -> Icons.Outlined.BugReport
        WhatsNewDestination.KEY_CAPSULE -> Icons.Outlined.TouchApp
        WhatsNewDestination.SHORTCUTS -> Icons.Outlined.TouchApp
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
        // At larger text/display sizes keep the action trailing, on its own line, rather than crush text.
        val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.15f
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!stacked) WhatsNewIconTile(icon, accent)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(feature.title), style = MaterialTheme.typography.titleSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), fontWeight = FontWeight.Bold)
                    Text(stringResource(feature.description), style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!stacked) WhatsNewPill(feature, onClick)
            }
            if (stacked) Box(Modifier.align(Alignment.End)) { WhatsNewPill(feature, onClick) }
        }
    }
}

@Composable
private fun WhatsNewPill(feature: WhatsNewFeature, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.testTag("whats_new_${feature.destination.name.lowercase()}"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(stringResource(feature.button), style = MaterialTheme.typography.labelLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph))
    }
}

@Composable
private fun WhatsNewIconTile(icon: ImageVector, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.16f),
        contentColor = accent,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp))
        }
    }
}

/** Neighbouring rows never share a colour, so each line catches the eye on its own. */
private fun whatsNewAccent(index: Int): Color = WhatsNewAccents[index % WhatsNewAccents.size]

private fun fixIcon(fix: Int): ImageVector = when (fix) {
    R.string.whats_new_fix_assistant -> Icons.Filled.AutoAwesome
    R.string.whats_new_fix_setup_120 -> Icons.Outlined.Checklist
    R.string.whats_new_fix_conditions_120 -> Icons.Outlined.Block
    R.string.whats_new_fix_app_names_120 -> Icons.Outlined.Apps
    R.string.whats_new_fix_side_power_keys_120 -> Icons.Outlined.PowerSettingsNew
    R.string.whats_new_fix_idle_battery_120 -> Icons.Outlined.BatteryChargingFull
    R.string.whats_new_fix_advanced_setup_120 -> Icons.Outlined.Build
    R.string.whats_new_fix_saved_keys_120 -> Icons.Outlined.Key
    R.string.whats_new_fix_shortcuts_page_120 -> Icons.Outlined.Dashboard
    R.string.whats_new_fix_modes_120 -> Icons.Outlined.Layers
    else -> Icons.Outlined.Check
}

/** Matches the platform dialog dim this window had before it followed our fade. */
private const val DialogDim = 0.6f
