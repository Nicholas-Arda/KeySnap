package com.example.service

import android.net.Uri
import com.example.data.ScriptAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.FileNotFoundException
import java.util.function.Supplier

/**
 * Failure paths only. A successful apply is device-verified (see Manual checks), because
 * Robolectric's WallpaperManager shadow is not a trustworthy stand-in for the system service.
 */
@RunWith(RobolectricTestRunner::class)
class SetWallpaperActionTest {

    private val context = RuntimeEnvironment.getApplication()
    private val executor = ScriptActionExecutor.android(context)

    private fun run(vararg params: Pair<String, String>): Boolean =
        runBlocking { executor.execute(listOf(ScriptAction("set_wallpaper", mapOf(*params)))) }.single()

    /** What a real ContentResolver throws for this URI; Robolectric's own answers any URI with a stream. */
    private fun opening(uri: String, failure: Exception) {
        shadowOf(context.contentResolver).registerInputStreamSupplier(Uri.parse(uri), Supplier { throw failure })
    }

    @Test fun anImageTheAppCanNoLongerReadFailsInsteadOfCrashing() {
        // The photo was deleted, or the backup was restored onto a phone that never had it.
        opening("content://com.example.gone/images/1", FileNotFoundException("gone"))
        assertFalse(run("imageUri" to "content://com.example.gone/images/1", "target" to "both"))
        // The persisted read grant was lost.
        opening("content://com.example.revoked/images/2", SecurityException("no grant"))
        assertFalse(run("imageUri" to "content://com.example.revoked/images/2", "target" to "home"))
    }

    @Test fun aMissingImageOrAnUnknownTargetFails() {
        assertFalse(run("target" to "home"))
        assertFalse(run("imageUri" to "content://com.example.gone/images/1", "target" to "sideways"))
    }
}
