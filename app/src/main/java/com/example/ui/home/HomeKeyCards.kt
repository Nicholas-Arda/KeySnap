package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bridge.KeyEventMapping
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import com.example.data.keyDisplayName
import com.example.ui.components.ConsoleShape
import com.example.ui.components.IconTile
import com.example.ui.components.LeftColumn
import com.example.ui.components.MinTouchTarget
import com.example.ui.components.TwoColumns
import com.example.ui.components.glassCard
import com.example.ui.scripts.ScriptCard
import com.example.ui.scripts.ShortcutChip
import com.example.ui.scripts.actionLabel
import com.example.ui.theme.LocalGlass
import com.example.ui.theme.SectionLabel
import io.github.nicholasarda.keysnap.R

/** Cards shown per section before "Show N more", as on the phone's recipe shelves. */
private const val SectionPeek = 4

/**
 * Home's shortcuts on a wide screen, in place of the phone's "Your shortcuts" rows: one card per
 * key or key combo saying what each press does, then the automations as the Shortcuts page draws
 * them. KeySnap does not know where a key sits on the phone, so a key is a card, not a drawing.
 */
@Composable
internal fun WideShortcutSections(
    scripts: List<ShortcutScript>,
    globallyEnabled: Boolean,
    failedScriptIds: Set<String>,
    onOpenScript: (String) -> Unit,
    onToggleScript: (String, Boolean) -> Unit,
    onGiveJob: (List<Int>, TriggerPressType) -> Unit,
) {
    // A combo is the same keys in any order; groups keep the order of the user's list.
    val keys = scripts.filter { !it.automation && it.triggers.isNotEmpty() }
        .groupBy { it.triggers.first().keyCodes.sorted() }
        .entries.toList()
    if (keys.isNotEmpty()) {
        SectionTitle(stringResource(R.string.home_your_keys))
        TwoColumns(keys) { (keyCodes, onKey) -> KeyCard(keyCodes, onKey, onOpenScript, onGiveJob) }
    }
    val card: @Composable (ShortcutScript) -> Unit = {
        ScriptCard(it, globallyEnabled, it.id in failedScriptIds, onToggleScript, onOpenScript)
    }
    CardSection(stringResource(R.string.home_automations), scripts.filter { it.automation }, card)
    CardSection(stringResource(R.string.trigger_kind_surface), scripts.filter { !it.automation && it.triggers.isEmpty() }, card)
}

@Composable
private fun SectionTitle(text: String, count: Int? = null) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = SectionLabel, color = colors.onSurfaceVariant)
        if (count != null) Text(count.toString(), style = SectionLabel, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun CardSection(title: String, scripts: List<ShortcutScript>, card: @Composable (ShortcutScript) -> Unit) {
    if (scripts.isEmpty()) return
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    SectionTitle(title, count = scripts.size.takeIf { it > SectionPeek })
    TwoColumns(if (expanded) scripts else scripts.take(SectionPeek)) { card(it) }
    if (scripts.size > SectionPeek && !expanded) LeftColumn {
        Text(
            pluralStringResource(R.plurals.home_recipes_show_more, scripts.size - SectionPeek, scripts.size - SectionPeek),
            Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .08f))
                .clickable(role = Role.Button) { expanded = true }
                .padding(vertical = 14.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/** One key: a row per press, each opening its shortcut, or starting one with this key and press. */
@Composable
private fun KeyCard(
    keyCodes: List<Int>,
    scripts: List<ShortcutScript>,
    onOpenScript: (String) -> Unit,
    onGiveJob: (List<Int>, TriggerPressType) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val res = LocalContext.current.resources
    // The order the user recorded them, which is how the editor names the combo too.
    val recorded = scripts.first().triggers.first().keyCodes
    val name = recorded.joinToString(" + ") { keyDisplayName(res, it) }
    // The assistant gesture only ever fires as a single press.
    val presses = if (KeyEventMapping.ASSISTANT_GESTURE in keyCodes) listOf(TriggerPressType.SINGLE_PRESS) else TriggerPressType.entries
    Card(
        Modifier.fillMaxWidth().glassCard(LocalGlass.current).testTag("home_key_card"),
        shape = ConsoleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.Smartphone, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (keyCodes.size >= 2) ShortcutChip(stringResource(R.string.home_held_together))
            }
            presses.forEach { press ->
                HorizontalDivider(color = colors.outline.copy(alpha = .45f))
                PressRow(press, scripts.filter { it.triggers.first().pressType == press }, { onGiveJob(recorded, press) }, onOpenScript)
            }
        }
    }
}

@Composable
private fun PressRow(press: TriggerPressType, scripts: List<ShortcutScript>, onGiveJob: () -> Unit, onOpenScript: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val first = scripts.firstOrNull()
    val action = first?.actions?.firstOrNull()
    val label = when {
        first == null -> stringResource(R.string.home_give_job)
        action == null -> stringResource(R.string.scripts_no_actions)
        else -> actionLabel(action)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.Button, onClickLabel = label) { if (first == null) onGiveJob() else onOpenScript(first.id) }
            .alpha(if (first != null && !first.enabled) .5f else 1f)
            .testTag("home_press_${press.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val badgeShape = RoundedCornerShape(8.dp)
        Text(
            stringResource(press.shortBadgeRes),
            Modifier.widthIn(min = 44.dp).clip(badgeShape)
                .then(
                    if (first == null) Modifier.border(1.dp, colors.outline.copy(alpha = .7f), badgeShape)
                    else Modifier.background(colors.primary.copy(alpha = .12f)),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (first == null) colors.onSurfaceVariant else colors.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    first == null -> colors.primary
                    action == null -> colors.error
                    else -> colors.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Another shortcut on the same press, e.g. in another mode.
            if (scripts.size > 1) ShortcutChip("+${scripts.size - 1}", accent = true)
        }
        Icon(
            if (first == null) Icons.Filled.Add else Icons.Outlined.ChevronRight,
            null,
            Modifier.size(20.dp),
            tint = if (first == null) colors.primary else colors.onSurfaceVariant,
        )
    }
}
