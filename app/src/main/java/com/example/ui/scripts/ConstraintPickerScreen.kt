package com.example.ui.scripts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.GridView
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ConstraintAvailability
import com.example.data.ConstraintCategory
import com.example.data.ConstraintDescriptor
import com.example.data.ScriptConstraintCatalog
import com.example.data.ShortcutScript
import com.example.mode.ModeHooks
import com.example.ui.components.IconTile
import com.example.ui.components.MinTouchTarget
import com.example.ui.components.trailingEdgeFade
import com.example.ui.components.isWideLayout
import com.example.ui.theme.SectionLabel
import io.github.nicholasarda.keysnap.R

/**
 * Full-screen catalog of every constraint, opened from the ONLY IF card. A screen and not a bottom
 * sheet on purpose: the catalog is long, and the sheet's drag-to-dismiss kept swallowing the scroll
 * or closing the picker outright. Layout mirrors AddActionScreen.
 */
@Composable
fun ConstraintPickerScreen(
    automation: Boolean,
    onClose: () -> Unit,
    onPickConstraint: (String) -> Unit,
    scriptId: String = "",
    scripts: List<ShortcutScript> = emptyList(),
    isPro: Boolean = false,
    hideModeCondition: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    // Saveable: folding or unfolding the phone recreates the activity.
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<ConstraintCategory?>(null) }
    var tab by rememberSaveable { mutableStateOf<PickerTab?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("constraint_picker_sheet")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopBarIconTile(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.common_back),
                onClose,
                Modifier.testTag("constraint_picker_back"),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.constraint_picker_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
        }
        SearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.constraint_picker_search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("constraint_picker_search"),
        )
        Spacer(Modifier.height(12.dp))
        val resources = LocalContext.current.resources
        // Browsing offers only what can be added; a search still finds the unreleased ones.
        // Automation-only conditions are hidden from key shortcuts altogether.
        fun offeredOf(base: List<ConstraintDescriptor>, searching: Boolean) =
            (if (searching) base else base.filter { it.availability == ConstraintAvailability.AVAILABLE })
                .filter { automation || it.type !in ScriptConstraintCatalog.AUTOMATION_ONLY }
                .filter { ModeHooks.offered(it.type) && !ModeHooks.hidesFromEditor(it.type, scriptId, scripts, isPro) }
                .filter { !hideModeCondition || it.type != ScriptConstraintCatalog.MODE_IS }
        // No condition is phone-specific yet, so the phone tile never shows here.
        val shownTabs = buildList {
            if (offeredOf(ScriptConstraintCatalog.all, false).any { it.type in ScriptConstraintCatalog.NEW }) add(PickerTab.NEW)
            add(PickerTab.POPULAR)
        }
        val categoryScroll = rememberLazyListState()
        LazyRow(
            Modifier.fillMaxWidth().trailingEdgeFade(categoryScroll.canScrollForward),
            state = categoryScroll,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CategoryIconChip(
                    icon = Icons.Outlined.GridView,
                    label = stringResource(R.string.picker_category_all),
                    selected = selectedCategory == null && tab == null,
                    onClick = { selectedCategory = null; tab = null },
                )
            }
            items(shownTabs.size) { i ->
                val shown = shownTabs[i]
                PickerTabChip(shown, "", selected = tab == shown) {
                    tab = if (tab == shown) null else shown
                    selectedCategory = null
                }
            }
            items(ConstraintCategory.entries.size) { i ->
                val category = ConstraintCategory.entries[i]
                CategoryIconChip(
                    icon = constraintCategoryIcon(category),
                    label = stringResource(category.titleRes),
                    selected = selectedCategory == category,
                    onClick = { tab = null; selectedCategory = if (selectedCategory == category) null else category },
                    category = constraintTileCategory(category),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val searching = query.isNotBlank()
        val offered = offeredOf(if (searching) ScriptConstraintCatalog.search(query, resources) else ScriptConstraintCatalog.all, searching)
        val filtered = when (tab) {
            PickerTab.NEW -> offered.filter { it.type in ScriptConstraintCatalog.NEW }
            PickerTab.POPULAR -> ScriptConstraintCatalog.POPULAR.mapNotNull { type -> offered.find { it.type == type } }
            PickerTab.PHONE, null -> selectedCategory?.let { cat -> offered.filter { it.category == cat } } ?: offered
        }
        // A tab is one flat list (Popular keeps its own order); categories group under headers.
        val grouped: List<Pair<ConstraintCategory?, List<ConstraintDescriptor>>> = when {
            tab != null -> if (filtered.isEmpty()) emptyList() else listOf(null to filtered)
            else -> ConstraintCategory.entries.mapNotNull { category ->
                val entries = filtered.filter { it.category == category }
                if (entries.isEmpty()) null else category to entries
            }
        }
        // In the wide panel the rows go two to a line, so a name never has to wrap.
        val columns = if (isWideLayout()) 2 else 1
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
            if (grouped.isEmpty()) {
                item {
                    Text(
                        if (searching) {
                            stringResource(R.string.constraint_picker_no_match, query)
                        } else {
                            stringResource(R.string.constraint_picker_empty_category)
                        },
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            grouped.forEach { (category, entries) ->
                if (category != null) {
                    item(key = "header_${category.name}") {
                        Text(
                            stringResource(category.titleRes),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = SectionLabel,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                val rows = entries.chunked(columns)
                items(rows.size, key = { "constraint_${rows[it][0].type}" }) { i ->
                    PickerRow(rows[i]) { ConstraintRow(it, onPickConstraint) }
                }
            }
        }
    }
}

@Composable
private fun ConstraintRow(descriptor: ConstraintDescriptor, onAdd: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val available = descriptor.availability == ConstraintAvailability.AVAILABLE
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(
                enabled = available,
                onClickLabel = stringResource(R.string.constraint_picker_add_label, stringResource(descriptor.titleRes)),
                onClick = { onAdd(descriptor.type) },
            )
            .semantics { if (!available) disabled() }
            .testTag("constraint_row_${descriptor.type}")
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (available) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            constraintIcon(descriptor.type, descriptor.category),
            category = constraintTileCategory(descriptor.category),
            size = 32.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(descriptor.titleRes),
                    Modifier.weight(1f, fill = false),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (descriptor.type in ScriptConstraintCatalog.EXPERIMENTAL) ExperimentalBadge(Modifier.padding(start = 8.dp))
            }
            if (!available) {
                Text(
                    stringResource(R.string.common_coming_soon),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (available) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(colors.primary.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = colors.primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Outlined "Experimental" tag for a condition in [ScriptConstraintCatalog.EXPERIMENTAL]. */
@Composable
internal fun ExperimentalBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.settings_experimental),
        modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, colors.tertiary, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = colors.tertiary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}
