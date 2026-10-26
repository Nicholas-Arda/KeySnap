package com.example.service

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.KeyEventMapping
import com.example.data.KeyMappingRepository
import com.example.mode.ModeHooks
import com.example.data.RunLogRepository
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptRepository
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import com.example.service.bridge.BridgeRunResult
import java.time.Duration
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.ShadowUsageStatsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AssistantTriggerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val coordinator = HardwareKeyTriggerCoordinator
    private val key = KeyEventMapping.ASSISTANT_GESTURE

    private fun script(id: String = "assistant", code: Int = key, press: TriggerPressType = TriggerPressType.SINGLE_PRESS) = ShortcutScript(
        id, id, triggers = listOf(ScriptTrigger(listOf(code), press)),
        actions = listOf(ScriptAction("launch_device_assistant")), vibrateOnTrigger = false,
    )

    @Test fun coldLockedSessionInitializesEvaluatesModeAndDeduplicatesTheInvocation() = runTest {
        assumeTrue(ModeHooks.AVAILABLE)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = ScriptRepository.getInstance(context)
        val runsBefore = RunLogRepository.getInstance(context).runs.value.size
        try {
            repo.setGloballyEnabled(true)
            allowPlainLockScreen()
            // A prior test may have launched an assistant within the 2s gate window.
            ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
            repo.upsert(script().copy(constraints = listOf(
                ScriptConstraint("mode_is", mapOf("mode" to "1")), ScriptConstraint("no_app_open"),
            )))
            val job = coordinator.onAssistantGesture(context)
            val ran = mutableListOf<String>()
            HardwareKeyTriggerCoordinator::class.java.getDeclaredField("actionExecutor").apply { isAccessible = true }
                .set(coordinator, ScriptActionExecutor { ran += it.type; true })
            assertNull(coordinator.onAssistantGesture(context))
            job!!.join()
            advanceUntilIdle()
            assertEquals(listOf("launch_device_assistant"), ran)
            assertEquals(runsBefore + 1, RunLogRepository.getInstance(context).runs.value.size)
        } finally {
            repo.setGloballyEnabled(true)
            shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
            denyUsageAccess()
            coordinator.initialize(context, KeyMappingRepository.getInstance(context))
            Dispatchers.resetMain()
        }
    }

    @Test fun assistantExecutesWhileBridgeOwnsPhysicalKeysAndWritesOneHistoryRecord() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            coordinator.initialize(context, KeyMappingRepository.getInstance(context))
            val repo = ScriptRepository.getInstance(context)
            repo.setGloballyEnabled(true)
            val runsBefore = RunLogRepository.getInstance(context).runs.value.size
            repo.upsert(script())
            repo.upsert(script("physical", 26))
            val ran = mutableListOf<String>()
            HardwareKeyTriggerCoordinator::class.java.getDeclaredField("actionExecutor").apply { isAccessible = true }
                .set(coordinator, ScriptActionExecutor { ran += it.type; true })
            coordinator.setAdvancedBridgeActive(true)
            val gesture = HardwareKeyTriggerCoordinator::class.java.getDeclaredMethod("handleGesture", Int::class.javaPrimitiveType, TriggerPressType::class.java)
                .apply { isAccessible = true }
            gesture.invoke(coordinator, 26, TriggerPressType.SINGLE_PRESS)
            gesture.invoke(coordinator, key, TriggerPressType.SINGLE_PRESS)
            advanceUntilIdle()
            assertEquals(listOf("launch_device_assistant"), ran)
            val runs = RunLogRepository.getInstance(context).runs.value
            assertEquals(runsBefore + 1, runs.size)
            val run = runs.first()
            assertEquals(key, run.keyCode)
            assertEquals("SINGLE_PRESS", run.pressType)
            assertTrue(run.actions.single().ok)
        } finally {
            ScriptRepository.getInstance(context).setGloballyEnabled(true)
            coordinator.setAdvancedBridgeActive(false)
            coordinator.initialize(context, KeyMappingRepository.getInstance(context))
            Dispatchers.resetMain()
        }
    }

    @Test fun assistantStillObeysPauseConditionsAndSinglePressMatching() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = ScriptRepository.getInstance(context)
        val runsBefore = RunLogRepository.getInstance(context).runs.value.size
        try {
            coordinator.initialize(context, KeyMappingRepository.getInstance(context))
            repo.upsert(script().copy(constraints = listOf(ScriptConstraint("mode_is", mapOf("mode" to "2")), ScriptConstraint("no_app_open"))))
            repo.upsert(script("long", press = TriggerPressType.LONG_PRESS))
            coordinator.setConstraintEvaluatorForTest(object : ConstraintEvaluator {
                override fun isSatisfied(constraint: ScriptConstraint) = false
            })
            coordinator.setAdvancedBridgeActive(true)
            val gesture = HardwareKeyTriggerCoordinator::class.java.getDeclaredMethod("handleGesture", Int::class.javaPrimitiveType, TriggerPressType::class.java)
                .apply { isAccessible = true }
            gesture.invoke(coordinator, key, TriggerPressType.SINGLE_PRESS)
            advanceUntilIdle()
            assertEquals(runsBefore, RunLogRepository.getInstance(context).runs.value.size)
            repo.setGloballyEnabled(false)
            repo.upsert(script())
            gesture.invoke(coordinator, key, TriggerPressType.SINGLE_PRESS)
            advanceUntilIdle()
            assertEquals(runsBefore, RunLogRepository.getInstance(context).runs.value.size)
        } finally {
            repo.setGloballyEnabled(true)
            coordinator.setAdvancedBridgeActive(false)
            coordinator.initialize(context, KeyMappingRepository.getInstance(context))
            Dispatchers.resetMain()
        }
    }

    /** A plain lock screen: Usage Access granted, nothing resumed, keyguard showing, mode 1. */
    private fun allowPlainLockScreen() {
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED,
        )
        AndroidConstraintEvaluator.rememberForegroundPackageForTest(null)
        ShadowUsageStatsManager.reset()
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        ModeHooks.forgetState(context)
        ModeHooks.ensureState(context)
    }

    private fun denyUsageAccess() {
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED,
        )
        AndroidConstraintEvaluator.rememberForegroundPackageForTest(null)
    }

    @Test fun activeBridgeReceivesActionsAndAssistantLaunchStaysAppSideExactlyOnce() = runTest {
        val actions = listOf(ScriptAction("cycle_mode"), ScriptAction("launch_device_assistant"))
        val ran = mutableListOf<String>()
        var requests = 0
        val result = runChain(actions, ScriptActionExecutor { ran += it.type; true }) {
            requests++
            assertEquals(actions, it)
            BridgeRunResult(1, 2, listOf("cycle_mode" to true, "launch_device_assistant" to false))
        }
        assertEquals(1, requests)
        assertEquals(listOf("launch_device_assistant"), ran)
        assertEquals(listOf(null, true), result.appResults)
    }
}
