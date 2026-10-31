package com.example.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.billing.ProPrice
import com.example.billing.ProSale
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.zIndex
import com.example.capsule.CapsuleHooks
import com.example.data.EntitlementManager
import com.example.data.RatingPromptChoice
import com.example.data.ScriptActionCatalog
import com.example.data.ShortcutScript
import com.example.data.SuggestedShortcut
import com.example.ui.components.ConsolePanel
import com.example.ui.components.ConsoleShape
import com.example.ui.components.GroundedPage
import com.example.ui.components.IconTile
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.scripts.triggerSummary
import com.example.ui.theme.ThemeMode
import com.example.data.AccessibilityStatus
import io.github.nicholasarda.keysnap.R

@Composable
fun HomeScreen(
    isMappingEnabled: Boolean,
    isServiceActive: Boolean,
    isAdvancedModeRunning: Boolean,
    triggerCount: Int,
    lastSource: String,
    isPro: Boolean,
    proPrice: ProPrice?,
    activeScriptCount: Int,
    activeScriptLimit: Int,
    themeMode: ThemeMode,
    scripts: List<ShortcutScript>,
    onOpenScript: (String) -> Unit,
    onToggleMapping: () -> Unit,
    onOpenWizard: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onPickSuggestion: (SuggestedShortcut) -> Unit,
    onOpenProScreen: () -> Unit,
    onOpenWhatsNew: () -> Unit = {},
    onWhatsNewLogoBounds: (Rect) -> Unit = {},
    whatsNewLogoAway: Boolean = false,
    announceWhatsNew: Boolean = false,
    showWhatsNewHint: Boolean = false,
    onWhatsNewHintFinished: () -> Unit = {},
    nudgeDismissed: Boolean = true,
    onDismissNudge: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    scrollToProCard: Boolean = false,
    onScrolledToProCard: () -> Unit = {},
    accessibilityStatus: AccessibilityStatus = if (isServiceActive) AccessibilityStatus.ACTIVE else AccessibilityStatus.DISABLED,
    showRatingPrompt: Boolean = false,
    onRatingChoice: (RatingPromptChoice) -> Unit = {},
    showColorOsRecentsTip: Boolean = false,
    onDismissColorOsRecentsTip: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val permissionGaps = rememberPermissionGaps()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("keysnap_setup", android.content.Context.MODE_PRIVATE) }
    var notificationsDismissed by remember { mutableStateOf(prefs.getBoolean("notifications_dismissed", false)) }
    val setup = RequiredSetupState(isServiceActive, permissionGaps.battery, permissionGaps.notifications, notificationsDismissed)
    var sheet by rememberSaveable { mutableStateOf<SetupStep?>(null) }
    var showUsageSheet by rememberSaveable { mutableStateOf(false) }
    val requestNotifications = rememberNotificationPermissionRequest()
    val dismissNotifications = {
        notificationsDismissed = true
        prefs.edit().putBoolean("notifications_dismissed", true).apply()
    }
    val openNext: () -> Unit = {
        if (setup.nextStep == SetupStep.Accessibility) onOpenAccessibility() else sheet = setup.nextStep
    }
    val showNudge = setup.nextStep == null && scripts.isEmpty() && !nudgeDismissed
    if (sheet != null) {
        val current = sheet!!
        SetupStepSheet(current, onConfirm = {
            sheet = null
            when (current) {
                SetupStep.Accessibility -> onOpenAccessibility()
                SetupStep.Background -> requestIgnoreBatteryOptimizations(context)
                SetupStep.Notifications -> requestNotifications()
            }
        }, onDismiss = {
            if (current == SetupStep.Notifications) dismissNotifications()
            sheet = null
        })
    }
    if (showUsageSheet) {
        UsageAccessSheet(onOpenSettings = {
            showUsageSheet = false
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }, onDismiss = { showUsageSheet = false })
    }
    LaunchedEffect(scrollToProCard) {
        if (scrollToProCard && !isPro) {
            // The Pro card is the last thing on the page.
            scrollState.animateScrollTo(scrollState.maxValue)
            onScrolledToProCard()
        }
    }
    GroundedPage(themeMode, scrollState) {
        ConsoleHeader(
            isMappingEnabled = isMappingEnabled,
            themeMode = themeMode,
            onToggleMapping = if (setup.nextStep != null) openNext else onToggleMapping,
            setupRequired = setup.pill(isMappingEnabled) == SetupPill.Setup,
            onOpenWhatsNew = onOpenWhatsNew,
            announceWhatsNew = announceWhatsNew,
            showWhatsNewHint = showWhatsNewHint,
            onWhatsNewHintFinished = onWhatsNewHintFinished,
            onLogoBounds = onWhatsNewLogoBounds,
            logoAway = whatsNewLogoAway,
        )
        if (!isPro && proPrice?.sale != null) {
            HomeSaleStrip(proPrice) {
                scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
            }
        }
        if (setup.nextStep != null) {
            SetupCard(permissionGaps, isServiceActive, openNext)
        }
        AnimatedVisibility(setup.showNotifications, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            NotificationsCard(onTurnOn = { sheet = SetupStep.Notifications }, onClose = { dismissNotifications() })
        }
        AnimatedVisibility(!permissionGaps.usageAccess && com.example.service.needsUsageAccess(scripts),
            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            UsageAccessBanner { showUsageSheet = true }
        }
        LiveStatusPanel(
            isServiceActive = isServiceActive,
            isAdvancedActive = isAdvancedModeRunning,
            triggerCount = triggerCount,
            lastSource = lastSource,
            onOpenWizard = onOpenWizard,
            onOpenAccessibility = onOpenAccessibility,
            accessibilityStatus = accessibilityStatus,
            showColorOsRecentsTip = showColorOsRecentsTip,
            onDismissColorOsRecentsTip = onDismissColorOsRecentsTip,
        )
        if (scripts.isNotEmpty()) YourShortcutsPanel(scripts, onOpenScript)
        if (showRatingPrompt) RatingPromptCard(onRatingChoice)
        if (showNudge) FirstShortcutNudge(onDismiss = onDismissNudge)
        SuggestedShortcutsPanel(isPro = isPro, scripts = scripts, onPick = onPickSuggestion)
        if (!isPro) {
            ProUpsellCard(
                activeCount = activeScriptCount,
                limit = activeScriptLimit,
                proPrice = proPrice?.price,
                sale = proPrice?.sale,
                onOpenProScreen = onOpenProScreen,
            )
        }
    }
}

@Composable
private fun HomeSaleStrip(proPrice: ProPrice, onClick: () -> Unit) {
    val sale = proPrice.sale ?: return
    val colors = MaterialTheme.colorScheme
    val now = rememberSaleNow(sale.endsAtMillis)
    val parts = sale.endsAtMillis?.let { saleTimeLeft(it, now) ?: return }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clip(ConsoleShape)
            .background(colors.primary)
            .testTag("home_sale_strip")
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sale.percentOff?.let {
            Text(
                ("−" + NumberFormat.getPercentInstance().format(it / 100.0)).unbroken(),
                Modifier.clip(InnerShape).background(colors.onPrimary.copy(alpha = .18f)).padding(6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onPrimary,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.home_sale_strip_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimary,
                maxLines = 2,
            )
            if (parts != null) {
                Text(
                    stringResource(R.string.pro_sale_ends_in, formatTimeLeft(parts)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                sale.fullPrice.unbroken(),
                style = MaterialTheme.typography.labelSmall,
                textDecoration = TextDecoration.LineThrough,
                color = colors.onPrimary.copy(alpha = .7f),
            )
            Text(
                proPrice.price.unbroken(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimary,
            )
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = colors.onPrimary)
    }
}

/**
 * Explains the free limit beside its usage count and opens the existing Pro purchase dialog.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ProUpsellCard(
    activeCount: Int,
    limit: Int,
    proPrice: String?,
    onOpenProScreen: () -> Unit,
    sale: ProSale? = null,
) {
    val colors = MaterialTheme.colorScheme
    val atCap = activeCount >= limit
    val now = rememberSaleNow(sale?.endsAtMillis)
    val liveSale = sale?.takeIf { it.endsAtMillis == null || saleTimeLeft(it.endsAtMillis, now) != null }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(ConsoleShape)
            .background(colors.primary.copy(alpha = .08f))
            .border(1.dp, colors.primary.copy(alpha = .4f), ConsoleShape)
            .testTag("pro_upsell_card")
            .padding(16.dp),
    ) {
        val stacked = maxWidth / LocalDensity.current.fontScale < 240.dp
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!stacked) {
                    Box(
                        Modifier.size(38.dp).clip(InnerShape).background(colors.primary.copy(alpha = .12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, null, tint = colors.primary, modifier = Modifier.size(19.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.app_name_pro),
                        style = MaterialTheme.typography.titleMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        if (atCap) {
                            stringResource(R.string.pro_card_at_cap)
                        } else {
                            stringResource(R.string.pro_card_pay_once)
                        },
                        style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                        color = colors.onSurfaceVariant,
                    )
                }
                liveSale?.percentOff?.let {
                    Text(
                        ("−" + NumberFormat.getPercentInstance().format(it / 100.0)).unbroken(),
                        Modifier.clip(CircleShape).background(colors.primary)
                            .padding(horizontal = 9.dp, vertical = 5.dp).testTag("pro_sale_badge"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimary,
                    )
                }
            }

            // Label, "1 / 3" and the bar are one fact. Spoken separately TalkBack says the ratio twice
            // and then a percentage; cleared and relabelled it says it once, in words.
            val progressDescription = stringResource(R.string.pro_progress_description, activeCount, limit)
            Column(
                Modifier.clearAndSetSemantics { contentDescription = progressDescription },
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    maxItemsInEachRow = if (stacked) 1 else 2,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.pro_active_shortcuts), style = MaterialTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = colors.onSurfaceVariant)
                    Text(
                        "$activeCount / $limit",
                        // A count pair reads left to right in every language; RTL would flip it to "2 / 1".
                        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                        color = colors.onSurface,
                    )
                }
                LinearProgressIndicator(
                    progress = { if (limit == 0) 1f else (activeCount.toFloat() / limit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = if (atCap) colors.error else colors.primary,
                    trackColor = colors.surfaceVariant,
                )
            }

            FreeVsProTable(stacked)

            if (proPrice != null) {
                if (liveSale != null) {
                    ProSaleLine(liveSale, proPrice, stacked)
                } else {
                    Text(
                        proPrice.unbroken(),
                        style = if (stacked) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                }
            }

            Button(
                onClick = onOpenProScreen,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                shape = InnerShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("pro_upsell_purchase_button"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.pro_unlock),
                        style = MaterialTheme.typography.titleSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.pro_card_one_time),
                        style = MaterialTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                        color = colors.onPrimary.copy(alpha = .85f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * A Play Console discount: old price struck out, the new one, and the time left. Price, percentage and
 * end all come from Play, so ending the offer there removes this line; it also hides itself at the end.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ProSaleLine(sale: ProSale, price: String, stacked: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val now = rememberSaleNow(sale.endsAtMillis)
    val parts = sale.endsAtMillis?.let { saleTimeLeft(it, now) ?: return }
    FlowRow(
        Modifier.fillMaxWidth().testTag("pro_sale_line"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            Modifier.align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                sale.fullPrice.unbroken(),
                Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.LineThrough,
                color = colors.onSurfaceVariant,
            )
            Text(
                price.unbroken(),
                // At large font scales a long local price ("COP 10.500,00") is wider than the card.
                style = if (stacked) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
        }
        if (parts != null) {
            Text(
                stringResource(R.string.pro_sale_ends_in, formatTimeLeft(parts)),
                Modifier.clip(CircleShape).background(colors.primary.copy(alpha = .10f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
        }
    }
}

/**
 * The clock behind a sale countdown. No loop when the sale has no end, and no state write when the
 * two units on screen (days and hours, or hours and minutes) have not changed. Stops in the background.
 */
@Composable
private fun rememberSaleNow(endsAtMillis: Long?): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(endsAtMillis) {
        val ends = endsAtMillis ?: return@LaunchedEffect
        if (System.currentTimeMillis() >= ends) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(30_000)
                val next = System.currentTimeMillis()
                if (next >= ends) {
                    now = next
                    break
                }
                if (saleTimeLeft(ends, next) != saleTimeLeft(ends, now)) now = next
            }
        }
    }
    return now
}

/**
 * Keeps one price or percentage on one line: low-price-level markets format to strings like
 * "COP 16.900,00", and "−%38" otherwise breaks after the minus. Lines break only between the items.
 */
internal fun String.unbroken(): String =
    replace(' ', '\u00A0').replace("−", "−\u2060").replace("%", "%\u2060").trimEnd('\u2060')

/** Localized "6 days, 4 hours" from Android's own unit names, so no unit strings need translating. */
private fun formatTimeLeft(parts: List<Pair<Int, MeasureUnit>>): String =
    MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
        .formatMeasures(*parts.map { (value, unit) -> Measure(value, unit) }.toTypedArray())

/** The two largest units left until [endsAtMillis] (days+hours, or hours+minutes); null once it has ended. */
internal fun saleTimeLeft(endsAtMillis: Long, nowMillis: Long): List<Pair<Int, MeasureUnit>>? {
    val minutes = (endsAtMillis - nowMillis) / 60_000
    if (endsAtMillis <= nowMillis) return null
    val days = (minutes / (24 * 60)).toInt()
    val hours = (minutes / 60 % 24).toInt()
    val mins = (minutes % 60).toInt()
    return when {
        days > 0 -> listOf(days to MeasureUnit.DAY, hours to MeasureUnit.HOUR)
        hours > 0 -> listOf(hours to MeasureUnit.HOUR, mins to MeasureUnit.MINUTE)
        else -> listOf(maxOf(mins, 1) to MeasureUnit.MINUTE)
    }
}

/** Each feature is one accessible comparison, including in the large-font layout. */
@Composable
private fun FreeVsProTable(stacked: Boolean) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier.fillMaxWidth().clip(InnerShape).background(colors.surface)
            .border(1.dp, colors.surfaceVariant, InnerShape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.pro_table_what_you_get).uppercase(locale),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = .06.em, hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                color = colors.onSurfaceVariant,
            )
            if (!stacked) {
                ComparisonPlanPill(stringResource(R.string.pro_table_free).uppercase(locale), pro = false)
                ComparisonPlanPill(stringResource(R.string.pro_table_pro).uppercase(locale), pro = true)
            }
        }
        ComparisonRow(
            stringResource(R.string.pro_active_shortcuts),
            Icons.Outlined.Keyboard,
            stacked,
            freeIncluded = null,
        )
        if (CapsuleHooks.AVAILABLE) {
            ComparisonRow(
                stringResource(R.string.capsule_intro_title),
                Icons.Outlined.AutoAwesome,
                stacked,
                freeIncluded = false,
            )
        }
        if (com.example.mode.ModeHooks.AVAILABLE) {
            ComparisonRow(
                stringResource(R.string.pro_table_modes),
                Icons.Outlined.Tune,
                stacked,
                freeIncluded = false,
            )
        }
        ComparisonRow(stringResource(R.string.pro_table_future), Icons.Outlined.Update, stacked, freeIncluded = false)
        ComparisonRow(stringResource(R.string.pro_table_everything_else), Icons.Outlined.DoneAll, stacked, freeIncluded = true)
    }
}

@Composable
private fun ComparisonPlanPill(label: String, pro: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            Modifier.clip(CircleShape)
                .then(if (pro) Modifier.background(colors.primary) else Modifier.border(1.dp, colors.outline, CircleShape))
                .padding(horizontal = 7.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (pro) colors.onPrimary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComparisonRow(label: String, icon: ImageVector, stacked: Boolean, freeIncluded: Boolean?) {
    val colors = MaterialTheme.colorScheme
    val free = if (freeIncluded == null) {
        "${EntitlementManager.FREE_SCRIPT_LIMIT}"
    } else {
        stringResource(if (freeIncluded) R.string.pro_table_included else R.string.pro_table_not_included)
    }
    val pro = stringResource(if (freeIncluded == null) R.string.pro_table_unlimited else R.string.pro_table_included)
    val rowDescription = stringResource(R.string.pro_table_row_description, label, free, pro)
    HorizontalDivider(color = colors.surfaceVariant)
    Column(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = rowDescription }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                Text(
                    label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                    color = colors.onSurface,
                )
            }
            if (!stacked) {
                Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                    if (freeIncluded == null) {
                        Text(free, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                    } else {
                        ComparisonCheck(included = freeIncluded, pro = false)
                    }
                }
                Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                    if (freeIncluded == null) {
                        Text("∞", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.primary)
                    } else {
                        ComparisonCheck(included = true, pro = true)
                    }
                }
            }
        }
        if (stacked) {
            Text(
                "${stringResource(R.string.pro_table_free)}: $free · ${stringResource(R.string.pro_table_pro)}: $pro",
                style = MaterialTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComparisonCheck(included: Boolean, pro: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(24.dp).clip(CircleShape).then(
            when {
                !included -> Modifier.background(colors.surfaceVariant)
                pro -> Modifier.background(colors.primary)
                else -> Modifier.border(1.5.dp, colors.outline, CircleShape)
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (included) Icons.Filled.Check else Icons.Filled.Close,
            null,
            Modifier.size(if (included) 13.dp else 12.dp),
            tint = when {
                !included -> colors.onSurfaceVariant
                pro -> colors.onPrimary
                else -> colors.onSurface
            },
        )
    }
}

@Composable
internal fun ConsoleHeader(
    isMappingEnabled: Boolean,
    themeMode: ThemeMode,
    onToggleMapping: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    setupRequired: Boolean = false,
    announceWhatsNew: Boolean = false,
    showWhatsNewHint: Boolean = false,
    onWhatsNewHintFinished: () -> Unit = {},
    /** The lockup's on-screen bounds, reported just before [onOpenWhatsNew], so it can fly into the dialog. */
    onLogoBounds: (Rect) -> Unit = {},
    /** The lockup is away in the What's New dialog: leave its place empty until it flies back. */
    logoAway: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    var logoBounds by remember { mutableStateOf<Rect?>(null) }
    val animateAnnouncement = announceWhatsNew && !reducedMotion
    val cycle = rememberAnnouncementCycle(animateAnnouncement)
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
            .zIndex(if (showWhatsNewHint) 1f else 0f),
        content = {
            // The logo opens What's New. Theme stays in Settings → Appearance.
            Box(
                Modifier
                    .heightIn(min = MinTouchTarget)
                    .testTag("header_logo")
                    .clickable(onClickLabel = stringResource(R.string.whats_new_title)) {
                        logoBounds?.let(onLogoBounds)
                        onOpenWhatsNew()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box {
                    KeySnapLockup(
                        themeMode,
                        (if (animateAnnouncement) Modifier.logoShine(cycle) else Modifier)
                            .onGloballyPositioned { logoBounds = Rect(it.positionOnScreen(), it.size.toSize()) }
                            .graphicsLayer { alpha = if (logoAway) 0f else 1f },
                    )
                    if (announceWhatsNew) {
                        AnnouncementDot(
                            progress = cycle,
                            animate = animateAnnouncement,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                        )
                    }
                    if (showWhatsNewHint) {
                        WhatsNewHintBubble(
                            reducedMotion = reducedMotion,
                            onFinished = onWhatsNewHintFinished,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
            }
            Box(Modifier.testTag("header_controls")) {
                MappingBadge(isMappingEnabled, onToggleMapping, setupRequired)
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // Keep the status beside the floating Settings gear on one row, including at 200% text.
        // Share tight widths with the vector logo; the status can ellipsize within what remains.
        val rowWidth = (constraints.maxWidth - 60.dp.roundToPx()).coerceAtLeast(0)
        val gap = minOf(12.dp.roundToPx(), rowWidth)
        val contentWidth = rowWidth - gap
        val logo = measurables[0].measure(loose.copy(maxWidth = (contentWidth * .55f).toInt()))
        val controls = measurables[1].measure(loose.copy(maxWidth = contentWidth - logo.width))
        val height = maxOf(logo.height, controls.height)
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            logo.placeRelative(0, (height - logo.height) / 2)
            controls.placeRelative(rowWidth - controls.width, (height - controls.height) / 2)
        }
    }
}

/** The global mapping switch: states what is true now, and flips it on tap. */
@Composable
private fun MappingBadge(active: Boolean, onClick: () -> Unit, setupRequired: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    // Moves only when the state flips — a colour sweep, the dot hopping into pause bars and the
    // label rolling over — and stands still otherwise. All of it follows the system animation scale.
    val accent by animateColorAsState(if (setupRequired) com.example.ui.theme.setupWarning() else if (active) colors.primary else colors.error, tween(BadgeMs), label = "badge_accent")
    val paused by animateFloatAsState(if (active) 0f else 1f, tween(BadgeMs, easing = BadgeEasing), label = "badge_icon")
    val sweep = remember { Animatable(1f) }
    val hop = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        // The first composition is not a flip.
        if (!settled) { settled = true; return@LaunchedEffect }
        launch { sweep.snapTo(0f); sweep.animateTo(1f, tween(500, easing = BadgeEasing)) }
        launch { hop.snapTo(0f); hop.animateTo(1f, tween(450, easing = BadgeEasing)) }
    }
    Row(
        Modifier
            .testTag("mapping_badge")
            .clip(CircleShape)
            .heightIn(min = MinTouchTarget)
            .clickable(
                role = if (setupRequired) Role.Button else Role.Switch,
                onClickLabel = stringResource(if (setupRequired) R.string.setup_label else if (active) R.string.home_pause_all else R.string.home_resume_all),
                onClick = onClick,
            )
            // Role.Switch alone leaves TalkBack with no on/off to read out.
            .semantics { if (!setupRequired) toggleableState = ToggleableState(active) }
            .background(accent.copy(alpha = .14f))
            .drawBehind {
                // Fills from the start edge over the first 60%, then fades out.
                val p = sweep.value
                if (p >= 1f) return@drawBehind
                val fill = size.width * (p / .6f).coerceAtMost(1f)
                val alpha = if (p < .6f) 1f else 1f - (p - .6f) / .4f
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width - fill else 0f
                drawRect(accent.copy(alpha = .25f * alpha), Offset(left, 0f), Size(fill, size.height))
            }
            .border(1.dp, accent.copy(alpha = .55f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (setupRequired) {
            Icon(Icons.Default.WarningAmber, null, Modifier.size(14.dp), tint = accent)
        } else Canvas(
            Modifier.size(8.dp).graphicsLayer {
                val lift = sin(hop.value * PI.toFloat())
                translationY = -5.dp.toPx() * lift
                scaleX = 1f + .3f * lift
                scaleY = scaleX
            },
        ) {
            // A dot that narrows into the first pause bar while the second bar fades in.
            val barWidth = size.width - (size.width - 2.5.dp.toPx()) * paused
            val corner = CornerRadius(barWidth / 2f)
            drawRoundRect(accent, Offset(.75.dp.toPx() * paused, 0f), Size(barWidth, size.height), corner)
            drawRoundRect(accent.copy(alpha = paused), Offset(size.width - barWidth - .75.dp.toPx() * paused, 0f), Size(barWidth, size.height), corner)
        }
        Spacer(Modifier.width(7.dp))
        AnimatedContent(
            targetState = active,
            transitionSpec = {
                val down = if (targetState) -1 else 1
                (slideInVertically(tween(BadgeMs, easing = BadgeEasing)) { it * down } + fadeIn(tween(BadgeMs)))
                    .togetherWith(slideOutVertically(tween(BadgeMs, easing = BadgeEasing)) { -it * down } + fadeOut(tween(BadgeMs)))
                    .using(SizeTransform(clip = true) { _, _ -> tween(BadgeMs, easing = BadgeEasing) })
            },
            label = "badge_label",
        ) { on ->
            Text(
                stringResource(if (setupRequired) R.string.setup_label else if (on) R.string.home_status_live else R.string.home_status_paused),
                modifier = Modifier.testTag("mapping_status_label"),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val BadgeMs = 350
private val BadgeEasing = CubicBezierEasing(.2f, .9f, .2f, 1f)

@Composable
private fun LiveStatusPanel(
    isServiceActive: Boolean,
    isAdvancedActive: Boolean,
    triggerCount: Int,
    lastSource: String,
    onOpenWizard: () -> Unit,
    onOpenAccessibility: () -> Unit,
    accessibilityStatus: AccessibilityStatus,
    showColorOsRecentsTip: Boolean,
    onDismissColorOsRecentsTip: () -> Unit,
) {
    // Anything that needs fixing is the setup card's job now, so this panel only ever reports:
    // it never paints an alarm and never opens itself.
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    ConsolePanel(
        label = stringResource(R.string.home_system_status),
        testTag = "system_status",
        onHeaderClick = { isExpanded = !isExpanded },
        isExpanded = isExpanded,
        headerActionDescription = stringResource(if (isExpanded) R.string.home_collapse_status else R.string.home_expand_status),
        headerActionIcon = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
    ) {
        // Visible without expanding: ColorOS users need this before they swipe the app away.
        if (showColorOsRecentsTip) {
            ColorOsRecentsLockTip(onDismissColorOsRecentsTip)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
        }
        AnimatedVisibility(visible = isExpanded) {
            Column {
                StatusLine(
                    label = stringResource(R.string.home_accessibility_service),
                    status = stringResource(accessibilityStatus.label),
                    active = isServiceActive,
                    onClick = onOpenAccessibility,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                StatusLine(
                    // Reports whether the detached bridge answers its control port, which is not
                    // the same thing as an ADB connection — so it is named for what it measures.
                    label = stringResource(R.string.home_status_advanced),
                    status = stringResource(if (isAdvancedActive) R.string.home_status_running else R.string.home_status_off),
                    active = isAdvancedActive,
                    onClick = onOpenWizard,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // The counter lives in the coordinator and starts at zero with the process, so it
                // is a session total. Calling it "today" would claim a number the app cannot back.
                Text(
                    stringResource(R.string.home_triggers_session),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isExpanded && lastSource.isNotBlank()) {
                    Text(
                        stringResource(R.string.home_last_from, lastSource),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                triggerCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** ColorOS kills a swiped-away KeySnap and turns Accessibility off. Dismiss hides it for good. */
@Composable
private fun ColorOsRecentsLockTip(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().testTag("coloros_recents_lock_tip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.home_coloros_recents_title),
                style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                color = colors.onSurface,
            )
            Text(
                stringResource(R.string.home_coloros_recents_body),
                style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                color = colors.onSurfaceVariant,
            )
        }
        IconButton(onDismiss) {
            Icon(Icons.Filled.Close, stringResource(R.string.home_nudge_dismiss), tint = colors.onSurfaceVariant)
        }
    }
}

/** One system dependency, reported, with the screen that changes it one tap away. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StatusLine(
    label: String,
    status: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val statusColor = if (active) colors.primary else colors.onSurfaceVariant
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.home_open_label_settings, label),
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
    ) {
        val stacked = maxWidth / LocalDensity.current.fontScale < 240.dp
        FlowRow(
            Modifier.fillMaxWidth(),
            maxItemsInEachRow = if (stacked) 1 else 2,
        ) {
            Text(
                label,
                (if (stacked) Modifier.fillMaxWidth() else Modifier.weight(1f)).align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                color = colors.onSurface,
            )
            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(6.dp))
                Text(status, style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = statusColor)
                Icon(
                    Icons.Outlined.ChevronRight,
                    null,
                    Modifier.padding(start = 2.dp).size(18.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * What the user actually built, above the ready-made suggestions — without it Home looks identical
 * before and after the first shortcut exists. A row, not a card: the enable toggle and the delete
 * button stay on Scripts, so this stays a place to glance and open.
 */
@Composable
private fun YourShortcutsPanel(scripts: List<ShortcutScript>, onOpenScript: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    ConsolePanel(label = stringResource(R.string.home_your_shortcuts), testTag = "your_shortcuts") {
        Column {
            scripts.forEachIndexed { index, script ->
                if (index > 0) HorizontalDivider(color = colors.outline.copy(alpha = .45f))
                ShortcutRow(script, onOpenScript)
            }
        }
    }
}

@Composable
internal fun ShortcutRow(script: ShortcutScript, onOpenScript: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = script.name.ifBlank { stringResource(R.string.scripts_unnamed) }
    val (triggerText, triggerOk) = triggerSummary(script)
    val firstAction = script.actions.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .testTag("home_script_${script.id}")
            .clickable(role = Role.Button, onClickLabel = name, onClick = { onOpenScript(script.id) })
            .alpha(if (script.enabled) 1f else 0.5f)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (firstAction != null) {
            IconTile(
                iconForScript(script),
                ScriptActionCatalog.descriptor(firstAction.type)?.category,
                size = 38.dp,
            )
        } else {
            Box(Modifier.size(38.dp).clip(InnerShape).background(colors.surfaceVariant))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                triggerText,
                style = MaterialTheme.typography.bodySmall,
                color = if (triggerOk) colors.onSurfaceVariant else colors.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
    }
}
