package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SectionLabel

/**
 * The app has exactly two radii, and every rounded element picks one by what it is rather than by
 * how big it is: [ConsoleShape] for panels — cards, sheets, the floating bottom bar, the trigger
 * row, full-width buttons — and [InnerShape] for tiles — glyph tiles, chips, badges, small buttons,
 * search fields. A circle is not a radius choice: those stay `CircleShape`.
 */
internal val ConsoleShape = RoundedCornerShape(20.dp)
val InnerShape = RoundedCornerShape(12.dp)

/** Android's minimum comfortable touch target. Any row or pill that takes a tap should reach it. */
val MinTouchTarget = 48.dp

/**
 * Fades out the trailing edge of a horizontally scrolling row while [more] of it is off-screen. A
 * rail that stops dead at the screen edge reads as the end of the list, so the categories past it
 * are never looked for. Alpha rather than a colour wash, so it works on whatever paints behind.
 */
fun Modifier.trailingEdgeFade(more: Boolean): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (more) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    1f to Color.Transparent,
                    startX = size.width - 40.dp.toPx(),
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

@Composable
fun ConsoleSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A titled panel. [testTag] is passed rather than derived from [label] so renaming user-facing copy
 * never silently breaks a test that was looking for the old wording.
 *
 * When [onHeaderClick] is set the whole header row is the one target - the trailing icon is an
 * indicator inside it, not a second control competing for the same tap.
 */
@Composable
fun ConsolePanel(
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
    onHeaderClick: (() -> Unit)? = null,
    isExpanded: Boolean? = null,
    headerActionDescription: String? = null,
    headerActionIcon: ImageVector? = null,
    alert: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth().testTag(testTag),
        shape = ConsoleShape,
        colors = CardDefaults.cardColors(
            containerColor = if (alert) colors.error.copy(alpha = .10f).compositeOver(colors.surface) else colors.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, if (alert) colors.error.copy(alpha = .55f) else colors.outline.copy(alpha = .4f)),
    ) {
        // A clickable header needs a 48dp tap target; giving it the column's top padding instead of
        // stacking on top of it keeps its label on the same line as every plain panel's label.
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = if (onHeaderClick == null) 16.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (onHeaderClick != null) {
                            Modifier
                                .heightIn(min = MinTouchTarget)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = headerActionDescription,
                                    onClick = onHeaderClick,
                                )
                                // The chevron is the only open/closed cue; these two actions are
                                // what makes TalkBack say "expanded"/"collapsed" as well.
                                .semantics {
                                    when (isExpanded) {
                                        true -> collapse { onHeaderClick(); true }
                                        false -> expand { onHeaderClick(); true }
                                        null -> Unit
                                    }
                                }
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(1f), style = SectionLabel, color = if (alert) colors.error else colors.onSurfaceVariant)
                if (headerActionIcon != null) {
                    Icon(headerActionIcon, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                }
            }
            content()
        }
    }
}
