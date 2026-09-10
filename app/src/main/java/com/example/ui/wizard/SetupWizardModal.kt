package com.example.ui.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.window.DialogProperties
import com.example.data.DeveloperOptionsGuides
import com.example.service.adb.PairingStatus
import com.example.ui.components.ConsoleShape
import com.example.ui.home.loopPosition
import com.example.ui.home.ramp
import com.example.ui.home.rememberReducedMotion
import com.example.ui.components.InnerShape
import com.example.ui.theme.MonoBody
import com.example.ui.theme.MonoLabel
import com.example.viewmodel.MainViewModel
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlin.math.roundToInt

@Composable
fun SetupWizardModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentStep by viewModel.wizardStep.collectAsState()
    val isRunning by viewModel.isAdvancedModeRunning.collectAsState()
    var isDevSettingsOpened by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val pairingStatus by viewModel.pairingStatus.collectAsState()
    val isStep1Valid = currentStep != 1 || isDevSettingsOpened
    val lastKernelLine by viewModel.lastRawKernelLine.collectAsState()

    LaunchedEffect(pairingStatus, isRunning) {
        if ((pairingStatus == PairingStatus.PAIRED_SUCCESS || isRunning) && currentStep < 2) {
            viewModel.setWizardStep(2)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val colors = MaterialTheme.colorScheme
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(colors.background)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(InnerShape)
                                    .background(colors.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = colors.background,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column {
                                Text(stringResource(R.string.wizard_title), style = MaterialTheme.typography.titleSmall, color = colors.onBackground)
                                Text(stringResource(R.string.wizard_subtitle), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    WizardStepIndicator(currentStep = currentStep)
                }
            },
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        // A usePlatformDefaultWidth=false dialog is sized to the whole display but
                        // still starts below the status bar, so the Scaffold lays its bottom bar
                        // out that far past the screen; the bottom padding is what pulls Continue
                        // back up. 64dp only just cancelled the overflow and left the button on the
                        // gesture handle — this clears it. Insets read as zero inside the dialog,
                        // so navigationBarsPadding above cannot do the job here.
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                    shape = ConsoleShape,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline.copy(alpha = .4f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStep in 1..1) {
                            OutlinedButton(
                                onClick = { viewModel.setWizardStep(currentStep - 1) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurfaceVariant),
                                border = BorderStroke(1.5.dp, colors.outline.copy(alpha = .5f)),
                                shape = ConsoleShape,
                                modifier = Modifier.width(104.dp).height(52.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.common_back), style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        if (currentStep < 1) {
                            Button(
                                onClick = { viewModel.setWizardStep(currentStep + 1) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = ConsoleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("wizard_next_button"),
                                enabled = (currentStep == 0) || (currentStep == 1 && isStep1Valid)
                            ) {
                                Text(stringResource(R.string.wizard_continue), color = colors.background, style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.background, modifier = Modifier.size(15.dp))
                            }
                        } else if (currentStep == 1) {
                            Button(
                                onClick = { viewModel.setWizardStep(currentStep + 1) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    disabledContainerColor = colors.surfaceVariant,
                                ),
                                shape = ConsoleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("wizard_next_to_done_button"),
                                enabled = (pairingStatus == PairingStatus.PAIRED_SUCCESS || pairingStatus == PairingStatus.ADVANCED_MODE_RUNNING)
                            ) {
                                val onColor = if (pairingStatus == PairingStatus.PAIRED_SUCCESS || pairingStatus == PairingStatus.ADVANCED_MODE_RUNNING) colors.background else colors.onSurfaceVariant
                                Text(stringResource(R.string.wizard_finish_pairing), color = onColor, style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Check, null, tint = onColor, modifier = Modifier.size(15.dp))
                            }
                        } else if (currentStep == 2) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = ConsoleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = colors.background, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.onboarding_finish), color = colors.background, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (currentStep) {
                    0 -> WizardIntroStep(onGoToPairing = { viewModel.setWizardStep(1) })
                    1 -> WizardDeveloperOptionsStep(
                        pairingStatus = pairingStatus,
                        settingsOpened = isDevSettingsOpened,
                        onOpenSettings = { viewModel.openDeveloperSettings(context) },
                        onSettingsOpened = { isDevSettingsOpened = true }
                    )
                    2 -> WizardSuccessStep(
                        isRunning = isRunning,
                        pairingStatus = pairingStatus,
                        lastKernelLine = lastKernelLine,
                        onTestFlash = { viewModel.toggleFlashlight() }
                    )
                }
            }
        }
    }
}

@Composable
fun WizardStepIndicator(currentStep: Int) {
    val colors = MaterialTheme.colorScheme
    val steps = listOf(
        stringResource(R.string.wizard_step_intro),
        stringResource(R.string.wizard_step_pairing),
        stringResource(R.string.wizard_step_done),
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { index, title ->
            val isCompleted = currentStep > index
            val isActive = currentStep == index
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isCompleted || isActive) colors.primary else colors.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(Icons.Default.Check, null, tint = colors.background, modifier = Modifier.size(12.dp))
                    } else {
                        Text(
                            (index + 1).toString(),
                            color = if (isActive) colors.background else colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Text(
                    title,
                    color = if (isActive || isCompleted) colors.onBackground else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            if (index != steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(2.dp)
                        .clip(InnerShape)
                        .background(if (isCompleted) colors.primary else colors.outline.copy(alpha = .4f))
                )
            }
        }
    }
}

/** Bordered card matching [ConsoleShape] and the panel border used across the redesign. */
@Composable
private fun WizardCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = ConsoleShape,
        border = BorderStroke(1.dp, colors.outline.copy(alpha = .4f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onBackground)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun NumberBadge(number: Int) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = .14f)),
        contentAlignment = Alignment.Center
    ) {
        Text(number.toString(), color = colors.primary, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The first thing anyone meets after tapping Advanced Mode, so it is the step most likely to be
 * skipped: it used to run to two full cards of prose before Continue came into view, and the
 * Developer Options walkthrough on it described a screen the user had not been asked to open yet.
 *
 * Every claim from that version survives — the three capabilities and the three limits — as lines
 * short enough to be read at a glance, marked "can"/"never" per line instead of under two headings.
 * The walkthrough moved to [WizardDeveloperOptionsStep], where it is next to the button that opens
 * that screen, and a press producing a kernel line says what the paragraph was trying to.
 */
@Composable
fun WizardIntroStep(onGoToPairing: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    WizardCard(icon = Icons.Default.Terminal, title = stringResource(R.string.wizard_what_is_expert)) {
        WizardKernelIllustration(reduced)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.wizard_expert_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.outline.copy(alpha = .4f))
        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            val can = stringResource(R.string.wizard_can)
            val never = stringResource(R.string.wizard_never)
            stringArrayResource(R.array.wizard_can_do_lines).forEach {
                ScopeLine(mark = can, markColor = colors.primary, line = it)
            }
            stringArrayResource(R.array.wizard_never_does_lines).forEach {
                ScopeLine(mark = never, markColor = colors.error, line = it)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.wizard_expert_off_note),
            style = MonoLabel,
            color = colors.onSurfaceVariant,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = ConsoleShape,
        border = BorderStroke(1.dp, colors.outline.copy(alpha = .4f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGoToPairing)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.DeveloperMode, null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.wizard_no_dev_options),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground,
                )
                Text(
                    stringResource(R.string.wizard_no_dev_options_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(15.dp))
        }
    }
}

/** One capability or limit: a mono "can"/"never" mark, then the line it applies to. */
@Composable
private fun ScopeLine(mark: String, markColor: Color, line: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        // widthIn rather than width: the mark is a word, and a longer translation must push the
        // sentence across rather than be clipped.
        Text(mark, style = MonoLabel, color = markColor, modifier = Modifier.widthIn(min = 42.dp).padding(top = 2.dp))
        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A hardware key press turning into a kernel line and firing a shortcut — what Advanced Mode is,
 * in the only terms that matter to the person setting it up. Built like the Home tutorial's
 * miniatures: one loop, every animated value read inside a draw or layer lambda.
 */
@Composable
private fun WizardKernelIllustration(reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    val t = loopPosition(reduced)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(InnerShape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outline.copy(alpha = .55f), InnerShape)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 84.dp)
                .drawBehind {
                    val lit = ramp(t.value, .30f, .38f) - ramp(t.value, .76f, .92f)
                    val radius = CornerRadius(10.dp.toPx())
                    drawRoundRect(colors.primary.copy(alpha = .04f + .14f * lit), cornerRadius = radius)
                    drawRoundRect(
                        color = lerp(colors.outline, colors.primary, lit),
                        cornerRadius = radius,
                        style = Stroke(1.6.dp.toPx()),
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp)
                    .offset {
                        val pressed = ramp(t.value, .24f, .32f) - ramp(t.value, .46f, .58f)
                        IntOffset((4.dp.toPx() - 3.dp.toPx() * pressed).roundToInt(), 0)
                    }
                    .size(width = 4.dp, height = 20.dp)
                    .drawBehind {
                        val pressed = ramp(t.value, .24f, .32f) - ramp(t.value, .46f, .58f)
                        drawRoundRect(
                            lerp(colors.onSurfaceVariant, colors.primary, pressed),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.wizard_illo_kernel_line),
                modifier = Modifier.graphicsLayer {
                    alpha = ramp(t.value, .30f, .38f) * (1f - ramp(t.value, .84f, .94f))
                },
                style = MonoLabel,
                color = colors.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.wizard_illo_action),
                modifier = Modifier.graphicsLayer {
                    alpha = ramp(t.value, .44f, .52f) * (1f - ramp(t.value, .84f, .94f))
                },
                style = MonoLabel,
                color = colors.primary,
            )
        }
    }
}

@Composable
fun DeveloperOptionsGuideCard() {
    val colors = MaterialTheme.colorScheme
    var selectedGuide by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(DeveloperOptionsGuides.detectForDevice())
    }
    val chipListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(Unit) {
        val index = DeveloperOptionsGuides.all.indexOfFirst { it.id == selectedGuide.id }
        if (index > 0) {
            chipListState.scrollToItem(index)
            val layoutInfo = chipListState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            if (itemInfo != null) {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val itemCenter = itemInfo.offset + itemInfo.size / 2
                chipListState.scrollBy((itemCenter - viewportCenter).toFloat())
            }
        }
    }

    WizardCard(icon = Icons.Default.DeveloperMode, title = stringResource(R.string.wizard_how_to_dev_options)) {
        LazyRow(state = chipListState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DeveloperOptionsGuides.all) { guide ->
                val isSelected = guide.id == selectedGuide.id
                Box(
                    modifier = Modifier
                        .clip(InnerShape)
                        .background(if (isSelected) colors.primary else colors.surfaceVariant)
                        .clickable { selectedGuide = guide }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(guide.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) colors.background else colors.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stringArrayResource(selectedGuide.stepsRes).forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    NumberBadge(index + 1)
                    Text(
                        step,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun pairingStatusLabel(status: PairingStatus): String = when (status) {
    PairingStatus.IDLE -> stringResource(R.string.pairing_idle)
    PairingStatus.SEARCHING_MDNS_AND_SCREEN -> stringResource(R.string.pairing_searching)
    PairingStatus.CREDENTIALS_CAPTURED -> stringResource(R.string.pairing_captured)
    PairingStatus.PAIRING_IN_PROGRESS -> stringResource(R.string.pairing_in_progress)
    PairingStatus.PAIRED_SUCCESS -> stringResource(R.string.pairing_paired)
    PairingStatus.CONNECTING_STREAM -> stringResource(R.string.pairing_connecting)
    PairingStatus.ADVANCED_MODE_RUNNING -> stringResource(R.string.pairing_connected)
    PairingStatus.ERROR -> stringResource(R.string.pairing_error)
}

/**
 * The mDNS search starts with the step, not with the user, so "Searching…" was pulsing at someone
 * who had not tapped anything yet. Until [started] the row says what to tap instead.
 */
@Composable
private fun PairingStatusRow(status: PairingStatus, started: Boolean) {
    val colors = MaterialTheme.colorScheme
    val waitingOnUser = !started &&
        (status == PairingStatus.IDLE || status == PairingStatus.SEARCHING_MDNS_AND_SCREEN)
    val dotColor = when {
        waitingOnUser -> colors.onSurfaceVariant
        status == PairingStatus.ERROR -> colors.error
        status == PairingStatus.IDLE -> colors.onSurfaceVariant
        else -> colors.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ConsoleShape)
            .background(colors.surface)
            .then(Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Text(
            if (waitingOnUser) stringResource(R.string.pairing_not_started) else pairingStatusLabel(status),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
fun WizardDeveloperOptionsStep(
    pairingStatus: PairingStatus,
    settingsOpened: Boolean,
    onOpenSettings: () -> Unit,
    onSettingsOpened: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            onOpenSettings()
            onSettingsOpened()
        }
    )

    WizardCard(icon = Icons.Default.DeveloperMode, title = stringResource(R.string.wizard_auto_pairing)) {
        Text(
            text = stringResource(R.string.wizard_auto_pairing_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val steps = stringArrayResource(R.array.wizard_pairing_steps)
            steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    NumberBadge(index + 1)
                    Text(
                        step,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        onOpenSettings()
                        onSettingsOpened()
                    } else {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    onOpenSettings()
                    onSettingsOpened()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("open_developer_options_button"),
            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant),
            shape = ConsoleShape
        ) {
            Icon(Icons.Default.OpenInNew, null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.wizard_open_settings), color = colors.primary, style = MaterialTheme.typography.labelLarge)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    PairingStatusRow(pairingStatus, started = settingsOpened)

    Spacer(modifier = Modifier.height(16.dp))

    // Moved here from the intro: it is a walkthrough of the screen the button above opens, so this
    // is the first point at which it is worth reading.
    DeveloperOptionsGuideCard()
}

/** How long the last step waits for the bridge before it calls the attempt failed. */
private const val CONNECT_GRACE_MS = 30_000L

@Composable
fun WizardSuccessStep(
    isRunning: Boolean,
    pairingStatus: PairingStatus,
    lastKernelLine: String?,
    onTestFlash: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    // Pairing succeeds seconds before the bridge answers, so this step used to open on the red
    // error card and only turn green later. Wait for a definitive answer instead: a running
    // bridge, a reported error, or the grace period running out. A late success still wins.
    var graceExpired by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(isRunning, pairingStatus) {
        graceExpired = false
        if (!isRunning) {
            kotlinx.coroutines.delay(CONNECT_GRACE_MS)
            graceExpired = true
        }
    }
    val failed = !isRunning && (pairingStatus == PairingStatus.ERROR || graceExpired)
    val waiting = !isRunning && !failed

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = ConsoleShape,
        border = BorderStroke(1.dp, colors.outline.copy(alpha = .4f)),
    ) {
        Column(
            modifier = Modifier.padding(22.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val statusColor = when {
                isRunning -> colors.primary
                failed -> colors.error
                else -> colors.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                if (waiting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        color = colors.primary,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(
                    when {
                        isRunning -> R.string.wizard_expert_running
                        waiting -> R.string.wizard_connecting
                        else -> R.string.wizard_connection_error
                    }
                ),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(
                    when {
                        isRunning -> R.string.wizard_expert_running_body
                        waiting -> R.string.wizard_connecting_body
                        else -> R.string.wizard_connection_error_body
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (isRunning) {
                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ConsoleShape)
                        .background(colors.background)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(colors.primary))
                        Text(stringResource(R.string.wizard_kernel_stream), style = MonoLabel, color = colors.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = lastKernelLine ?: stringResource(R.string.wizard_kernel_placeholder),
                        style = MonoBody,
                        color = colors.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onTestFlash,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = .14f)),
                    shape = ConsoleShape
                ) {
                    Icon(Icons.Default.FlashOn, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.wizard_test_flash), color = colors.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
