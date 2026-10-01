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
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The cooldown gates each edge; it is never part of the edge itself, so its expiry cannot fire
 * the automation, and an edge that lands inside it is used up rather than replayed later.
 */
@RunWith(RobolectricTestRunner::class)
class CooldownAutomationTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RuntimeEnvironment.setQualifiers("+notnight")
        HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
        DeviceEventReceiver.register(context, DeviceEventReceiver())
        // The repository and the coordinator's run history outlive a test class: start from no
        // scripts (another class's dark-mode automation would fire too), and give each test its own id.
        val repo = ScriptRepository.getInstance(context)
        repo.scripts.value.forEach { repo.delete(it.id) }
        idle()
    }

    @After fun tearDown() {
        listOf("qa-cooldown", "qa-cooldown-tap").forEach { ScriptRepository.getInstance(context).delete(it) }
        idle()
    }

    @Test fun aSecondEdgeInsideTheCooldownIsSkippedAndOneAfterItRuns() {
        upsert("qa-cooldown")
        val before = count()
        setNight(true)
        assertEquals(before + 1, count())
        setNight(false)
        setNight(true)
        assertEquals("inside 10 minutes: skipped", before + 1, count())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10))
        assertEquals("the cooldown expiring alone is not an edge", before + 1, count())
        setNight(false)
        setNight(true)
        assertEquals(before + 2, count())
    }

    /** A widget or tile tap is a run too: it starts the cooldown and is refused inside it. */
    @Test fun aSurfaceTapInsideTheCooldownIsRefused() {
        upsert("qa-cooldown-tap")
        setNight(true)
        var tap: SurfaceRun? = null
        HardwareKeyTriggerCoordinator.runFromSurface(context, "qa-cooldown-tap", "widget") { tap = it }
        idle()
        assertEquals(SurfaceRun.ConditionsUnmet("QA Cooldown"), tap)
    }

    private fun upsert(id: String) {
        ScriptRepository.getInstance(context).upsert(
            ShortcutScript(
                id = id,
                name = "QA Cooldown",
                triggers = emptyList(),
                actions = listOf(ScriptAction("toggle_flashlight")),
                constraints = listOf(ScriptConstraint("dark_mode_on"), ScriptConstraint("cooldown", mapOf("minutes" to "10"))),
                automation = true,
                vibrateOnTrigger = false,
            ),
        )
        idle()
    }

    private fun setNight(on: Boolean) {
        RuntimeEnvironment.setQualifiers(if (on) "+night" else "+notnight")
        (context as Application).onConfigurationChanged(context.resources.configuration)
        idle()
    }

    private fun count() = HardwareKeyTriggerCoordinator.triggerCount.value

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
