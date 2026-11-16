package com.example.surface

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.AppLocale
import com.example.data.RunRecord
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import com.example.service.HardwareKeyTriggerCoordinator
import com.example.service.showSurfaceRunToast
import com.example.surface.ShortcutWidgetProvider.Companion.px
import com.example.surface.ShortcutWidgetProvider.Companion.toBitmap
import com.example.ui.scripts.actionIconFor
import io.github.nicholasarda.keysnap.R

/**
 * A Quick Settings tile that runs, or switches on and off, the shortcut bound to its [slot]. Android shows one tile per
 * declared service, so the number of tiles is the number of subclasses in the manifest. An unbound
 * tile, or one whose shortcut was deleted, opens the picker instead; a long-press always does.
 * The edit panel offers only the next free slot; see [syncSlots].
 */
abstract class ShortcutTileService(private val slot: Int) : TileService() {

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    private fun boundScript(): ShortcutScript? = SurfaceBindings.tileScript(this, slot)?.let { id ->
        ScriptRepository.getInstance(this).scripts.value.firstOrNull { it.id == id }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        SurfaceBindings.setTileAdded(this, slot, true)
        syncSlots(this)
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        SurfaceBindings.setTileAdded(this, slot, false)
        syncSlots(this)
    }

    /** Called each time the panel opens, which is when a new binding or a rename shows up. */
    override fun onStartListening() {
        super.onStartListening()
        // Only a tile in Quick Settings listens, so this settles a slot an older version left unknown.
        if (!SurfaceBindings.tileAdded(this, slot)) {
            SurfaceBindings.setTileAdded(this, slot, true)
            syncSlots(this)
        }
        refreshTile()
    }

    /**
     * A run tile is never lit. A toggle tile is lit while its shortcut is on, and an "All shortcuts"
     * one while KeySnap is, the same as Android's own switches.
     */
    private fun refreshTile() {
        val tile = qsTile ?: return
        val all = SurfaceBindings.tileScript(this, slot) == SurfaceToggle.ALL
        val script = boundScript()
        val on = SurfaceBindings.tileToggles(this, slot) && (all || script != null) &&
            SurfaceToggle.isOn(this, if (all) SurfaceToggle.ALL else script!!.id) == true
        tile.label = when {
            all -> getString(R.string.app_name)
            else -> script?.name ?: getString(labelRes(slot))
        }
        tile.subtitle = when {
            all -> getString(if (on) R.string.scripts_toggle_state_on else R.string.home_status_paused)
            script == null -> getString(R.string.surface_choose_shortcut)
            SurfaceBindings.tileToggles(this, slot) ->
                getString(if (on) R.string.scripts_toggle_state_on else R.string.scripts_toggle_state_off)
            else -> getString(R.string.app_name)
        }
        tile.icon = iconFor(SurfaceBindings.tileIcon(this, slot), all, script)
            ?.let { Icon.createWithBitmap(it.toBitmap(px(TILE_ICON_DP), Color.WHITE)) }
            ?: Icon.createWithResource(this, R.drawable.ic_tile_bolt)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val all = SurfaceBindings.tileScript(this, slot) == SurfaceToggle.ALL
        val script = boundScript()
        if (!all && script == null) return openPicker()
        val app = applicationContext
        val run = if (SurfaceBindings.tileToggles(this, slot)) {
            Runnable {
                SurfaceToggle.flip(app, if (all) SurfaceToggle.ALL else script!!.id)
                    ?.let { Toast.makeText(app, it, Toast.LENGTH_SHORT).show() }
                refreshTile()
            }
        } else {
            Runnable {
                HardwareKeyTriggerCoordinator.runFromSurface(app, script!!.id, RunRecord.SOURCE_TILE) { showSurfaceRunToast(app, it) }
            }
        }
        // A tile is reachable from the lock screen. With a PIN, pattern or password, whoever holds
        // the phone unlocks it first, as they would to reach a home-screen widget.
        if (isLocked && isSecure) unlockAndRun(run) else run.run()
    }

    private fun openPicker() {
        val intent = ShortcutPickerActivity.tileIntent(this, slot).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                // Negative: widget picker intents use the (positive) widget id on the same activity.
                PendingIntent.getActivity(this, -slot, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            // The Intent overload throws only for apps targeting 34+ on 34+; this branch runs below 34.
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        const val SLOT_COUNT = 3
        private const val TILE_ICON_DP = 48

        /** The chosen icon, else the shortcut's first action's, else the power glyph for all shortcuts. */
        fun iconFor(chosen: String?, all: Boolean, script: ShortcutScript?): ImageVector? = when {
            chosen != null -> actionIconFor(chosen)
            all -> Icons.Outlined.PowerSettingsNew
            script != null -> actionIconFor(script.actions.firstOrNull()?.type.orEmpty())
            else -> null
        }

        /**
         * Android lists every declared tile in the edit panel, which showed three blank "KeySnap"
         * entries. Only added slots and the lowest free one stay enabled, so adding a tile offers the
         * next. An added slot is never disabled: that removes it from Quick Settings.
         * ponytail: a tile given a shortcut and removed before 1.3.1 stays listed (no remove callback ever reaches us).
         */
        fun syncSlots(context: Context) {
            val pm = context.packageManager
            SurfaceBindings.initTileSlots(context, 1..SLOT_COUNT)
            val offered = (1..SLOT_COUNT).firstOrNull { !SurfaceBindings.tileAdded(context, it) }
            for (slot in 1..SLOT_COUNT) {
                val show = SurfaceBindings.tileAdded(context, slot) || slot == offered
                val component = ComponentName(context, classFor(slot))
                val state = if (show) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                if (pm.getComponentEnabledSetting(component) != state) {
                    pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                }
            }
        }

        fun classFor(slot: Int): Class<out ShortcutTileService> = when (slot) {
            1 -> ShortcutTile1::class.java
            2 -> ShortcutTile2::class.java
            else -> ShortcutTile3::class.java
        }

        @StringRes
        fun labelRes(slot: Int): Int = when (slot) {
            1 -> R.string.tile_label_1
            2 -> R.string.tile_label_2
            else -> R.string.tile_label_3
        }

        /** The slot a tile long-press names, from the component the system passes; null for anything else. */
        fun slotFor(className: String?): Int? = when (className) {
            ShortcutTile1::class.java.name -> 1
            ShortcutTile2::class.java.name -> 2
            ShortcutTile3::class.java.name -> 3
            else -> null
        }
    }
}

class ShortcutTile1 : ShortcutTileService(1)
class ShortcutTile2 : ShortcutTileService(2)
class ShortcutTile3 : ShortcutTileService(3)
