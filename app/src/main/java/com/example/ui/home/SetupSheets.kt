package com.example.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import com.example.ui.components.InnerShape
import com.example.ui.theme.setupSuccess
import com.example.ui.theme.setupWarning
import io.github.nicholasarda.keysnap.R

@Composable
fun SetupCard(
    gaps: PermissionGaps,
    isServiceActive: Boolean,
    onOpenAccessibility: () -> Unit,
) {
    val state = RequiredSetupState(isServiceActive, gaps.battery, gaps.notifications)
    if (state.banner == null) return
    val tint = if (state.banner == SetupBanner.NotRunning) MaterialTheme.colorScheme.error else setupWarning()
    val text = stringResource(if (state.banner == SetupBanner.NotRunning) R.string.setup_not_running else R.string.setup_background_risk)
    BannerRow(text, tint, onOpenAccessibility, Modifier.testTag("permission_alerts")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_progress, state.completeCount), color = tint, style = MaterialTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph))
            repeat(2) { index ->
                Box(Modifier.size(width = 28.dp, height = 4.dp).background(
                    tint.copy(alpha = if (index < state.completeCount) 1f else .2f), InnerShape))
            }
        }
    }
}

/** Tinted banner: message (+ optional extra line) on the left, a filled Fix button on the right. */
@Composable
private fun BannerRow(
    text: String,
    tint: Color,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().background(tint.copy(alpha = .10f), InnerShape)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, null, Modifier.padding(end = 8.dp).size(18.dp), tint = tint)
                Text(text, color = tint,
                    style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph))
            }
            extra()
        }
        // A real button, so people tap it rather than read it.
        Button(
            onClick = onFix,
            modifier = Modifier.padding(start = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = lerp(tint, Color.Black, .2f), contentColor = Color.White),
        ) {
            Text(stringResource(R.string.setup_fix), fontWeight = FontWeight.Bold)
        }
    }
}

/** One-line amber banner for a conditional permission (usage access). */
@Composable
fun UsageAccessBanner(onFix: () -> Unit) {
    val tint = setupWarning()
    BannerRow(stringResource(R.string.setup_usage_banner), tint, onFix, Modifier.testTag("usage_access_alert"))
}

/** Optional notifications prompt: neutral card, bell, Turn on, close. */
@Composable
fun NotificationsCard(onTurnOn: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(colors.onSurface.copy(alpha = .06f), InnerShape)
        .testTag("notifications_card").padding(start = 14.dp, top = 2.dp, bottom = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Notifications, null, Modifier.padding(end = 8.dp).size(18.dp), tint = colors.onSurfaceVariant)
        Text(stringResource(R.string.setup_optional_row), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onTurnOn) { Text(stringResource(R.string.setup_turn_on)) }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = colors.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageAccessSheet(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.setup_usage_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.setup_usage_why), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FactColumn(R.string.setup_does, listOf(R.string.setup_usage_sees, R.string.setup_usage_for),
                    false, setupSuccess(), Modifier.weight(1f))
                FactColumn(R.string.setup_does_not, listOf(R.string.setup_no_content, R.string.setup_nothing_saved),
                    false, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_open_settings)) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_not_now)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupStepSheet(step: SetupStep, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val reduced = rememberReducedMotion()
    val title = when (step) {
        SetupStep.Accessibility -> R.string.setup_accessibility_title
        SetupStep.Background -> R.string.home_setup_allow_background
        SetupStep.Notifications -> R.string.setup_notifications_title
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(when (step) {
                SetupStep.Accessibility -> R.string.setup_step_one
                SetupStep.Background -> R.string.setup_step_two
                SetupStep.Notifications -> R.string.setup_optional_heading
            }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
            when (step) {
                SetupStep.Accessibility -> AccessibilityIllustration(reduced)
                SetupStep.Background -> BackgroundDialogIllustration(reduced)
                SetupStep.Notifications -> NotificationIllustration(reduced)
            }
            Text(stringResource(when (step) {
                SetupStep.Accessibility -> R.string.setup_accessibility_why
                SetupStep.Background -> R.string.setup_background_why
                SetupStep.Notifications -> R.string.setup_notifications_why
            }), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val can = when (step) {
                    SetupStep.Accessibility -> listOf(R.string.setup_reads_keys, R.string.setup_reads_code)
                    SetupStep.Background -> listOf(R.string.setup_keeps_open, R.string.setup_runs_key)
                    SetupStep.Notifications -> listOf(R.string.setup_needs_you, R.string.setup_shortcut_failed, R.string.setup_own_alerts)
                }
                val reassurance = when (step) {
                    SetupStep.Accessibility -> listOf(R.string.setup_no_screens, R.string.setup_no_data)
                    SetupStep.Background -> listOf(R.string.setup_no_drain, R.string.setup_idle)
                    SetupStep.Notifications -> listOf(R.string.setup_no_ads, R.string.setup_no_promotions, R.string.setup_no_surprises)
                }
                FactColumn(when (step) {
                    SetupStep.Accessibility -> R.string.setup_can
                    SetupStep.Background -> R.string.setup_does
                    SetupStep.Notifications -> R.string.setup_we_send
                }, can, false, setupSuccess(), Modifier.weight(1f), step == SetupStep.Accessibility)
                FactColumn(when (step) {
                    SetupStep.Accessibility -> R.string.setup_cannot
                    SetupStep.Background -> R.string.setup_does_not
                    SetupStep.Notifications -> R.string.setup_promise
                }, reassurance, step == SetupStep.Accessibility,
                    if (step == SetupStep.Accessibility) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f))
            }
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (step == SetupStep.Accessibility) R.string.setup_agree else title))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (step == SetupStep.Notifications) R.string.setup_skip else R.string.setup_not_now))
            }
        }
    }
}

@Composable
private fun FactColumn(@StringRes title: Int, facts: List<Int>, negative: Boolean, tint: Color, modifier: Modifier, pairing: Boolean = false) {
    Column(modifier.background(tint.copy(alpha = .09f), InnerShape).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), color = tint, fontWeight = FontWeight.Bold)
        facts.forEachIndexed { index, fact ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (negative) Icons.Default.Close else Icons.Default.Check, null, Modifier.size(16.dp), tint = tint)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(fact), style = MaterialTheme.typography.bodySmall)
                    if (pairing && index == 1) Text(stringResource(R.string.setup_advanced_only),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
