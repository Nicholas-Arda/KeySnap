package com.example.service

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.KeyMappingRepository
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptRepository
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import java.time.Duration
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

/**
 * A global pause stops every shortcut except one that can turn mapping back on ("Resume mapping"
 * or "Toggle mapping"); without that exception a key could pause mapping but never resume it.
 */
@RunWith(RobolectricTestRunner::class)
class PausedMappingTest {
    private lateinit var context: Context
    private val repo get() = ScriptRepository.getInstance(context)
    private val coordinator = HardwareKeyTriggerCoordinator

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RuntimeEnvironment.setQualifiers("+notnight")
        coordinator.initialize(context, KeyMappingRepository.getInstance(context))
        coordinator.setAdvancedBridgeActive(false)
        DeviceEventReceiver.register(context, DeviceEventReceiver())
        repo.scripts.value.forEach { repo.delete(it.id) }
        repo.setGloballyEnabled(true)
        idle()
    }

    @After fun tearDown() {
        repo.scripts.value.forEach { repo.delete(it.id) }
        repo.setGloballyEnabled(true)
        idle()
    }

    @Test fun pausedResumeKeyRunsAndResumesWhileOtherKeysStayOff() {
        repo.upsert(key("torch", 24, "toggle_flashlight"))
        repo.upsert(key("resume", 25, "resume_mapping"))
        repo.setGloballyEnabled(false)
        idle()
        val before = count()
        press(24)
        assertFalse(repo.globallyEnabled.value)
        assertEquals("a paused key does nothing", before, count())
        press(25)
        assertTrue(repo.globallyEnabled.value)
        assertEquals(before + 1, count())
    }

    @Test fun pausedResumeKeyIsMatchedOnTheRawKeyPath() {
        repo.upsert(key("torch", 24, "toggle_flashlight"))
        repo.upsert(key("resume", 25, "resume_mapping"))
        repo.setGloballyEnabled(false)
        idle()
        assertFalse(coordinator.dispatchKeyEvent(24, true, "test"))
        coordinator.dispatchKeyEvent(24, false, "test")
        assertTrue(coordinator.dispatchKeyEvent(25, true, "test"))
        coordinator.dispatchKeyEvent(25, false, "test")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(repo.globallyEnabled.value)
    }

    @Test fun toggleKeyPausesWhileRunningAndResumesWhilePaused() {
        repo.upsert(key("toggle", 26, "toggle_mapping"))
        idle()
        press(26)
        assertFalse(repo.globallyEnabled.value)
        press(26)
        assertTrue(repo.globallyEnabled.value)
    }

    @Test fun onlyTheResumingAutomationFiresWhilePaused() {
        repo.upsert(automation("auto-resume", "resume_mapping"))
        repo.upsert(automation("auto-torch", "toggle_flashlight"))
        repo.setGloballyEnabled(false)
        idle()
        val before = count()
        setNight(true)
        assertTrue(repo.globallyEnabled.value)
        // Resuming is not an edge to replay, so the torch automation stays quiet.
        assertEquals(before + 1, count())
    }

    private fun key(id: String, code: Int, action: String) = ShortcutScript(
        id, id, triggers = listOf(ScriptTrigger(listOf(code), TriggerPressType.SINGLE_PRESS)),
        actions = listOf(ScriptAction(action)), vibrateOnTrigger = false,
    )

    private fun automation(id: String, action: String) = ShortcutScript(
        id = id,
        name = id,
        triggers = emptyList(),
        actions = listOf(ScriptAction(action)),
        constraints = listOf(ScriptConstraint("dark_mode_on")),
        automation = true,
        vibrateOnTrigger = false,
    )

    private fun press(code: Int) {
        HardwareKeyTriggerCoordinator::class.java
            .getDeclaredMethod("handleGesture", Int::class.javaPrimitiveType, TriggerPressType::class.java)
            .apply { isAccessible = true }
            .invoke(coordinator, code, TriggerPressType.SINGLE_PRESS)
        idle()
    }

    private fun setNight(on: Boolean) {
        RuntimeEnvironment.setQualifiers(if (on) "+night" else "+notnight")
        (context as Application).onConfigurationChanged(context.resources.configuration)
        idle()
    }

    private fun count() = coordinator.triggerCount.value

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
