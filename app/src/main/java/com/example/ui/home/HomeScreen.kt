package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import com.example.data.EntitlementManager
import com.example.data.ScriptActionCatalog
import com.example.data.ShortcutScript
import com.example.data.SuggestedShortcut
import com.example.ui.components.ConsolePanel
import com.example.ui.components.ConsoleShape
import com.example.ui.components.GroundedPage
import com.example.ui.components.IconTile
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.scripts.triggerSummary
import com.example.ui.theme.ThemeMode
import io.github.nicholasarda.keysnap.R

@Composable
fun HomeScreen(
    isMappingEnabled: Boolean,
    isServiceActive: Boolean,
    isAdvancedModeRunning: Boolean,
    triggerCount: Int,
    lastSource: String,
    isPro: Boolean,
    proPrice: String?,
    activeScriptCount: Int,
    activeScriptLimit: Int,
    themeMode: ThemeMode,
    scripts: List<ShortcutScript>,
    onOpenScript: (String) -> Unit,
    onToggleMapping: () -> Unit,
    onOpenWizard: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onPickSuggestion: (SuggestedShortcut) -> Unit,
    onOpenProScreen: () -> Unit,
    onCycleTheme: () -> Unit = {},
    nudgeDismissed: Boolean = true,
    onDismissNudge: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    scrollToProCard: Boolean = false,
    onScrolledToProCard: () -> Unit = {},
) {
    val permissionGaps = rememberPermissionGaps()
    val setupIncomplete = !isServiceActive || permissionGaps.any
    // The tutorial plays a closing line and shrinks away rather than vanishing mid-frame, so its
    // item outlives `setupIncomplete` by the length of that exit and retires itself.
    var setupCardRetired by rememberSaveable { mutableStateOf(!setupIncomplete) }
    LaunchedEffect(setupIncomplete) { if (setupIncomplete) setupCardRetired = false }
    val showNudge = setupCardRetired && scripts.isEmpty() && !nudgeDismissed
    LaunchedEffect(scrollToProCard) {
        if (scrollToProCard && !isPro) {
            // The Pro card is the last thing on the page.
            scrollState.animateScrollTo(scrollState.maxValue)
            onScrolledToProCard()
        }
    }
    GroundedPage(themeMode, scrollState) {
        ConsoleHeader(
            isMappingEnabled = isMappingEnabled,
            themeMode = themeMode,
            onToggleMapping = onToggleMapping,
            onCycleTheme = onCycleTheme,
        )
        if (!setupCardRetired) {
            SetupCard(
                gaps = permissionGaps,
                isServiceActive = isServiceActive,
                onOpenAccessibility = onOpenAccessibility,
                onRetired = { setupCardRetired = true },
            )
        }
        LiveStatusPanel(
            isServiceActive = isServiceActive,
            isAdvancedActive = isAdvancedModeRunning,
            triggerCount = triggerCount,
            lastSource = lastSource,
            onOpenWizard = onOpenWizard,
            onOpenAccessibility = onOpenAccessibility,
        )
        if (scripts.isNotEmpty()) YourShortcutsPanel(scripts, onOpenScript)
        if (showNudge) FirstShortcutNudge(onDismiss = onDismissNudge)
        SuggestedShortcutsPanel(isPro = isPro, scripts = scripts, onPick = onPickSuggestion)
        if (!isPro) {
            ProUpsellCard(
                activeCount = activeScriptCount,
                limit = activeScriptLimit,
                proPrice = proPrice,
                onOpenProScreen = onOpenProScreen,
            )
        }
    }
}

/**
 * Explains the free limit beside its usage count and opens the existing Pro purchase dialog.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ProUpsellCard(
    activeCount: Int,
    limit: Int,
    proPrice: String?,
    onOpenProScreen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val atCap = activeCount >= limit
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(ConsoleShape)
            .background(colors.primary.copy(alpha = .08f))
            .border(1.dp, colors.primary.copy(alpha = .4f), ConsoleShape)
            .testTag("pro_upsell_card")
            .padding(16.dp),
    ) {
        val stacked = maxWidth / LocalDensity.current.fontScale < 240.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!stacked) {
                    Box(
                        Modifier.size(36.dp).clip(InnerShape).background(colors.primary.copy(alpha = .2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, null, tint = colors.primary, modifier = Modifier.size(19.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name_pro), style = MaterialTheme.typography.titleSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = colors.onSurface)
                    Text(
                        if (atCap) {
                            stringResource(R.string.pro_card_at_cap)
                        } else {
                            stringResource(R.string.pro_card_subtitle, EntitlementManager.FREE_SCRIPT_LIMIT)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            // Label, "1 / 3" and the bar are one fact. Spoken separately TalkBack says the ratio twice
            // and then a percentage; cleared and relabelled it says it once, in words.
            val progressDescription = stringResource(R.string.pro_progress_description, activeCount, limit)
            Column(
                Modifier.clearAndSetSemantics { contentDescription = progressDescription },
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    maxItemsInEachRow = if (stacked) 1 else 2,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.pro_active_shortcuts), style = MaterialTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = colors.onSurfaceVariant)
                    Text(
                        "$activeCount / $limit",
                        // A count pair reads left to right in every language; RTL would flip it to "2 / 1".
                        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                        color = colors.onSurface,
                    )
                }
                LinearProgressIndicator(
                    progress = { if (limit == 0) 1f else (activeCount.toFloat() / limit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = if (atCap) colors.error else colors.primary,
                    trackColor = colors.surfaceVariant,
                )
            }

            FreeVsProTable(stacked)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenProScreen,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget).testTag("pro_upsell_purchase_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    // Play's formatted price only exists once the product query answers; without it the
                    // button keeps its price-less label rather than showing a guessed amount. No line
                    // cap: in a long language the price must wrap, not be cut to "…".
                    Text(
                        if (proPrice != null) {
                            stringResource(R.string.pro_unlock_with_price, proPrice)
                        } else {
                            stringResource(R.string.pro_unlock)
                        },
                        style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The free and Pro active-shortcut limits, compared in one place. */
@Composable
private fun FreeVsProTable(stacked: Boolean) {
    val colors = MaterialTheme.colorScheme
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HorizontalDivider(color = colors.outline.copy(alpha = .3f))
            Text(stringResource(R.string.pro_active_shortcuts), style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = colors.onSurface)
            Text(
                "${stringResource(R.string.pro_table_free)}: ${EntitlementManager.FREE_SCRIPT_LIMIT}",
                style = MaterialTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                color = colors.onSurfaceVariant,
            )
            Text(
                "${stringResource(R.string.pro_table_pro)}: ${stringResource(R.string.pro_table_unlimited)}",
                style = MaterialTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                color = colors.onSurface,
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Three cells per row: read one at a time they are just loose words with no column to
        // belong to, so every row is merged into a single "<label>: Free x, Pro y" utterance.
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Spacer(Modifier.weight(1.3f))
            Text(
                stringResource(R.string.pro_table_free),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.pro_table_pro),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
            )
        }
        HorizontalDivider(color = colors.outline.copy(alpha = .3f))
        ComparisonRow(
            stringResource(R.string.pro_active_shortcuts),
            free = "${EntitlementManager.FREE_SCRIPT_LIMIT}",
            pro = stringResource(R.string.pro_table_unlimited),
        )
    }
}

@Composable
private fun ComparisonRow(label: String, free: String, pro: String) {
    val colors = MaterialTheme.colorScheme
    val rowDescription = stringResource(R.string.pro_table_row_description, label, free, pro)
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = rowDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
        Text(
            free,
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        // No line cap: "Без ограничений" has to wrap onto a second line, not lose its second word.
        Text(
            pro,
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface,
        )
    }
}

@Composable
internal fun ConsoleHeader(
    isMappingEnabled: Boolean,
    themeMode: ThemeMode,
    onToggleMapping: () -> Unit,
    onCycleTheme: () -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        content = {
            // The logo doubles as the theme switch: Light, Dark, Sepia in turn.
            Box(
                Modifier
                    .heightIn(min = MinTouchTarget)
                    .testTag("header_logo")
                    .clip(InnerShape)
                    .clickable(onClickLabel = stringResource(R.string.home_switch_theme), onClick = onCycleTheme),
                contentAlignment = Alignment.Center,
            ) {
                KeySnapLockup(themeMode)
            }
            Box(Modifier.testTag("header_controls")) {
                MappingBadge(isMappingEnabled, onToggleMapping)
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // Keep the status beside the floating Settings gear on one row, including at 200% text.
        // Share tight widths with the vector logo; the status can ellipsize within what remains.
        val rowWidth = (constraints.maxWidth - 60.dp.roundToPx()).coerceAtLeast(0)
        val gap = minOf(12.dp.roundToPx(), rowWidth)
        val contentWidth = rowWidth - gap
        val logo = measurables[0].measure(loose.copy(maxWidth = (contentWidth * .55f).toInt()))
        val controls = measurables[1].measure(loose.copy(maxWidth = contentWidth - logo.width))
        val height = maxOf(logo.height, controls.height)
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            logo.placeRelative(0, (height - logo.height) / 2)
            controls.placeRelative(rowWidth - controls.width, (height - controls.height) / 2)
        }
    }
}

/** The global mapping switch: states what is true now, and flips it on tap. */
@Composable
private fun MappingBadge(active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = if (active) colors.primary else colors.error
    Row(
        Modifier
            .testTag("mapping_badge")
            .clip(CircleShape)
            .heightIn(min = MinTouchTarget)
            .clickable(
                role = Role.Switch,
                onClickLabel = stringResource(if (active) R.string.home_pause_all else R.string.home_resume_all),
                onClick = onClick,
            )
            // Role.Switch alone leaves TalkBack with no on/off to read out.
            .semantics { toggleableState = ToggleableState(active) }
            .background(accent.copy(alpha = .14f))
            .border(1.dp, accent.copy(alpha = .55f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(7.dp))
        Text(
            stringResource(if (active) R.string.home_status_live else R.string.home_status_paused),
            modifier = Modifier.testTag("mapping_status_label"),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveStatusPanel(
    isServiceActive: Boolean,
    isAdvancedActive: Boolean,
    triggerCount: Int,
    lastSource: String,
    onOpenWizard: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    // Anything that needs fixing is the setup card's job now, so this panel only ever reports:
    // it never paints an alarm and never opens itself.
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    ConsolePanel(
        label = stringResource(R.string.home_system_status),
        testTag = "system_status",
        onHeaderClick = { isExpanded = !isExpanded },
        isExpanded = isExpanded,
        headerActionDescription = stringResource(if (isExpanded) R.string.home_collapse_status else R.string.home_expand_status),
        headerActionIcon = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
    ) {
        AnimatedVisibility(visible = isExpanded) {
            Column {
                StatusLine(
                    label = stringResource(R.string.home_accessibility_service),
                    status = stringResource(if (isServiceActive) R.string.home_status_active else R.string.home_status_offline),
                    active = isServiceActive,
                    onClick = onOpenAccessibility,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                StatusLine(
                    // Reports whether the detached bridge answers its control port, which is not
                    // the same thing as an ADB connection — so it is named for what it measures.
                    label = stringResource(R.string.home_status_advanced),
                    status = stringResource(if (isAdvancedActive) R.string.home_status_running else R.string.home_status_off),
                    active = isAdvancedActive,
                    onClick = onOpenWizard,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // The counter lives in the coordinator and starts at zero with the process, so it
                // is a session total. Calling it "today" would claim a number the app cannot back.
                Text(
                    stringResource(R.string.home_triggers_session),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isExpanded && lastSource.isNotBlank()) {
                    Text(
                        stringResource(R.string.home_last_from, lastSource),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                triggerCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** One system dependency, reported, with the screen that changes it one tap away. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StatusLine(
    label: String,
    status: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val statusColor = if (active) colors.primary else colors.onSurfaceVariant
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.home_open_label_settings, label),
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
    ) {
        val stacked = maxWidth / LocalDensity.current.fontScale < 240.dp
        FlowRow(
            Modifier.fillMaxWidth(),
            maxItemsInEachRow = if (stacked) 1 else 2,
        ) {
            Text(
                label,
                (if (stacked) Modifier.fillMaxWidth() else Modifier.weight(1f)).align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                color = colors.onSurface,
            )
            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(6.dp))
                Text(status, style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = statusColor)
                Icon(
                    Icons.Outlined.ChevronRight,
                    null,
                    Modifier.padding(start = 2.dp).size(18.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * What the user actually built, above the ready-made suggestions — without it Home looks identical
 * before and after the first shortcut exists. A row, not a card: the enable toggle and the delete
 * button stay on Scripts, so this stays a place to glance and open.
 */
@Composable
private fun YourShortcutsPanel(scripts: List<ShortcutScript>, onOpenScript: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    ConsolePanel(label = stringResource(R.string.home_your_shortcuts), testTag = "your_shortcuts") {
        Column {
            scripts.forEachIndexed { index, script ->
                if (index > 0) HorizontalDivider(color = colors.outline.copy(alpha = .45f))
                ShortcutRow(script, onOpenScript)
            }
        }
    }
}

@Composable
internal fun ShortcutRow(script: ShortcutScript, onOpenScript: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = script.name.ifBlank { stringResource(R.string.scripts_unnamed) }
    val (triggerText, triggerOk) = triggerSummary(script)
    val firstAction = script.actions.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .testTag("home_script_${script.id}")
            .clickable(role = Role.Button, onClickLabel = name, onClick = { onOpenScript(script.id) })
            .alpha(if (script.enabled) 1f else 0.5f)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (firstAction != null) {
            IconTile(
                iconForScript(script),
                ScriptActionCatalog.descriptor(firstAction.type)?.category,
                size = 38.dp,
            )
        } else {
            Box(Modifier.size(38.dp).clip(InnerShape).background(colors.surfaceVariant))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                triggerText,
                style = MaterialTheme.typography.bodySmall,
                color = if (triggerOk) colors.onSurfaceVariant else colors.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
    }
}
