package com.example.ui.scripts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.example.ui.components.IconTile
import com.example.ui.components.MinTouchTarget
import com.example.ui.components.trailingEdgeFade
import com.example.ui.theme.SectionLabel
import io.github.nicholasarda.keysnap.R

/**
 * Full-screen catalog of every constraint, opened from the ONLY IF card. A screen and not a bottom
 * sheet on purpose: the catalog is long, and the sheet's drag-to-dismiss kept swallowing the scroll
 * or closing the picker outright. Layout mirrors AddActionScreen.
 */
@Composable
fun ConstraintPickerScreen(onClose: () -> Unit, onPickConstraint: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ConstraintCategory?>(null) }

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
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                )
            }
            items(ConstraintCategory.entries.size) { i ->
                val category = ConstraintCategory.entries[i]
                CategoryIconChip(
                    icon = constraintCategoryIcon(category),
                    label = stringResource(category.titleRes),
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = if (selectedCategory == category) null else category },
                    category = constraintTileCategory(category),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val searching = query.isNotBlank()
        val resources = LocalContext.current.resources
        val base = if (searching) ScriptConstraintCatalog.search(query, resources) else ScriptConstraintCatalog.all
        // Browsing offers only what can be added; a search still finds the unreleased ones.
        val offered = if (searching) base else base.filter { it.availability == ConstraintAvailability.AVAILABLE }
        val filtered = selectedCategory?.let { cat -> offered.filter { it.category == cat } } ?: offered
        val grouped = ConstraintCategory.entries.mapNotNull { category ->
            val entries = filtered.filter { it.category == category }
            if (entries.isEmpty()) null else category to entries
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
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
                item(key = "header_${category.name}") {
                    Text(
                        stringResource(category.titleRes),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = SectionLabel,
                        color = colors.onSurfaceVariant,
                    )
                }
                items(entries.size, key = { "constraint_${entries[it].type}" }) { i ->
                    ConstraintRow(entries[i], onPickConstraint)
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
            Text(
                stringResource(descriptor.titleRes),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
