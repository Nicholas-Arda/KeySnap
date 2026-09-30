package com.example.surface

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.StringRes
import com.example.data.AppLocale
import com.example.data.RunRecord
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import com.example.service.HardwareKeyTriggerCoordinator
import com.example.service.showSurfaceRunToast
import io.github.nicholasarda.keysnap.R

/**
 * A Quick Settings tile that runs the shortcut bound to its [slot]. Android shows one tile per
 * declared service, so the number of tiles is the number of subclasses in the manifest. An unbound
 * tile, or one whose shortcut was deleted, opens the picker instead; a long-press always does.
 */
abstract class ShortcutTileService(private val slot: Int) : TileService() {

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    private fun boundScript(): ShortcutScript? = SurfaceBindings.tileScript(this, slot)?.let { id ->
        ScriptRepository.getInstance(this).scripts.value.firstOrNull { it.id == id }
    }

    /** Called each time the panel opens, which is when a new binding or a rename shows up. */
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val script = boundScript()
        tile.label = script?.name ?: getString(labelRes(slot))
        tile.subtitle = getString(if (script == null) R.string.surface_choose_shortcut else R.string.app_name)
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val script = boundScript() ?: return openPicker()
        val app = applicationContext
        val run = Runnable {
            HardwareKeyTriggerCoordinator.runFromSurface(app, script.id, RunRecord.SOURCE_TILE) { showSurfaceRunToast(app, it) }
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
