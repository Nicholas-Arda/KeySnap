package com.example.surface

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.data.AppLocale
import com.example.data.EntitlementManager
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import com.example.ui.components.ConsoleSwitch
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.scripts.actionIconFor
import com.example.ui.scripts.triggerSummary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.storedThemeMode
import com.example.ui.theme.storedThemePrefs
import io.github.nicholasarda.keysnap.R
import kotlin.math.roundToInt

/**
 * Picks which shortcut a widget or a Quick Settings tile runs. Exported, because the widget host
 * launches it to configure a new widget, so it acts only on a widget id that belongs to
 * [ShortcutWidgetProvider], or tile slots 1–3.
 */
class ShortcutPickerActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    private sealed interface Target {
        data class Widget(val id: Int) : Target
        data class Tile(val slot: Int) : Target
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = target() ?: return finish()
        if (target is Target.Widget) {
            // Anything but RESULT_OK tells the host not to place the widget, so Back cancels cleanly.
            setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, target.id))
        }
        // A free user's third widget gets the Pro offer instead of a binding; cancelling leaves the
        // result above, so the host removes the widget.
        val overLimit = target is Target.Widget && !SurfaceBindings.widgetAllowed(this, target.id)
        enableEdgeToEdge()
        val themeMode = storedThemeMode(this)
        val oled = storedThemePrefs(this).oled
        val scripts = ScriptRepository.getInstance(this).scripts
        setContent {
            MyApplicationTheme(themeMode = themeMode, oled = oled) {
                if (overLimit) {
                    WidgetLimitScreen(onUnlockPro = ::openProInApp, onCancel = ::finish)
                } else {
                    val list by scripts.collectAsState()
                    when (target) {
                        is Target.Tile -> ShortcutPickerScreen(list) { scriptId -> bind(target, scriptId) }
                        is Target.Widget -> WidgetConfig(target, list)
                    }
                }
            }
        }
    }

    private fun target(): Target? {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val provider = AppWidgetManager.getInstance(this)?.getAppWidgetInfo(widgetId)?.provider
            return Target.Widget(widgetId).takeIf { provider == ComponentName(this, ShortcutWidgetProvider::class.java) }
        }
        val slot = intent.getIntExtra(EXTRA_TILE_SLOT, 0).takeIf { it in 1..ShortcutTileService.SLOT_COUNT }
            ?: ShortcutTileService.slotFor(tileComponent()?.className)
        return slot?.let { Target.Tile(it) }
    }

    /** Set by the system when the user long-presses one of this app's tiles (QS_TILE_PREFERENCES). */
    private fun tileComponent(): ComponentName? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME)
    }

    /**
     * Pick a shortcut, then style the widget. Reconfiguring a widget whose shortcut still exists
     * opens straight on its style, with its stored values; Back from the style returns to the list.
     */
    @Composable
    private fun WidgetConfig(target: Target.Widget, scripts: List<ShortcutScript>) {
        var scriptId by rememberSaveable { mutableStateOf(SurfaceBindings.widgetScript(this, target.id)) }
        var style by rememberSaveable(stateSaver = WidgetStyleSaver) {
            mutableStateOf(SurfaceBindings.widgetStyle(this, target.id) ?: WidgetStyle())
        }
        val script = scripts.firstOrNull { it.id == scriptId }
        if (script == null) {
            ShortcutPickerScreen(scripts) { scriptId = it }
        } else {
            BackHandler { scriptId = null }
            WidgetStyleScreen(
                script = script,
                style = style,
                onStyle = { style = it },
                onChangeShortcut = { scriptId = null },
                onDone = { bindWidget(target, script.id, style) },
            )
        }
    }

    private fun bind(target: Target.Tile, scriptId: String) {
        SurfaceBindings.bindTile(this, target.slot, scriptId)
        finish()
    }

    private fun bindWidget(target: Target.Widget, scriptId: String, style: WidgetStyle) {
        SurfaceBindings.bindWidget(this, target.id, scriptId)
        SurfaceBindings.setWidgetStyle(this, target.id, style)
        ShortcutWidgetProvider.render(this, target.id)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, target.id))
        finish()
    }

    /** The purchase lives on Home's Pro card; the widget is not placed, and can be added again after. */
    private fun openProInApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_TILE_SLOT = "com.example.surface.TILE_SLOT"

        fun tileIntent(context: Context, slot: Int): Intent =
            Intent(context, ShortcutPickerActivity::class.java).putExtra(EXTRA_TILE_SLOT, slot)

        /** For a widget whose shortcut was deleted: tapping it asks again. */
        fun widgetPendingIntent(context: Context, appWidgetId: Int): PendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            Intent(context, ShortcutPickerActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

@Composable
internal fun ShortcutPickerScreen(scripts: List<ShortcutScript>, onPick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.surface_choose_shortcut),
            Modifier.padding(vertical = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
        )
        if (scripts.isEmpty()) {
            Text(
                stringResource(R.string.surface_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(scripts, key = { it.id }) { script ->
                val name = script.name.ifBlank { stringResource(R.string.scripts_unnamed) }
                ListItem(
                    headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(triggerSummary(script).first, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier
                        .clip(InnerShape)
                        .clickable { onPick(script.id) }
                        .testTag("shortcut_picker_row_${script.id}"),
                )
            }
        }
    }
}

/** The free plan's widget limit, in the same words and with the same Pro button as the rest of the app. */
@Composable
internal fun WidgetLimitScreen(onUnlockPro: () -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name_pro), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Text(
            stringResource(R.string.widget_limit_reached, EntitlementManager.FREE_WIDGET_LIMIT),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Button(
            onClick = onUnlockPro,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("widget_limit_unlock_pro"),
        ) { Text(stringResource(R.string.pro_unlock)) }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("widget_limit_cancel"),
        ) { Text(stringResource(R.string.common_cancel)) }
    }
}

private val WidgetStyleSaver = Saver<WidgetStyle, String>(save = { it.encode() }, restore = { WidgetStyle.decode(it) })

private val WidgetColor.titleRes: Int
    get() = when (this) {
        WidgetColor.BLUE -> R.string.widget_color_blue
        WidgetColor.WALLPAPER -> R.string.widget_color_wallpaper
        WidgetColor.DARK -> R.string.widget_color_dark
        WidgetColor.LIGHT -> R.string.widget_color_light
    }

/** The widget's look: a live preview at one cell and at two, and the four choices under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetStyleScreen(
    script: ShortcutScript,
    style: WidgetStyle,
    onStyle: (WidgetStyle) -> Unit,
    onChangeShortcut: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.widget_style_title),
            Modifier.padding(top = 16.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetPreview(script, style, compact = true, Modifier.size(72.dp))
            WidgetPreview(script, style, compact = false, Modifier.size(width = 152.dp, height = 72.dp))
        }
        Text(stringResource(R.string.widget_style_color), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetColor.entries.forEach { color ->
                FilterChip(
                    selected = style.color == color,
                    onClick = { onStyle(style.copy(color = color)) },
                    label = { Text(stringResource(color.titleRes), style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(colorResource(color.ground)))
                    },
                    modifier = Modifier.testTag("widget_color_${color.name.lowercase()}"),
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary,
                        selectedLabelColor = colors.onPrimary,
                        containerColor = colors.surfaceVariant,
                        labelColor = colors.onSurfaceVariant,
                    ),
                )
            }
        }
        val opacityTitle = stringResource(R.string.widget_style_opacity)
        val opacityValue = stringResource(R.string.widget_style_percent, style.opacity)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(opacityTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(opacityValue, style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
        Slider(
            value = style.opacity.toFloat(),
            onValueChange = { onStyle(style.copy(opacity = it.roundToInt())) },
            valueRange = 0f..100f,
            modifier = Modifier.testTag("widget_opacity_slider").semantics {
                contentDescription = opacityTitle
                stateDescription = opacityValue
            },
        )
        ConsoleSwitch(stringResource(R.string.widget_style_show_label), style.showLabel) {
            onStyle(style.copy(showLabel = it))
        }
        OutlinedTextField(
            value = style.customLabel,
            onValueChange = { onStyle(style.copy(customLabel = it)) },
            modifier = Modifier.fillMaxWidth().testTag("widget_custom_label_field"),
            label = { Text(stringResource(R.string.widget_style_custom_label)) },
            placeholder = { Text(script.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingText = { Text(stringResource(R.string.widget_style_custom_label_hint)) },
            singleLine = true,
        )
        ConsoleSwitch(stringResource(R.string.widget_style_show_icon), style.showIcon) {
            onStyle(style.copy(showIcon = it))
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("widget_style_done"),
        ) { Text(stringResource(R.string.common_done)) }
        TextButton(
            onClick = onChangeShortcut,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).padding(bottom = 8.dp)
                .testTag("widget_style_change_shortcut"),
        ) { Text(stringResource(R.string.surface_choose_shortcut)) }
    }
}

/** Draws what [ShortcutWidgetProvider.render] will, from the same colour resources and [WidgetStyle.parts]. */
@Composable
private fun WidgetPreview(script: ShortcutScript, style: WidgetStyle, compact: Boolean, modifier: Modifier) {
    val content = colorResource(style.color.content)
    val label = style.customLabel.ifBlank { script.name }
    val (showIcon, showLabel) = style.parts(compact)
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(style.color.ground).copy(alpha = style.opacity / 100f))
            .padding(6.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showIcon) {
                Icon(actionIconFor(script.actions.firstOrNull()?.type.orEmpty()), null, Modifier.size(36.dp), tint = content)
            }
            if (showLabel) {
                Text(
                    label,
                    color = content,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
