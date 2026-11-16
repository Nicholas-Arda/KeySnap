package com.example.surface

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ScriptActionCatalog
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.scripts.actionIconChoices
import io.github.nicholasarda.keysnap.R

/**
 * A Quick Settings tile over a wallpaper, small and large, as the panel draws it (tap it to see the
 * other state of an on/off tile), with what a tap does and the tile's icon in the sheet under it.
 * [icon] is the chosen action type, or null for the shortcut's own icon, [defaultIcon].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TileStyleScreen(
    name: String,
    defaultIcon: ImageVector,
    all: Boolean,
    toggles: Boolean,
    onToggles: (Boolean) -> Unit,
    icon: String?,
    onIcon: (String?) -> Unit,
    onChangeShortcut: () -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit,
) {
    var previewOn by rememberSaveable { mutableStateOf(true) }
    // A run tile is never lit, as in refreshTile.
    val on = toggles && previewOn
    val glyph = icon?.let { chosen -> actionIconChoices().firstOrNull { it.first == chosen }?.second } ?: defaultIcon
    val sub = stringResource(
        when {
            all -> if (on) R.string.scripts_toggle_state_on else R.string.home_status_paused
            toggles -> if (on) R.string.scripts_toggle_state_on else R.string.scripts_toggle_state_off
            else -> R.string.app_name
        },
    )
    val tap = { previewOn = !previewOn }.takeIf { toggles }
    val tick = rememberTick()
    WallpaperScreen("tile_style_back", onClose) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 28.dp).testTag("tile_style_preview").clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTile(glyph, name, on, tap)
            LargeTile(glyph, name, sub, on, tap)
        }
        OptionsSurface(Modifier.weight(1f)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!all) {
                    SheetLabel(stringResource(R.string.widget_tap_title))
                    ChipRow {
                        PartChip(stringResource(R.string.widget_tap_run), Icons.Outlined.PlayArrow, !toggles, "tile_tap_run") {
                            onToggles(false)
                        }
                        PartChip(stringResource(R.string.widget_tap_toggle), Icons.Outlined.ToggleOn, toggles, "tile_tap_toggle") {
                            onToggles(true)
                        }
                    }
                }
                SheetLabel(stringResource(R.string.widget_part_icon))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconCell(defaultIcon, name, icon == null, "tile_icon_default") { tick(); onIcon(null) }
                    actionIconChoices().filter { it.second != defaultIcon }.forEach { (type, vector) ->
                        val label = ScriptActionCatalog.descriptor(type)?.let { stringResource(it.titleRes) } ?: type
                        IconCell(vector, label, icon == type, "tile_icon_$type") { tick(); onIcon(type) }
                    }
                }
                Button(
                    onClick = onDone,
                    shape = InnerShape,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = MinTouchTarget).testTag("tile_style_done"),
                ) { Text(stringResource(R.string.common_done)) }
                TextButton(
                    onClick = onChangeShortcut,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("tile_style_change_shortcut"),
                ) { Text(stringResource(R.string.surface_choose_shortcut)) }
            }
        }
    }
}

/** The round tile most panels use, with its name under it. */
@Composable
private fun SmallTile(glyph: ImageVector, name: String, on: Boolean, onTap: (() -> Unit)?) {
    val fill by animateColorAsState(tileFill(on), label = "tileFill")
    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(60.dp).clip(CircleShape).background(fill).then(tapModifier(onTap)),
            contentAlignment = Alignment.Center,
        ) { Icon(glyph, null, Modifier.size(26.dp), tint = tileInk(on)) }
        Text(
            name,
            Modifier.padding(top = 6.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** The wide tile with a name and a state line, as on Pixel and Samsung. */
@Composable
private fun LargeTile(glyph: ImageVector, name: String, sub: String, on: Boolean, onTap: (() -> Unit)?) {
    val fill by animateColorAsState(tileFill(on), label = "tileFill")
    val ink = tileInk(on)
    Row(
        Modifier.width(176.dp).heightIn(min = 68.dp).clip(RoundedCornerShape(22.dp)).background(fill).then(tapModifier(onTap))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(glyph, null, Modifier.size(24.dp), tint = ink)
        Column(Modifier.padding(start = 12.dp)) {
            Text(name, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = ink.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun tileFill(on: Boolean) = if (on) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .16f)

@Composable
private fun tileInk(on: Boolean) = if (on) MaterialTheme.colorScheme.onPrimary else Color.White

private fun tapModifier(onTap: (() -> Unit)?) = if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier

@Composable
private fun IconCell(vector: ImageVector, description: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(MinTouchTarget)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(vector, null, Modifier.size(22.dp), tint = if (selected) colors.onPrimary else colors.onSurfaceVariant)
    }
}
