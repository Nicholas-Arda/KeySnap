package com.example.ui.wizard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.DeveloperOptionsGuides
import com.example.service.adb.PairingStatus
import com.example.ui.components.ConsoleShape
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.components.PageGroundLayer
import com.example.ui.components.PointBox
import com.example.ui.components.PointLine
import com.example.ui.components.glassCard
import com.example.ui.home.loopPosition
import com.example.ui.home.ramp
import com.example.ui.home.rememberReducedMotion
import com.example.ui.scripts.SentenceSection
import com.example.ui.theme.LocalGlass
import com.example.ui.theme.MonoBody
import com.example.ui.theme.MonoLabel
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.MainViewModel
import io.github.nicholasarda.keysnap.R
import kotlin.math.roundToInt

/**
 * Advanced Mode setup as one page read like the shortcut editor's sentence: what it is, pairing,
 * done. The step in hand is open, a finished one folds to its line, the next waits below. Every
 * explanation is the wording the paged wizard used; only the layout changed (2026-10-29).
 */
@Composable
fun SetupWizardModal(
    viewModel: MainViewModel,
    themeMode: ThemeMode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val step by viewModel.wizardStep.collectAsState()
    val isRunning by viewModel.isAdvancedModeRunning.collectAsState()
    val pairingStatus by viewModel.pairingStatus.collectAsState()
    val lastKernelLine by viewModel.lastRawKernelLine.collectAsState()
    var settingsOpened by remember { mutableStateOf(false) }
    var introReopened by remember { mutableStateOf(false) }

    LaunchedEffect(pairingStatus, isRunning) {
        if ((pairingStatus == PairingStatus.PAIRED_SUCCESS || isRunning) && step < 2) {
            viewModel.setWizardStep(2)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val colors = MaterialTheme.colorScheme
        // Insets read as zero inside a dialog, and where its window lands differs by device: it
        // started below the status bar and ran off the bottom. So the window is let out to the
        // whole display, and the padding is whatever of the activity's bars this box actually
        // overlaps, measured from where it sits on screen.
        val view = LocalView.current
        // Re-applied on every composition: the dialog resets its own window parameters, and when
        // opened from Settings it kept stopping short of the camera cutout, so the page behind
        // showed through a strip at the top.
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                setDimAmount(0f)
                val params = attributes
                if (params.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS ||
                    params.fitInsetsTypes != 0 || params.fitInsetsSides != 0
                ) {
                    attributes = params.apply {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        fitInsetsTypes = 0
                        fitInsetsSides = 0
                    }
                }
                // Now over the status bar, this window decides its icon colour: keep the app's.
                context.findActivity()?.window?.let { activityWindow ->
                    val own = WindowCompat.getInsetsController(activityWindow, activityWindow.decorView)
                    WindowCompat.getInsetsController(this, decorView).apply {
                        isAppearanceLightStatusBars = own.isAppearanceLightStatusBars
                        isAppearanceLightNavigationBars = own.isAppearanceLightNavigationBars
                    }
                }
            }
        }
        val bars = WindowInsets.systemBars.asPaddingValues()
        val topBar = bars.calculateTopPadding()
        val bottomBar = bars.calculateBottomPadding()
        Box(Modifier.fillMaxSize()) {
            PageGroundLayer(themeMode, Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = topBar + 12.dp, bottom = bottomBar + 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WizardHeader(onDismiss)
                Box(Modifier.fillMaxWidth().glassCard(LocalGlass.current).padding(10.dp)) {
                    WizardKernelIllustration(rememberReducedMotion())
                }
                Column(Modifier.padding(horizontal = 4.dp)) {
                    val introOpen = step == 0 || introReopened
                    SentenceSection(
                        title = stringResource(R.string.wizard_what_is_expert),
                        titleStyle = WizardTitle,
                        titleColor = colors.onSurface,
                        line = colors.primary.copy(alpha = .45f),
                        lineWidth = 2.dp,
                        tint = colors.primary,
                        modifier = if (step > 0) {
                            Modifier.clickable(role = Role.Button) { introReopened = !introReopened }
                        } else Modifier,
                    ) {
                        if (step > 0) {
                            Icon(if (introOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = colors.onSurfaceVariant)
                        }
                        Folding(introOpen) { IntroContent(onGoToPairing = { viewModel.setWizardStep(1) }) }
                    }
                    SentenceSection(
                        title = stringResource(R.string.wizard_auto_pairing),
                        titleStyle = WizardTitle,
                        titleColor = colors.onSurface,
                        line = colors.primary.copy(alpha = .45f),
                        lineWidth = 2.dp,
                        tint = if (step >= 1) colors.primary else colors.primary.copy(alpha = .35f),
                    ) {
                        if (step >= 2) {
                            Text(stringResource(R.string.pairing_connected), style = MaterialTheme.typography.bodySmall, color = colors.primary)
                        }
                        Folding(step == 1) {
                            PairingContent(
                                pairingStatus = pairingStatus,
                                settingsOpened = settingsOpened,
                                onOpenSettings = { viewModel.openDeveloperSettings(context) },
                                onSettingsOpened = { settingsOpened = true },
                                onSubmitCode = viewModel::submitPairingCode,
                            )
                        }
                    }
                    DoneSection(
                        reached = step == 2,
                        isRunning = isRunning,
                        pairingStatus = pairingStatus,
                        lastKernelLine = lastKernelLine,
                    )
                }
            }
            WizardCapsule(
                step = step,
                pairingStatus = pairingStatus,
                settingsOpened = settingsOpened,
                onContinue = { viewModel.setWizardStep(1) },
                onFinish = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = bottomBar + 16.dp),
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** A section's body, opened and closed with motion rather than appearing at once. */
@Composable
private fun Folding(open: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(tween(280)) + fadeIn(tween(220, delayMillis = 60)),
        exit = shrinkVertically(tween(240)) + fadeOut(tween(140)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun WizardHeader(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
            Text(
                stringResource(R.string.wizard_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
            Text(stringResource(R.string.wizard_subtitle), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(CircleShape)
                .background(glass.cardTop)
                .border(1.dp, glass.cardRim, CircleShape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.common_close), onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, stringResource(R.string.common_close), Modifier.size(20.dp), tint = colors.onSurface)
        }
    }
}

/**
 * Every claim of the first version survives: three capabilities and three limits, read at a glance
 * as two columns instead of a "can"/"never" mark on each line.
 */
@Composable
private fun IntroContent(onGoToPairing: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    PointBox(colors.primary) {
        PointLine(Icons.Filled.Bolt, colors.primary, stringResource(R.string.wizard_expert_explainer))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScopeColumn(
            mark = stringResource(R.string.wizard_can),
            lines = stringArrayResource(R.array.wizard_can_do_lines),
            tint = colors.primary,
            sign = Icons.Filled.Check,
            modifier = Modifier.weight(1f),
        )
        ScopeColumn(
            mark = stringResource(R.string.wizard_never),
            lines = stringArrayResource(R.array.wizard_never_does_lines),
            tint = colors.error,
            sign = Icons.Filled.Close,
            modifier = Modifier.weight(1f),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.PowerSettingsNew, null, Modifier.size(16.dp), tint = colors.tertiary)
        Text(stringResource(R.string.wizard_expert_off_note), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
    }
    GlassRow(
        icon = Icons.Filled.DeveloperMode,
        title = stringResource(R.string.wizard_no_dev_options),
        subtitle = stringResource(R.string.wizard_no_dev_options_sub),
        trailing = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant) },
        onClick = onGoToPairing,
    )
}

@Composable
private fun ScopeColumn(
    mark: String,
    lines: Array<String>,
    tint: Color,
    sign: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = .09f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(mark.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tint)
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(sign, null, Modifier.padding(top = 2.dp).size(14.dp), tint = tint)
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** A tappable glass row: an icon tile, a title over a subtitle, and whatever sits at the end. */
@Composable
private fun GlassRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .glassCard(LocalGlass.current)
            .clip(ConsoleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(colors.primary.copy(alpha = .12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(20.dp), tint = colors.primary) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        trailing()
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
            .background(colors.surfaceVariant.copy(alpha = .55f))
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
private fun PairingContent(
    pairingStatus: PairingStatus,
    settingsOpened: Boolean,
    onOpenSettings: () -> Unit,
    onSettingsOpened: () -> Unit,
    onSubmitCode: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val open = {
        onOpenSettings()
        onSettingsOpened()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { open() }

    val (automatic, fallback) = stringArrayResource(R.array.wizard_auto_pairing_points)
    PointBox(colors.primary) {
        PointLine(Icons.Filled.AutoAwesome, colors.primary, automatic)
        PointLine(Icons.Filled.Notifications, colors.tertiary, fallback)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stringArrayResource(R.array.wizard_pairing_steps).forEachIndexed { index, step ->
            NumberedLine(index + 1, step)
        }
    }
    Button(
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                open()
            }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("open_developer_options_button"),
        shape = RoundedCornerShape(50),
    ) {
        Icon(Icons.Filled.OpenInNew, null, Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.wizard_open_settings), style = MaterialTheme.typography.labelLarge)
    }

    // No code within a few seconds of opening Settings: the assisted tap found nothing to tap, or
    // Android needs the user first. The manual path works without the notification too.
    var showManual by remember { mutableStateOf(false) }
    LaunchedEffect(settingsOpened, pairingStatus) {
        if (settingsOpened && pairingStatus == PairingStatus.SEARCHING_MDNS_AND_SCREEN) {
            kotlinx.coroutines.delay(MANUAL_FALLBACK_MS)
            showManual = true
        } else if (pairingStatus == PairingStatus.ERROR) {
            showManual = true
        }
    }
    Folding(showManual && pairingStatus != PairingStatus.PAIRED_SUCCESS && pairingStatus != PairingStatus.ADVANCED_MODE_RUNNING) {
        ManualPairingCard(onSubmitCode)
    }
    DeveloperOptionsGuideCard()
}

@Composable
private fun NumberedLine(number: Int, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(colors.primary),
            contentAlignment = Alignment.Center,
        ) { Text(number.toString(), color = colors.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        Text(text, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

/**
 * The phone maker's walkthrough, folded: most people already have Developer Options. It opens on
 * the maker this phone reports.
 */
@Composable
private fun DeveloperOptionsGuideCard() {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    var selectedGuide by remember { mutableStateOf(DeveloperOptionsGuides.detectForDevice()) }
    val chipListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
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

    Column(Modifier.fillMaxWidth().glassCard(LocalGlass.current).animateContentSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(ConsoleShape)
                .clickable(role = Role.Button) { open = !open }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(colors.primary.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.DeveloperMode, null, Modifier.size(20.dp), tint = colors.primary) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.wizard_how_to_dev_options), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                Text(stringResource(selectedGuide.labelRes), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = colors.onSurfaceVariant)
        }
        if (open) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(state = chipListState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DeveloperOptionsGuides.all) { guide ->
                        val isSelected = guide.id == selectedGuide.id
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) colors.primary else colors.surfaceVariant)
                                .clickable { selectedGuide = guide }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                stringResource(guide.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                stringArrayResource(selectedGuide.stepsRes).forEachIndexed { index, step -> NumberedLine(index + 1, step) }
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

/** How long after opening Settings the pairing step offers the manual path. */
internal const val MANUAL_FALLBACK_MS = 10_000L

@Composable
private fun ManualPairingCard(onSubmitCode: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Not saveable: a one-time pairing code has no business in the saved-instance bundle.
    var code by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().glassCard(LocalGlass.current).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Terminal, null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.wizard_manual_title), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        }
        Text(stringResource(R.string.wizard_manual_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        androidx.compose.material3.OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(6) },
            label = { Text(stringResource(R.string.wizard_manual_code_label)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
            ),
            modifier = Modifier.fillMaxWidth().testTag("manual_pairing_code"),
        )
        Button(
            onClick = { onSubmitCode(code) },
            enabled = code.length == 6,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("manual_pairing_submit"),
            shape = RoundedCornerShape(50),
        ) {
            Text(stringResource(R.string.wizard_manual_pair), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** How long the last step waits for the bridge before it calls the attempt failed. */
private const val CONNECT_GRACE_MS = 30_000L

/**
 * The sentence's last clause. Pairing succeeds seconds before the bridge answers, so it waits for a
 * definitive answer — a running bridge, a reported error, or the grace period running out — rather
 * than opening on an error and turning green later. A late success still wins.
 */
@Composable
private fun DoneSection(
    reached: Boolean,
    isRunning: Boolean,
    pairingStatus: PairingStatus,
    lastKernelLine: String?,
) {
    val colors = MaterialTheme.colorScheme
    var graceExpired by remember { mutableStateOf(false) }
    LaunchedEffect(isRunning, pairingStatus, reached) {
        graceExpired = false
        if (reached && !isRunning) {
            kotlinx.coroutines.delay(CONNECT_GRACE_MS)
            graceExpired = true
        }
    }
    val failed = !isRunning && (pairingStatus == PairingStatus.ERROR || graceExpired)
    val waiting = !isRunning && !failed
    val title = when {
        !reached -> R.string.wizard_step_done
        isRunning -> R.string.wizard_expert_running
        waiting -> R.string.wizard_connecting
        else -> R.string.wizard_connection_error
    }
    SentenceSection(
        title = stringResource(title),
        titleStyle = WizardTitle,
        titleColor = colors.onSurface,
        line = colors.primary.copy(alpha = .45f),
        lineWidth = 2.dp,
        tint = when {
            !reached -> colors.primary.copy(alpha = .35f)
            failed -> colors.error
            else -> colors.primary
        },
        last = true,
    ) {
        Folding(reached) {
            AnimatedContent(
                targetState = Triple(isRunning, waiting, failed),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                label = "wizardDone",
            ) { (running, wait, _) ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            wait -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            running -> RunningTick()
                        }
                        Text(
                            stringResource(
                                when {
                                    running -> R.string.wizard_expert_running_body
                                    wait -> R.string.wizard_connecting_body
                                    else -> R.string.wizard_connection_error_body
                                },
                            ),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (running || wait) colors.onSurface else colors.error,
                        )
                    }
                    if (running) KernelStream(lastKernelLine)
                }
            }
        }
    }
}

@Composable
private fun RunningTick() {
    val colors = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(shown, enter = scaleIn(spring(dampingRatio = .55f, stiffness = Spring.StiffnessMediumLow)) + fadeIn()) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = colors.onPrimary)
            }
        }
    }
}

/** The last few kernel lines, newest at the bottom, so a key pressed now is seen arriving. */
@Composable
private fun KernelStream(lastKernelLine: String?) {
    val colors = MaterialTheme.colorScheme
    val lines = remember { mutableStateListOf<String>() }
    LaunchedEffect(lastKernelLine) {
        val line = lastKernelLine ?: return@LaunchedEffect
        lines.add(line)
        while (lines.size > 5) lines.removeAt(0)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(colors.inverseSurface)
            .padding(14.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(colors.inversePrimary))
            Text(stringResource(R.string.wizard_kernel_stream), style = MonoLabel, color = colors.inversePrimary)
        }
        if (lines.isEmpty()) {
            Text(stringResource(R.string.wizard_kernel_placeholder), style = MonoBody, color = colors.inverseOnSurface.copy(alpha = .75f))
        }
        lines.forEachIndexed { index, line ->
            Text(
                line,
                style = MonoBody,
                color = if (index == lines.lastIndex) colors.inversePrimary else colors.inverseOnSurface.copy(alpha = .75f),
                maxLines = 2,
            )
        }
    }
}

/**
 * The floating capsule: Continue on the first step, the live pairing status on the second (the
 * page moves on by itself once paired), Finish setup on the last.
 */
@Composable
private fun WizardCapsule(
    step: Int,
    pairingStatus: PairingStatus,
    settingsOpened: Boolean,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = glass.barShadow, spotColor = glass.barShadow)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(glass.sheetTop, glass.sheetMid)))
            .border(1.2.dp, glass.barRim, shape)
            .padding(6.dp),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "wizardCapsule",
        ) { current ->
            when (current) {
                0 -> CapsuleButton(stringResource(R.string.wizard_continue), "wizard_next_button", onContinue)
                1 -> PairingStatusLine(pairingStatus, settingsOpened)
                else -> CapsuleButton(stringResource(R.string.onboarding_finish), "wizard_finish_button", onFinish)
            }
        }
    }
}

@Composable
private fun CapsuleButton(label: String, tag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag),
        shape = RoundedCornerShape(50),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/**
 * The mDNS search starts with the step, not with the user, so "Searching…" was pulsing at someone
 * who had not tapped anything yet. Until [started] the line says what to tap instead.
 */
@Composable
private fun PairingStatusLine(status: PairingStatus, started: Boolean) {
    val colors = MaterialTheme.colorScheme
    val waitingOnUser = !started &&
        (status == PairingStatus.IDLE || status == PairingStatus.SEARCHING_MDNS_AND_SCREEN)
    val dotColor = when {
        waitingOnUser || status == PairingStatus.IDLE -> colors.onSurfaceVariant
        status == PairingStatus.ERROR -> colors.error
        else -> colors.primary
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        AnimatedContent(
            targetState = if (waitingOnUser) stringResource(R.string.pairing_not_started) else pairingStatusLabel(status),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "pairingStatus",
        ) { text ->
            Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
        }
    }
}

/** Section titles: dark and a size up from the editor's, which sit above much larger controls. */
private val WizardTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleMedium
