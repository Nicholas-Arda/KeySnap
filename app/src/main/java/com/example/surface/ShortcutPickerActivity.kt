package com.example.surface

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.ActionCategory
import com.example.data.AppLocale
import com.example.data.KeyMappingRepository
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.scripts.actionIconFor
import com.example.ui.scripts.triggerSummary
import com.example.ui.scripts.triggerSummaryText
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.restingColorFor
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
        data class ListWidget(val id: Int) : Target
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = target() ?: return finish()
        val widgetId = (target as? Target.Widget)?.id ?: (target as? Target.ListWidget)?.id
        if (widgetId != null) {
            // Anything but RESULT_OK tells the host not to place the widget, so Back cancels cleanly.
            setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
        enableEdgeToEdge()
        val themeMode = storedThemeMode(this)
        val oled = storedThemePrefs(this).oled
        val scripts = ScriptRepository.getInstance(this).scripts
        // Key names (an OEM key the user named) live in the key repository; load it in a cold process.
        KeyMappingRepository.getInstance(this)
        setContent {
            MyApplicationTheme(themeMode = themeMode, oled = oled) {
                val list by scripts.collectAsState()
                when (target) {
                    is Target.Tile -> TileConfig(target, list)
                    is Target.Widget -> WidgetConfig(target, list)
                    // The list widget's provider is disabled below API 31, so it never gets here there.
                    is Target.ListWidget -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ListWidgetConfig(target, list)
                }
            }
        }
    }

    private fun target(): Target? {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            return when (AppWidgetManager.getInstance(this)?.getAppWidgetInfo(widgetId)?.provider) {
                ComponentName(this, ShortcutWidgetProvider::class.java) -> Target.Widget(widgetId)
                ComponentName(this, ShortcutListWidgetProvider::class.java) -> Target.ListWidget(widgetId)
                else -> null
            }
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
        var toggles by rememberSaveable { mutableStateOf(SurfaceBindings.widgetToggles(this, target.id)) }
        // A new widget starts in this phone's own look; one placed before looks keeps Classic.
        var style by rememberSaveable(stateSaver = WidgetStyleSaver) {
            mutableStateOf(SurfaceBindings.widgetStyle(this, target.id) ?: WidgetStyle(look = WidgetLook.forPhone()))
        }
        val all = scriptId == SurfaceToggle.ALL
        val script = scripts.firstOrNull { it.id == scriptId }
        if (!all && script == null) {
            ShortcutPickerScreen(scripts) { scriptId = it }
        } else {
            BackHandler { scriptId = null }
            WidgetStyleScreen(
                name = if (all) stringResource(R.string.app_name) else script!!.name,
                icon = if (all) Icons.Outlined.PowerSettingsNew else actionIconFor(script!!.actions.firstOrNull()?.type.orEmpty()),
                sub = script?.let { triggerSummaryText(resources, it).first },
                all = all,
                toggles = all || toggles,
                onToggles = { toggles = it },
                style = style,
                onStyle = { style = it },
                onChangeShortcut = { scriptId = null },
                onClose = ::finish,
                onDone = { bindWidget(target, scriptId!!, all || toggles, style) },
            )
        }
    }

    /** The list widget has no shortcut to pick: only its look. */
    @RequiresApi(Build.VERSION_CODES.S)
    @Composable
    private fun ListWidgetConfig(target: Target.ListWidget, scripts: List<ShortcutScript>) {
        var style by rememberSaveable(stateSaver = WidgetStyleSaver) {
            mutableStateOf(SurfaceBindings.widgetStyle(this, target.id) ?: WidgetStyle(look = WidgetLook.forPhone()))
        }
        var parts by rememberSaveable(stateSaver = ListPartsSaver) { mutableStateOf(SurfaceBindings.listParts(this, target.id)) }
        ListWidgetStyleScreen(scripts, style, { style = it }, parts, { parts = it }, ::finish) {
            SurfaceBindings.setWidgetStyle(this, target.id, style)
            SurfaceBindings.setListParts(this, target.id, parts)
            ShortcutListWidget.render(this, target.id)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, target.id))
            finish()
        }
    }

    /**
     * Pick a shortcut, then what a tap does and the tile's icon; "All shortcuts" only switches. A
     * long-press on a tile whose shortcut still exists opens straight on its settings.
     */
    @Composable
    private fun TileConfig(target: Target.Tile, scripts: List<ShortcutScript>) {
        var picked by rememberSaveable { mutableStateOf(SurfaceBindings.tileScript(this, target.slot)) }
        var toggles by rememberSaveable { mutableStateOf(SurfaceBindings.tileToggles(this, target.slot)) }
        var icon by rememberSaveable { mutableStateOf(SurfaceBindings.tileIcon(this, target.slot)) }
        val all = picked == SurfaceToggle.ALL
        val script = scripts.firstOrNull { it.id == picked }
        if (!all && script == null) {
            // A new shortcut starts with its own icon.
            ShortcutPickerScreen(scripts) { picked = it; icon = null }
        } else {
            BackHandler { picked = null }
            TileStyleScreen(
                name = if (all) stringResource(R.string.app_name) else script!!.name,
                defaultIcon = ShortcutTileService.iconFor(null, all, script)!!,
                all = all,
                toggles = all || toggles,
                onToggles = { toggles = it },
                icon = icon,
                onIcon = { icon = it },
                onChangeShortcut = { picked = null },
                onClose = ::finish,
                onDone = {
                    SurfaceBindings.bindTile(this, target.slot, picked!!, all || toggles, icon)
                    TileService.requestListeningState(this, ComponentName(this, ShortcutTileService.classFor(target.slot)))
                    finish()
                },
            )
        }
    }

    private fun bindWidget(target: Target.Widget, scriptId: String, toggles: Boolean, style: WidgetStyle) {
        SurfaceBindings.bindWidget(this, target.id, scriptId)
        SurfaceBindings.setWidgetToggles(this, target.id, toggles)
        SurfaceBindings.setWidgetStyle(this, target.id, style)
        ShortcutWidgetProvider.render(this, target.id)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, target.id))
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
            item(key = SurfaceToggle.ALL) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.surface_all_shortcuts)) },
                    supportingContent = { Text(stringResource(R.string.surface_all_shortcuts_sub)) },
                    leadingContent = { Icon(Icons.Outlined.PowerSettingsNew, null) },
                    modifier = Modifier
                        .clip(InnerShape)
                        .clickable { onPick(SurfaceToggle.ALL) }
                        .testTag("shortcut_picker_row_all"),
                )
            }
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


private val ListPartsSaver = Saver<ListParts, List<Boolean>>(
    save = { listOf(it.live, it.keys, it.header) },
    restore = { ListParts(it[0], it[1], it[2]) },
)

private val WidgetStyleSaver = Saver<WidgetStyle, String>(save = { it.encode() }, restore = { WidgetStyle.decode(it) })

private val WidgetColor.titleRes: Int
    get() = when (this) {
        WidgetColor.BLUE -> R.string.widget_color_blue
        WidgetColor.WALLPAPER -> R.string.widget_color_wallpaper
        WidgetColor.DARK -> R.string.widget_color_dark
        WidgetColor.LIGHT -> R.string.widget_color_light
    }

internal val WidgetLook.titleRes: Int
    get() = when (this) {
        WidgetLook.CLASSIC -> R.string.widget_look_classic
        WidgetLook.PIXEL -> R.string.widget_look_pixel
        WidgetLook.NOTHING -> R.string.widget_look_nothing
        WidgetLook.ONE_UI -> R.string.widget_look_one_ui
        WidgetLook.HYPER_OS -> R.string.widget_look_hyper_os
    }

/** The "on" colours offered for a phone look: the app's own category tones, and white. */
internal val WidgetAccents: List<Int> = ActionCategory.entries.map { restingColorFor(it).toArgb() } + 0xFFF4F4F4.toInt()

/**
 * The small widget over a wallpaper, at one cell and at two, drawn from the very RemoteViews the
 * home screen gets (tap it to see the other state of an on/off widget), with every option in the
 * sheet under it.
 */
@Composable
internal fun WidgetStyleScreen(
    name: String,
    icon: ImageVector,
    sub: String?,
    all: Boolean,
    toggles: Boolean,
    onToggles: (Boolean) -> Unit,
    style: WidgetStyle,
    onStyle: (WidgetStyle) -> Unit,
    onChangeShortcut: () -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var previewOn by rememberSaveable { mutableStateOf(true) }
    // A run button has no off state, so it wears the on colour.
    val on = !toggles || previewOn
    val state = ShortcutWidgetProvider.stateText(context.resources, toggles, all, on)
    val label = style.customLabel.ifBlank { name }
    val tap = { previewOn = !previewOn }.takeIf { toggles }
    val tick = rememberTick()
    WallpaperScreen("widget_style_back", onClose) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 28.dp).testTag("widget_style_preview"),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (compact in listOf(true, false)) {
                val views = ShortcutWidgetProvider.styleViews(
                    context, style, compact, on, toggles, label, sub, state, icon.takeIf { style.showIcon },
                )
                FacePreview(views, Modifier.size(width = if (compact) 76.dp else 170.dp, height = 76.dp), tap)
            }
        }
        OptionsSurface(Modifier.weight(1f)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!all) {
                    SheetLabel(stringResource(R.string.widget_tap_title))
                    ChipRow {
                        PartChip(stringResource(R.string.widget_tap_run), Icons.Outlined.PlayArrow, !toggles, "widget_tap_run") {
                            onToggles(false)
                        }
                        PartChip(stringResource(R.string.widget_tap_toggle), Icons.Outlined.ToggleOn, toggles, "widget_tap_toggle") {
                            onToggles(true)
                        }
                    }
                }
                SheetLabel(stringResource(R.string.widget_style_look))
                LookChips(WidgetLook.entries, style, "widget_look", onStyle)
                if (style.look != WidgetLook.CLASSIC) {
                    SheetLabel(stringResource(R.string.widget_form_title))
                    ChipRow {
                        WidgetForm.entries.forEach { form ->
                            FilterChip(
                                selected = style.form == form,
                                onClick = { tick(); onStyle(style.copy(form = form)) },
                                label = { Text(stringResource(form.titleRes)) },
                                modifier = Modifier.testTag("widget_form_${form.name.lowercase()}"),
                            )
                        }
                    }
                }
                SheetLabel(stringResource(R.string.widget_style_color))
                if (style.look == WidgetLook.CLASSIC) {
                    ChipRow(spacing = 4) {
                        WidgetColor.entries.forEach { color ->
                            Swatch(
                                solidBrush(context.getColor(color.ground)),
                                style.color == color,
                                stringResource(color.titleRes),
                                "widget_color_${color.name.lowercase()}",
                            ) { onStyle(style.copy(color = color)) }
                        }
                    }
                } else {
                    AccentRow(style, onStyle)
                }
                SheetLabel(stringResource(R.string.list_widget_show))
                ChipRow {
                    PartChip(stringResource(R.string.widget_part_name), Icons.Outlined.Title, style.showLabel, "widget_part_name") {
                        onStyle(style.copy(showLabel = !style.showLabel))
                    }
                    PartChip(stringResource(R.string.widget_part_icon), icon, style.showIcon, "widget_part_icon") {
                        onStyle(style.copy(showIcon = !style.showIcon))
                    }
                }
                val opacityTitle = stringResource(R.string.widget_style_opacity)
                val opacityValue = stringResource(R.string.widget_style_percent, style.opacity)
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(opacityTitle, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text(opacityValue, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                OutlinedTextField(
                    value = style.customLabel,
                    onValueChange = { onStyle(style.copy(customLabel = it)) },
                    modifier = Modifier.fillMaxWidth().testTag("widget_custom_label_field"),
                    label = { Text(stringResource(R.string.widget_style_custom_label)) },
                    placeholder = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingText = { Text(stringResource(R.string.widget_style_custom_label_hint)) },
                    singleLine = true,
                )
                Button(
                    onClick = onDone,
                    shape = InnerShape,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = MinTouchTarget).testTag("widget_style_done"),
                ) { Text(stringResource(R.string.common_done)) }
                TextButton(
                    onClick = onChangeShortcut,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("widget_style_change_shortcut"),
                ) { Text(stringResource(R.string.surface_choose_shortcut)) }
            }
        }
    }
}

/** One RemoteViews applied in-process, the way the launcher would; [onTap] stands in for its click. */
@Composable
private fun FacePreview(views: RemoteViews, modifier: Modifier, onTap: (() -> Unit)?) {
    AndroidView(
        factory = { FrameLayout(it) },
        modifier = modifier.clearAndSetSemantics { },
        update = { frame ->
            frame.removeAllViews()
            val view = views.apply(frame.context, frame)
            frame.addView(view)
            if (onTap != null) view.setOnClickListener { onTap() } else view.setOnClickListener(null)
            view.isClickable = onTap != null
        },
    )
}
