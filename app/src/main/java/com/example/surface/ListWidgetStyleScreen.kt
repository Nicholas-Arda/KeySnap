package com.example.surface

import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import io.github.nicholasarda.keysnap.R

/*
 * The widget as it will sit on the home screen, over a wallpaper in the phone's own wallpaper
 * colours, with every option in one sheet under it: look, colour and what to show, as chips. Each
 * change shows in the preview at once, and nothing has to scroll.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
internal fun ListWidgetStyleScreen(
    scripts: List<ShortcutScript>,
    style: WidgetStyle,
    onStyle: (WidgetStyle) -> Unit,
    parts: ListParts,
    onParts: (ListParts) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    // Stand-in states for the preview: a tap there changes only the picture, never a shortcut.
    var live by remember { mutableStateOf(ScriptRepository.getInstance(context).globallyEnabled.value) }
    var states by remember(scripts) { mutableStateOf(scripts.associate { it.id to it.enabled }) }
    WallpaperScreen("list_widget_back", onBack) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            PreviewStage(style, parts, live, scripts.map { it to (states[it.id] ?: it.enabled) },
                onLive = { live = !live },
                onRow = { id -> states = states + (id to !(states[id] ?: false)) })
            // Where the home screen's apps would be; it places the widget, nothing more.
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(4) { Box(Modifier.size(52.dp).background(Color.White.copy(alpha = .3f), CircleShape)) }
            }
        }
        OptionsSurface {
            SheetLabel(stringResource(R.string.widget_style_look))
            LookChips(WidgetLook.entries - WidgetLook.CLASSIC, style, "list_widget_look", onStyle)
            SheetLabel(stringResource(R.string.widget_style_color))
            AccentRow(style, onStyle)
            SheetLabel(stringResource(R.string.list_widget_show))
            ChipRow {
                PartChip(stringResource(R.string.list_widget_show_live), Icons.Outlined.PowerSettingsNew, parts.live) {
                    onParts(parts.copy(live = !parts.live))
                }
                PartChip(stringResource(R.string.list_widget_show_keys), Icons.Outlined.Keyboard, parts.keys) {
                    onParts(parts.copy(keys = !parts.keys))
                }
                PartChip(stringResource(R.string.list_widget_show_header), Icons.Outlined.Title, parts.header) {
                    onParts(parts.copy(header = !parts.header))
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDone,
                shape = InnerShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("list_widget_style_done"),
            ) { Text(stringResource(R.string.common_done)) }
        }
    }
}

/**
 * The very RemoteViews the home screen draws. Its taps are rewired to [onLive] and [onRow], so the
 * colours answer while nothing runs or switches.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun PreviewStage(
    style: WidgetStyle,
    parts: ListParts,
    live: Boolean,
    rows: List<Pair<ShortcutScript, Boolean>>,
    onLive: () -> Unit,
    onRow: (String) -> Unit,
) {
    // Not state: a page turn draws its own views and must not wait for a recomposition.
    val page = remember { intArrayOf(0) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize()
            .testTag("list_widget_preview"),
    ) {
        AndroidView(
            factory = { FrameLayout(it) },
            modifier = Modifier.fillMaxWidth(),
            update = { frame ->
                val ctx = frame.context
                val chunks = rows.chunked(ShortcutListWidget.PREVIEW_ROWS)
                fun show(views: RemoteViews) {
                    frame.removeAllViews()
                    val view = views.apply(ctx, frame)
                    frame.addView(view)
                    view.findViewById<android.view.View>(R.id.list_live).setOnClickListener { onLive() }
                    // A turn draws afresh, as on the home screen, so the pages slide the same way.
                    fun turn(step: Int) {
                        val from = page[0]
                        page[0] = (from + step).mod(chunks.size.coerceAtLeast(1))
                        show(ShortcutListWidget.preview(ctx, style, parts, live, rows, page[0], from, back = step < 0))
                    }
                    view.findViewById<android.view.View>(R.id.list_prev).setOnClickListener { turn(-1) }
                    view.findViewById<android.view.View>(R.id.list_next).setOnClickListener { turn(+1) }
                    val list = view.findViewById<ViewGroup>(R.id.list_in)
                    val shown = chunks.getOrNull(page[0]).orEmpty()
                    for (r in 0 until list.childCount) {
                        val id = shown.getOrNull(r)?.first?.id ?: continue
                        val row = list.getChildAt(r)
                        row.setOnClickListener { onRow(id) }
                    }
                }
                show(ShortcutListWidget.preview(ctx, style, parts, live, rows, page[0]))
            },
        )
    }
}
