package com.example.service

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.MainActivity
import com.example.data.AccessibilityStatus
import com.example.data.AppLocale
import com.example.viewmodel.MainViewModel
import io.github.nicholasarda.keysnap.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in physical-phone acceptance. The service APIs are real; no shadow or injected status. */
@RunWith(AndroidJUnit4::class)
class AccessibilityStatusDeviceTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val copyContext get() = AppLocale.wrap(context)
    private var scenario: ActivityScenario<MainActivity>? = null
    @Volatile private var watching = false
    private var watcher: Thread? = null

    private fun start(): UiAutomation {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("accessibilityPhone") == "true")
        assertTrue("Capture original preferences before phone tests", File(context.filesDir, "accessibility-phone-original-preferences.json").isFile)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        watching = true
        watcher = Thread {
            while (watching) {
                val root = automation.rootInActiveWindow
                if (root?.findAccessibilityNodeInfosByText("${context.packageName}.test")?.isNotEmpty() == true) {
                    var button = root.findAccessibilityNodeInfosByText("Allow only once").firstOrNull { it.text?.toString() == "Allow only once" }
                    while (button != null && !button.isClickable) button = button.parent
                    button?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                SystemClock.sleep(200)
            }
        }.apply { start() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        return automation
    }

    private fun permissionEnabled(): Boolean {
        val component = ComponentName(context, KeyMapperAccessibilityService::class.java)
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    @After fun close() {
        watching = false
        watcher?.join(1_000)
        scenario?.close()
    }

    @Test fun probeActualState() {
        val args = InstrumentationRegistry.getArguments()
        val automation = start()
        val expected = AccessibilityStatus.valueOf(args.getString("expectedAccessibility") ?: error("Expected phone state required"))
        val name = args.getString("captureName", expected.name).replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val samples = JSONArray()
        val start = SystemClock.elapsedRealtime()
        var actual = AccessibilityStatus.DISABLED
        do {
            scenario!!.onActivity { activity -> actual = ViewModelProvider(activity)[MainViewModel::class.java].accessibilityStatus.value }
            samples.put(JSONObject().put("elapsedMs", SystemClock.elapsedRealtime() - start).put("status", actual.name))
            if (actual == expected) break
            SystemClock.sleep(250)
        } while (SystemClock.elapsedRealtime() - start < 15_000)
        // Real application timers and service callbacks must drive the status; the test never sets it.
        assertEquals(expected, actual)
        assertEquals(expected != AccessibilityStatus.DISABLED, permissionEnabled())
        val connected = KeyMapperAccessibilityService.refreshConnectionStatus()
        assertEquals(expected == AccessibilityStatus.ACTIVE, connected)
        rule.onAllNodesWithTag("whats_new_got_it").fetchSemanticsNodes().firstOrNull()?.let {
            rule.onNodeWithTag("whats_new_got_it").performClick()
        }
        if (expected == AccessibilityStatus.NEEDS_RECONNECT) {
            rule.onNodeWithText(copyContext.getString(R.string.accessibility_reconnect_title)).assertIsDisplayed()
        }
        rule.onNodeWithText(copyContext.getString(R.string.home_system_status)).performScrollTo().performClick()
        rule.onAllNodesWithText(copyContext.getString(expected.label)).onLast().performScrollTo().assertIsDisplayed()
        val directory = context.getExternalFilesDir("accessibility-phone")!!
        directory.mkdirs()
        automation.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        File(directory, "$name.json").writeText(JSONObject()
            .put("package", context.packageName).put("expected", expected.name).put("actual", actual.name)
            .put("permissionEnabled", permissionEnabled()).put("frameworkConnected", connected)
            .put("samples", samples).put("dontSuppressAccessibilityServices", true).toString(2))
        instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "PHONE_ACCESSIBILITY_${expected.name}_VERIFIED\n") })
    }
}
