package com.example.service

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.KeyMappingRepository
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * A dark-mode automation has to fire on the uiMode edge. `ACTION_CONFIGURATION_CHANGED` arrives
 * before this process's `Resources` are updated, so the re-evaluation it triggers still reads the
 * old uiMode and misses the edge; the [android.content.ComponentCallbacks] registered by
 * [DeviceEventReceiver.register] is what runs once the new configuration is actually applied.
 */
@RunWith(RobolectricTestRunner::class)
class ConfigurationAutomationEdgeTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RuntimeEnvironment.setQualifiers("+notnight")
        HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
        DeviceEventReceiver.register(context, DeviceEventReceiver())
        ScriptRepository.getInstance(context).upsert(
            ShortcutScript(
                id = "qa-dark",
                name = "QA Dark",
                triggers = emptyList(),
                actions = listOf(ScriptAction("toggle_flashlight")),
                constraints = listOf(ScriptConstraint("dark_mode_on")),
                automation = true,
                vibrateOnTrigger = false,
            ),
        )
        idle()
    }

    @Test fun turningNightModeOnFiresTheAutomationOnTheConfigurationChange() {
        val before = HardwareKeyTriggerCoordinator.triggerCount.value
        RuntimeEnvironment.setQualifiers("+night")
        // What ActivityThread does once the new configuration is applied to the process:
        // Application.onConfigurationChanged fans out to every registered ComponentCallbacks.
        (context as Application).onConfigurationChanged(context.resources.configuration)
        idle()
        assertEquals(before + 1, HardwareKeyTriggerCoordinator.triggerCount.value)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
