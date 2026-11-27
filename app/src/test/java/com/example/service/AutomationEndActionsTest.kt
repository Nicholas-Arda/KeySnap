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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** "When it ends": an automation's second list runs once, on the true -> false edge of its If. */
@RunWith(RobolectricTestRunner::class)
class AutomationEndActionsTest {
    private lateinit var context: Context
    private val repo get() = ScriptRepository.getInstance(context)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RuntimeEnvironment.setQualifiers("+notnight")
        HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
        DeviceEventReceiver.register(context, DeviceEventReceiver())
    }

    @After fun tearDown() {
        repo.scripts.value.filter { it.id.startsWith("qa-end") }.forEach { repo.delete(it.id) }
    }

    private fun automation(id: String, end: List<ScriptAction>) = repo.upsert(
        ShortcutScript(
            id = id, name = id, triggers = emptyList(),
            actions = listOf(ScriptAction("toggle_flashlight")),
            constraints = listOf(ScriptConstraint("dark_mode_on")),
            automation = true, vibrateOnTrigger = false,
            endActions = end,
        ),
    ).also { idle() }

    private fun night(on: Boolean) {
        RuntimeEnvironment.setQualifiers(if (on) "+night" else "+notnight")
        (context as Application).onConfigurationChanged(context.resources.configuration)
        idle()
    }

    @Test fun theEndListRunsOnceWhenTheIfStops() {
        automation("qa-end", listOf(ScriptAction("toggle_flashlight")))
        val start = HardwareKeyTriggerCoordinator.triggerCount.value
        night(true)
        assertEquals(start + 1, HardwareKeyTriggerCoordinator.triggerCount.value)
        assertTrue("qa-end" in AutomationEnd.pendingIds(context))
        night(false)
        assertEquals(start + 2, HardwareKeyTriggerCoordinator.triggerCount.value)
        assertFalse("qa-end" in AutomationEnd.pendingIds(context))
        // Still false: nothing more is owed.
        (context as Application).onConfigurationChanged(context.resources.configuration)
        idle()
        assertEquals(start + 2, HardwareKeyTriggerCoordinator.triggerCount.value)
    }

    @Test fun withoutAnEndListNothingRunsWhenTheIfStops() {
        automation("qa-end-none", emptyList())
        val start = HardwareKeyTriggerCoordinator.triggerCount.value
        night(true)
        night(false)
        assertEquals(start + 1, HardwareKeyTriggerCoordinator.triggerCount.value)
        assertFalse("qa-end-none" in AutomationEnd.pendingIds(context))
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
