package com.example.ui.scripts

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.InnerShape
import com.example.ui.home.rememberReducedMotion
import com.example.ui.theme.setupSuccess
import com.example.ui.theme.setupWarning
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.delay
import java.util.Locale

// The phone's own English labels. The sheet names them as the system shows them in English, and the
// hint tells non-English users to switch for a minute (some languages hide the row while it is off).
private const val DEV_OPTIONS = "Developer options"
private const val ROW_NAME = "Disable system optimization"
private const val CAUTION = "Proceed with caution"
private const val NOT_NOW = "Not now"
private const val TURN_ON_ANYWAY = "Turn on anyway"
private const val STEP_MS = 3_000L

/**
 * Explains the one Developer options switch that OPPO, OnePlus and realme need before an app may
 * switch Bluetooth. KeySnap only reads the switch (`persist.sys.permission.enable`); the user flips
 * it, and the phone's own security warning is named, not played down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SystemOptimizationSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp)
                .testTag("system_optimization_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$DEV_OPTIONS › $ROW_NAME", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.sysopt_title), style = MaterialTheme.typography.headlineSmall)
            SystemOptimizationIllustration(reduced)
            Text(stringResource(R.string.sysopt_why), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val ink = MaterialTheme.colorScheme.onSurface
                FactBox(
                    stringResource(R.string.sysopt_switch_column), ink, MaterialTheme.colorScheme.surfaceContainerHigh, Modifier.weight(1f),
                    listOf(
                        Fact("✓", stringResource(R.string.sysopt_unblocks), setupSuccess()),
                        Fact("⚠", stringResource(R.string.sysopt_warns), setupWarning(), highlight = true),
                        Fact("↩", stringResource(R.string.sysopt_off_anytime), MaterialTheme.colorScheme.primary),
                    ),
                )
                val primary = MaterialTheme.colorScheme.primary
                FactBox(
                    "KeySnap", primary, primary.copy(alpha = .09f), Modifier.weight(1f),
                    listOf(
                        Fact("✓", stringResource(R.string.sysopt_only_reads), primary),
                        Fact("✓", stringResource(R.string.sysopt_no_permissions), primary),
                        Fact("✓", stringResource(R.string.sysopt_open_source), primary),
                    ),
                )
            }
            Button(onClick = { openDeveloperOptions(context); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sysopt_open))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_not_now)) }
            if (Locale.getDefault().language != "en") {
                Text(
                    "🌐 " + stringResource(R.string.sysopt_english_hint),
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Developer options, or About phone when they were never turned on (Developer options would show nothing). */
private fun openDeveloperOptions(context: Context) {
    val enabled = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    val action = if (enabled) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_DEVICE_INFO_SETTINGS
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private class Fact(val mark: String, val text: String, val tint: Color, val highlight: Boolean = false)

@Composable
private fun FactBox(title: String, titleTint: Color, ground: Color, modifier: Modifier, facts: List<Fact>) {
    Column(modifier.background(ground, InnerShape).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = titleTint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        facts.forEach { fact ->
            val rowModifier = if (fact.highlight) {
                Modifier.background(fact.tint.copy(alpha = .14f), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
            } else Modifier
            Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fact.mark, color = fact.tint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text(fact.text, color = if (fact.highlight) fact.tint else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Three steps in one loop (find, turn on, confirm the phone's warning); reduced motion shows them side by side. */
@Composable
private fun SystemOptimizationIllustration(reduced: Boolean) {
    val captions = listOf(
        stringResource(R.string.sysopt_step_find, ROW_NAME),
        stringResource(R.string.sysopt_step_turn_on),
        stringResource(R.string.sysopt_step_confirm, TURN_ON_ANYWAY),
    )
    val stage = Modifier.fillMaxWidth().height(164.dp).clip(InnerShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    if (reduced) {
        Row(stage.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..2).forEach { step ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().height(104.dp)) { StepFrame(step, switchOn = step >= 1) }
                    Text(captions[step], style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        return
    }
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(STEP_MS)
            step = (step + 1) % 3
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(stage.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedContent(step, Modifier.fillMaxWidth().height(112.dp), transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "sysoptStep") { current ->
                StepFrame(current, switchOn = current == 1, animateSwitch = current == 1)
            }
            AnimatedContent(step, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "sysoptCaption") { current ->
                Text(captions[current], style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..2).forEach { index ->
                val dot by animateColorAsState(
                    if (index == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "sysoptDot",
                )
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            }
        }
    }
}

/** One mini screen: the Developer options list (steps 0 and 1) or the phone's caution screen (step 2). */
@Composable
private fun StepFrame(step: Int, switchOn: Boolean, animateSwitch: Boolean = false) {
    val compact = 9.sp
    if (step == 2) {
        // The phone's own warning, drawn as it looks; "Turn on anyway" is not emphasised, so nothing nudges past it.
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color(0xFF111111)).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text("⚠", color = Color(0xFFFFB300), fontSize = 14.sp)
            Text(CAUTION, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth(.8f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1677FF)).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                Text(NOT_NOW, color = Color.White, fontSize = compact, fontWeight = FontWeight.SemiBold)
            }
            Text(TURN_ON_ANYWAY, color = Color(0xFF5AA2FF), fontSize = compact, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        return
    }
    val surface = MaterialTheme.colorScheme.surface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(DEV_OPTIONS, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        MiniRow("Disable child process restrictions", dim, on = false)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .1f)).padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ROW_NAME, Modifier.weight(1f), fontSize = compact, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MiniSwitch(on = switchOn, animate = animateSwitch)
        }
        MiniRow("Logging level", dim, on = null)
    }
}

@Composable
private fun MiniRow(label: String, ink: Color, on: Boolean?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 9.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (on != null) MiniSwitch(on, animate = false)
    }
}

@Composable
private fun MiniSwitch(on: Boolean, animate: Boolean) {
    // Shown off, then flips once the frame is in: the one motion that is the step itself.
    var flipped by remember { mutableIntStateOf(if (animate) 0 else 1) }
    LaunchedEffect(animate) {
        if (animate) {
            delay(700)
            flipped = 1
        }
    }
    val isOn = on && flipped == 1
    val track by animateColorAsState(if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "miniTrack")
    val knob by animateDpAsState(if (isOn) 10.dp else 2.dp, label = "miniKnob")
    Box(Modifier.width(22.dp).height(12.dp).clip(CircleShape).background(track)) {
        Box(Modifier.offset(x = knob, y = 2.dp).size(8.dp).clip(CircleShape).background(Color.White))
    }
}
