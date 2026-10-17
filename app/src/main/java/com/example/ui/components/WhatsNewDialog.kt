package com.example.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import com.example.ui.theme.whatsNewSurfaceColors
import io.github.nicholasarda.keysnap.BuildConfig
import io.github.nicholasarda.keysnap.R

@Composable
fun WhatsNewDialog(
    release: WhatsNewRelease,
    themeMode: ThemeMode,
    onDismiss: () -> Unit,
    onOpenSettings: (WhatsNewDestination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (surface, itemSurface) = whatsNewSurfaceColors(themeMode)
    // Insets read as zero inside the dialog window, which starts below the status bar yet is as
    // tall as the display, so read both bars here in the activity and clear them by hand. Without
    // this, at larger display sizes the Got it button sat under a 3-button navigation bar.
    val barsClearance = with(LocalDensity.current) {
        (WindowInsets.statusBars.getTop(this) + WindowInsets.navigationBars.getBottom(this)).toDp()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp + barsClearance),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxWidth / LocalDensity.current.fontScale < 300.dp
            Surface(
                modifier = Modifier.padding(horizontal = if (compact) 8.dp else 20.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight).testTag("whats_new_dialog"),
                shape = RoundedCornerShape(28.dp),
                color = surface,
                contentColor = colors.onSurface,
            ) {
                Column {
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(if (compact) 12.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!compact) Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) {
                                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.matchParentSize())
                                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.matchParentSize())
                            }
                            Column {
                                Text(stringResource(R.string.whats_new_title), style = MaterialTheme.typography.headlineSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(R.string.whats_new_version, whatsNewVersionLabel(BuildConfig.VERSION_NAME)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        WhatsNewSectionLabel(stringResource(R.string.whats_new_new))
                        release.features.forEach { feature ->
                            Surface(shape = RoundedCornerShape(16.dp), color = itemSurface) {
                                WhatsNewFeatureCard(feature) { onOpenSettings(feature.destination) }
                            }
                        }
                        WhatsNewSectionLabel(stringResource(R.string.whats_new_fixed))
                        release.fixes.forEach { fix ->
                            WhatsNewFix(fix, compact)
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp).fillMaxWidth().testTag("whats_new_got_it"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(stringResource(R.string.whats_new_got_it))
                    }
                }
            }
        }
    }
}

@Composable
internal fun WhatsNewFix(fix: Int, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth().testTag("whats_new_fix_$fix"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) WhatsNewIconTile(if (fix == R.string.whats_new_fix_assistant) Icons.Filled.AutoAwesome else Icons.Outlined.Check)
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
internal fun WhatsNewFeatureCard(feature: WhatsNewFeature, onClick: () -> Unit) {
    val icon = when (feature.destination) {
        WhatsNewDestination.ADVANCED_MODE -> Icons.Outlined.RestartAlt
        WhatsNewDestination.APPEARANCE -> Icons.Outlined.Palette
        WhatsNewDestination.ABOUT -> Icons.Outlined.BugReport
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
        // At larger text/display sizes keep the action trailing, on its own line, rather than crush text.
        val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.15f
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!stacked) WhatsNewIconTile(icon)
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
private fun WhatsNewIconTile(icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp))
        }
    }
}
