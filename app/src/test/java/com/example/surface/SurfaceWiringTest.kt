package com.example.surface

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.nicholasarda.keysnap.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The widget provider has to be exported, so everything that can actually run a shortcut is kept
 * where only this app's own immutable PendingIntents reach it.
 */
@RunWith(RobolectricTestRunner::class)
class SurfaceWiringTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun eachWidgetGetsItsOwnImmutableRunIntent() {
        val first = ScriptRunReceiver.pendingIntent(context, 11, "script-a")
        val second = ScriptRunReceiver.pendingIntent(context, 12, "script-b")
        assertNotEquals(first, second)
        assertEquals("script-a", shadowOf(first).savedIntent.getStringExtra(ScriptRunReceiver.EXTRA_SCRIPT_ID))
        assertEquals("script-b", shadowOf(second).savedIntent.getStringExtra(ScriptRunReceiver.EXTRA_SCRIPT_ID))
        assertTrue(shadowOf(first).flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test fun onlyTheAppCanReachTheRunReceiver() {
        val info = context.packageManager.getReceiverInfo(ComponentName(context, ScriptRunReceiver::class.java), 0)
        assertFalse(info.exported)
    }

    @Test fun aLongPressNamesItsTileSlotAndNothingElseDoes() {
        assertEquals(1, ShortcutTileService.slotFor(ShortcutTile1::class.java.name))
        assertEquals(2, ShortcutTileService.slotFor(ShortcutTile2::class.java.name))
        assertEquals(3, ShortcutTileService.slotFor(ShortcutTile3::class.java.name))
        assertNull(ShortcutTileService.slotFor("com.other.app.SomeTile"))
        assertNull(ShortcutTileService.slotFor(null))
    }

    @Test fun aSpoofedDeleteKeepsALiveWidgetsBinding() {
        val live = shadowOf(AppWidgetManager.getInstance(context))
            .createWidget(ShortcutWidgetProvider::class.java, R.layout.widget_shortcut)
        val gone = live + 1000
        SurfaceBindings.bindWidget(context, live, "script-live")
        SurfaceBindings.bindWidget(context, gone, "script-gone")

        ShortcutWidgetProvider().onDeleted(context, intArrayOf(live, gone))

        assertEquals("script-live", SurfaceBindings.widgetScript(context, live))
        assertNull(SurfaceBindings.widgetScript(context, gone))
    }

    @Test fun everyTileIsProtectedBySystemBinding() {
        listOf(ShortcutTile1::class.java, ShortcutTile2::class.java, ShortcutTile3::class.java).forEach { tile ->
            val info = context.packageManager.getServiceInfo(ComponentName(context, tile), 0)
            assertEquals(tile.name, "android.permission.BIND_QUICK_SETTINGS_TILE", info.permission)
        }
    }
}
