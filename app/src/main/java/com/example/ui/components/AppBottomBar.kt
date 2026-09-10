package com.example.ui.components

import androidx.compose.animation.animateColor
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import androidx.compose.ui.unit.dp

private data class BottomNavDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val page: Int,
    val testTag: String,
)

private val bottomNavDestinations = listOf(
    BottomNavDestination(R.string.nav_home, Icons.Default.Home, 0, "bottom_nav_home"),
    BottomNavDestination(R.string.nav_shortcuts, Icons.Default.Bolt, 1, "bottom_nav_scripts"),
    BottomNavDestination(R.string.nav_settings, Icons.Default.Settings, 2, "bottom_nav_settings"),
)

/**
 * A minimal floating bar: no filled indicator pill behind the active tab, just its icon/label
 * turning `primary`.
 */
@Composable
fun AppBottomBar(selectedPage: Int, onPageSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ConsoleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        ),
    ) {
        val selectionTransition = updateTransition(
            targetState = selectedPage,
            label = "bottomNavSelection",
        )

        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomNavDestinations.forEach { destination ->
                val isSelected = selectedPage == destination.page
                val contentColor by selectionTransition.animateColor(
                    transitionSpec = { tween(150, easing = FastOutSlowInEasing) },
                    label = "navItem${destination.page}Color",
                ) { page ->
                    if (page == destination.page) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // The bar's height lives on the items, not the Surface: at 200% font scale
                        // the label needs more than 68dp and a fixed bar would clip it.
                        .heightIn(min = 68.dp)
                        .clip(InnerShape)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onPageSelected(destination.page) },
                        )
                        .testTag(destination.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(destination.icon, contentDescription = null, tint = contentColor)
                    Text(
                        text = stringResource(destination.labelRes),
                        color = contentColor,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
