package com.example.ui.scripts

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import androidx.compose.ui.unit.dp
import com.example.data.InstalledApp
import com.example.data.InstalledAppsRepository
import com.example.ui.components.MinTouchTarget

private val IconSize = 40.dp

/**
 * Launcher apps, searchable. A package with no launcher entry never appears here — that is what
 * the configuration sheet's typed package-name field is for.
 *
 * Icons are decoded per row, off the main thread, only once the row composes, and cached by the
 * repository — decoding several hundred drawables up front is what would make a cold picker stutter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    apps: List<InstalledApp>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    loadIcon: suspend (packageName: String, sizePx: Int) -> ImageBitmap? = LocalContext.current.let { context ->
        remember(context) { InstalledAppsRepository(context)::icon }
    },
) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
        }
    }
    val listHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().testTag("app_picker_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.action_config_choose_app),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("app_picker_search"),
                placeholder = { Text(stringResource(R.string.app_picker_search)) },
                singleLine = true,
                enabled = apps.isNotEmpty(),
            )
            // Three distinct outcomes, each said plainly: still loading, nothing installed to show,
            // and a search that matched nothing. Showing "Loading" for all three lies about two.
            Box(Modifier.fillMaxWidth().height(listHeight)) {
                when {
                    apps.isEmpty() -> LoadingApps()
                    filtered.isEmpty() -> NoMatches(query)
                    else -> LazyColumn(Modifier.fillMaxWidth()) {
                        items(filtered.size, key = { filtered[it].packageName }) { index ->
                            AppRow(
                                app = filtered[index],
                                selected = filtered[index].packageName == selected,
                                onSelect = { onSelect(filtered[index].packageName) },
                                loadIcon = loadIcon,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LoadingApps() {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(28.dp))
        Text(
            stringResource(R.string.app_picker_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoMatches(query: String) {
    Text(
        stringResource(R.string.app_picker_no_match, query),
        Modifier.fillMaxWidth().padding(24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AppRow(
    app: InstalledApp,
    selected: Boolean,
    onSelect: () -> Unit,
    loadIcon: suspend (String, Int) -> ImageBitmap?,
) {
    val colors = MaterialTheme.colorScheme
    val sizePx = with(LocalDensity.current) { IconSize.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.packageName) { value = loadIcon(app.packageName, sizePx) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.RadioButton, onClickLabel = stringResource(R.string.app_picker_choose, app.label), onClick = onSelect)
            .testTag("app_option_${app.packageName}")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Fixed-size slot either way, so rows do not shift when the icon arrives.
        Box(Modifier.size(IconSize)) {
            icon?.let { Image(it, contentDescription = null, Modifier.size(IconSize)) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, stringResource(R.string.app_picker_selected), tint = colors.primary)
        }
    }
}
