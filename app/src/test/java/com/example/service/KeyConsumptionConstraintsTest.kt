package com.example.service

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.media.AudioManager
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.example.data.KeyMappingRepository
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.data.ScriptRepository
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import com.example.data.shouldConsumeKey
import com.squareup.moshi.Moshi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUsageStatsManager

@RunWith(RobolectricTestRunner::class)
class KeyConsumptionConstraintsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val evaluator = AndroidConstraintEvaluator(context)

    @After fun resetProcessWideForegroundState() {
        AndroidConstraintEvaluator.rememberForegroundPackageForTest(null)
        ShadowUsageStatsManager.reset()
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED,
        )
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        ScriptRepository.getInstance(context).setGloballyEnabled(true)
    }

    @Test fun mediaConstraintActuallyLetsTheVolumeKeyThrough() {
        val script = ShortcutScript(
            "volume", "Volume", true, listOf(ScriptTrigger(listOf(25), TriggerPressType.SINGLE_PRESS)),
            listOf(ScriptAction.ToggleFlashlight), listOf(ScriptConstraint("no_media_playing")),
        )
        val audio = context.getSystemService(AudioManager::class.java)
        fun consume(): Boolean {
            val decision = evaluator.forDecision()
            return shouldConsumeKey(listOf(script), 25, true, true) { it.constraints.all(decision::isSatisfied) }
        }
        shadowOf(audio).setIsMusicActive(true)
        assertFalse(consume())
        shadowOf(audio).setIsMusicActive(false)
        assertTrue(consume())
    }

    @Test fun noAppOpenRequiresUsageAccessEvenOnKeyguard() {
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED,
        )
        val keyguard = shadowOf(context.getSystemService(KeyguardManager::class.java))
        keyguard.setKeyguardLocked(true)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("no_app_open")))
        keyguard.setKeyguardLocked(false)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("no_app_open")))
    }

    @Test fun revokingUsageAccessDisablesBothHomeAndKeyguardMatching() {
        prepareHomeScreen()
        shadowOf(context.getSystemService(UsageStatsManager::class.java))
            .addEvent(resumed("com.example.launcher", System.currentTimeMillis() - 10))
        val noAppOpen = ScriptConstraint("no_app_open")
        assertTrue(evaluator.forDecision().isSatisfied(noAppOpen))
        val appOps = shadowOf(context.getSystemService(AppOpsManager::class.java))
        appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED)
        assertFalse(evaluator.forDecision().isSatisfied(noAppOpen))
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        assertFalse(evaluator.forDecision().isSatisfied(noAppOpen))
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED)
        assertTrue(evaluator.forDecision().isSatisfied(noAppOpen))
    }

    @Test fun noAppOpenRequiresTheDefaultHomeAndRefreshesBetweenDecisions() {
        AndroidConstraintEvaluator.rememberForegroundPackageForTest(null)
        ShadowUsageStatsManager.reset()
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED,
        )
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        shadowOf(context.packageManager).addResolveInfoForIntent(homeIntent, ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.example.launcher"
                name = "com.example.launcher.Home"
            }
        })
        val usage = shadowOf(context.getSystemService(UsageStatsManager::class.java))
        val now = System.currentTimeMillis()
        usage.addEvent(ShadowUsageStatsManager.EventBuilder.buildEvent()
            .setEventType(UsageEvents.Event.ACTIVITY_RESUMED).setPackage("com.example.launcher").setTimeStamp(now - 5 * 60_000).build())
        val decision = evaluator.forDecision()
        assertTrue(decision.isSatisfied(ScriptConstraint("no_app_open")))
        usage.addEvent(ShadowUsageStatsManager.EventBuilder.buildEvent()
            .setEventType(UsageEvents.Event.ACTIVITY_RESUMED).setPackage("com.example.player").setTimeStamp(now - 10).build())
        // A different foreground predicate in the SAME decision must share the same query result.
        assertTrue(decision.isSatisfied(ScriptConstraint("app_in_foreground", mapOf("packageName" to "com.example.launcher"))))
        assertFalse(evaluator.forDecision().isSatisfied(ScriptConstraint("no_app_open")))
    }

    @Test fun foregroundLookbackRemembersTheLastPackageAndFallsBackOnce() {
        prepareHomeScreen()
        val now = System.currentTimeMillis()
        val usage = shadowOf(context.getSystemService(UsageStatsManager::class.java))
        usage.addEvent(resumed("com.example.launcher", now - 2 * 60 * 60_000))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("no_app_open")))

        ShadowUsageStatsManager.reset()
        usage.addEvent(resumed("com.example.player", now - 2 * 60 * 60_000))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("no_app_open")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("app_in_foreground", mapOf("packageName" to "com.example.launcher"))))

        usage.addEvent(resumed("com.example.player", now - 5_000))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("no_app_open")))

        AndroidConstraintEvaluator.rememberForegroundPackageForTest(null)
        ShadowUsageStatsManager.reset()
        usage.addEvent(resumed("com.example.launcher", now - 10 * 60 * 60_000))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("no_app_open")))
    }

    @Test fun coordinatorShouldConsumeKeyUsesTheDecisionEvaluator() {
        val repo = ScriptRepository.getInstance(context)
        val wasEnabled = repo.globallyEnabled.value
        val script = homeScript("consume-fake")
        var atHome = true
        var calls = 0
        val fake = object : ConstraintEvaluator {
            override fun isSatisfied(constraint: ScriptConstraint): Boolean {
                calls++
                return constraint.type == "no_app_open" && atHome
            }
            override fun forDecision(): ConstraintEvaluator = this
        }
        HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
        repo.setGloballyEnabled(true)
        repo.upsert(script)
        try {
            HardwareKeyTriggerCoordinator.setConstraintEvaluatorForTest(fake)
            assertTrue(HardwareKeyTriggerCoordinator.shouldConsumeKey(312))
            atHome = false
            assertFalse(HardwareKeyTriggerCoordinator.shouldConsumeKey(312))
            assertFalse(HardwareKeyTriggerCoordinator.shouldConsumeKey(24))
            assertEquals(2, calls)
        } finally {
            repo.delete(script.id)
            repo.setGloballyEnabled(wasEnabled)
            HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
        }
    }

    @Test fun coordinatorConsumesWhenTheDefaultHomeIsInFront() {
        prepareHomeScreen()
        val now = System.currentTimeMillis()
        val usage = shadowOf(context.getSystemService(UsageStatsManager::class.java))
        usage.addEvent(resumed("com.example.launcher", now - 20_000))
        val repo = ScriptRepository.getInstance(context)
        val wasEnabled = repo.globallyEnabled.value
        val script = homeScript("consume-home")
        HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
        repo.setGloballyEnabled(true)
        repo.upsert(script)
        try {
            assertTrue(HardwareKeyTriggerCoordinator.shouldConsumeKey(312))
            usage.addEvent(resumed("com.example.player", now - 1_000))
            assertFalse(HardwareKeyTriggerCoordinator.shouldConsumeKey(312))
        } finally {
            repo.delete(script.id)
            repo.setGloballyEnabled(wasEnabled)
            AndroidConstraintEvaluator.rememberForegroundPackageForTest(null)
            ShadowUsageStatsManager.reset()
        }
    }

    @Test fun accessibilityConsumptionSurvivesANewServiceInstance() {
        val tracker = HardwareKeyTriggerCoordinator.accessibilityKeyConsumption
        assertTrue(tracker.onEvent(312, 7, 50L, true, 0) { true })
        assertTrue(HardwareKeyTriggerCoordinator.accessibilityKeyConsumption.onEvent(312, 7, 50L, false, 0) { error("up") })
    }

    private fun homeScript(id: String) = ShortcutScript(
        id, "Home volume", true,
        listOf(ScriptTrigger(listOf(312), TriggerPressType.SINGLE_PRESS)),
        listOf(ScriptAction.ToggleFlashlight),
        listOf(ScriptConstraint("no_app_open")),
        consumeOriginalEvent = true,
    )

    private fun prepareHomeScreen() {
        AndroidConstraintEvaluator.rememberForegroundPackageForTest(null)
        ShadowUsageStatsManager.reset()
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED,
        )
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        shadowOf(context.packageManager).addResolveInfoForIntent(homeIntent, ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.example.launcher"
                name = "com.example.launcher.Home"
            }
        })
    }

    private fun resumed(packageName: String, at: Long) = ShadowUsageStatsManager.EventBuilder.buildEvent()
        .setEventType(UsageEvents.Event.ACTIVITY_RESUMED).setPackage(packageName).setTimeStamp(at).build()

    @Test fun alarmCameraAndCallActivitiesOccludeKeyguardUntilPausedOrStopped() {
        for (packageName in listOf("com.coloros.alarmclock", "com.android.camera", "com.android.dialer")) {
            for (endEvent in listOf(UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED)) {
                prepareHomeScreen()
                shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
                val usage = shadowOf(context.getSystemService(UsageStatsManager::class.java))
                val now = System.currentTimeMillis()
                val condition = ScriptConstraint("no_app_open")
                usage.addEvent(resumed(packageName, now - 20_000))
                assertFalse(evaluator.forDecision().isSatisfied(condition))
                // A different evaluator must see the same process-wide active activity state.
                assertFalse(AndroidConstraintEvaluator(context).isSatisfied(condition))
                usage.addEvent(ShadowUsageStatsManager.EventBuilder.buildEvent()
                    .setEventType(endEvent).setPackage(packageName).setTimeStamp(now - 10_000).build())
                assertTrue(evaluator.forDecision().isSatisfied(condition))
                assertTrue(evaluator.isSatisfied(ScriptConstraint("lock_screen_showing")))
                assertFalse(evaluator.isSatisfied(ScriptConstraint("lock_screen_not_showing")))
                // Replaying the short window must not resurrect an ended activity.
                assertTrue(evaluator.forDecision().isSatisfied(condition))
            }
        }
    }

    @Test fun pausedLauncherDoesNotCountAsUnlockedHome() {
        prepareHomeScreen()
        val now = System.currentTimeMillis()
        val usage = shadowOf(context.getSystemService(UsageStatsManager::class.java))
        usage.addEvent(resumed("com.example.launcher", now - 20_000))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("no_app_open")))
        usage.addEvent(ShadowUsageStatsManager.EventBuilder.buildEvent()
            .setEventType(UsageEvents.Event.ACTIVITY_PAUSED).setPackage("com.example.launcher").setTimeStamp(now - 10_000).build())
        assertFalse(evaluator.isSatisfied(ScriptConstraint("no_app_open")))
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("no_app_open")))
    }

    @Test fun pausingAnotherActivityInTheSamePackageDoesNotHideTheAlarm() {
        prepareHomeScreen()
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        val usage = shadowOf(context.getSystemService(UsageStatsManager::class.java))
        val now = System.currentTimeMillis()
        fun add(type: Int, activity: String, ago: Long) {
            usage.addEvent(ShadowUsageStatsManager.EventBuilder.buildEvent().setEventType(type)
                .setPackage("com.coloros.alarmclock").setClass(activity).setTimeStamp(now - ago).build())
        }
        add(UsageEvents.Event.ACTIVITY_RESUMED, "AlarmAlertFullScreen", 30_000)
        add(UsageEvents.Event.ACTIVITY_PAUSED, "AlarmSettings", 20_000)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("no_app_open")))
        add(UsageEvents.Event.ACTIVITY_STOPPED, "AlarmAlertFullScreen", 10_000)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("no_app_open")))
    }

    @Test fun ringingAndBothCallAudioModesAlwaysLetVolumeThrough() {
        prepareHomeScreen()
        val usage = shadowOf(context.getSystemService(UsageStatsManager::class.java))
        val audio = context.getSystemService(AudioManager::class.java)
        val keyguard = shadowOf(context.getSystemService(KeyguardManager::class.java))
        val condition = ScriptConstraint("no_app_open")
        for (locked in listOf(true, false)) {
            keyguard.setKeyguardLocked(locked)
            if (!locked) usage.addEvent(resumed("com.example.launcher", System.currentTimeMillis() - 10))
            for (mode in listOf(AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION)) {
                audio.mode = mode
                assertFalse(evaluator.forDecision().isSatisfied(condition))
                assertFalse(shouldConsumeKey(listOf(homeScript("call")), 312, true, true) {
                    it.constraints.all(evaluator.forDecision()::isSatisfied)
                })
            }
            audio.mode = AudioManager.MODE_NORMAL
            assertTrue(evaluator.forDecision().isSatisfied(condition))
        }
    }

    @Test fun homeComparisonRejectsUnknownAndAnotherLauncher() {
        assertTrue(isHomeInForeground("com.home", "com.home"))
        assertFalse(isHomeInForeground("com.other.home", "com.home"))
        assertFalse(isHomeInForeground(null, "com.home"))
        assertFalse(isHomeInForeground(null, null))
        assertFalse(isHomeInForeground("", ""))
    }

    @Test fun specialConditionsKeepTheExistingEvaluatorSemantics() {
        val conditions = listOf(
            ScriptConstraint("time", mapOf("start" to "bad")),
            ScriptConstraint("day_of_week"),
            ScriptConstraint(ScriptConstraintCatalog.AT_TIME),
            ScriptConstraint(ScriptConstraintCatalog.INTERVAL),
            ScriptConstraint(ScriptConstraintCatalog.NOTIFICATION_RECEIVED),
            ScriptConstraint(ScriptConstraintCatalog.RSS_NEW_ITEM),
            ScriptConstraint(ScriptConstraintCatalog.CALENDAR_EVENT_ACTIVE),
            ScriptConstraint(ScriptConstraintCatalog.COOLDOWN),
        )
        val decision = evaluator.forDecision()
        conditions.forEach { condition ->
            org.junit.Assert.assertEquals(condition.type, evaluator.isSatisfied(condition), decision.isSatisfied(condition))
        }
    }

    @Test fun repositoryCombinesHomeMediaAndCallConditionsWithoutChangingOverrides() {
        prepareHomeScreen()
        val repository = ScriptRepository(
            context.getSharedPreferences("consumption-test", Context.MODE_PRIVATE), Moshi.Builder().build(),
        )
        val script = ShortcutScript(
            "volume", "Volume", true, listOf(ScriptTrigger(listOf(25), TriggerPressType.LONG_PRESS)),
            listOf(ScriptAction.ToggleFlashlight),
            listOf(ScriptConstraint("no_app_open"), ScriptConstraint("no_media_playing"), ScriptConstraint("not_in_phone_call")),
            consumeOriginalEvent = true,
        )
        repository.upsert(script)
        val keyguard = shadowOf(context.getSystemService(KeyguardManager::class.java))
        val audio = context.getSystemService(AudioManager::class.java)
        fun consume(): Boolean {
            val decision = evaluator.forDecision()
            return repository.shouldConsume(25) { it.constraints.all(decision::isSatisfied) }
        }
        keyguard.setKeyguardLocked(true)
        shadowOf(audio).setIsMusicActive(false)
        audio.mode = AudioManager.MODE_NORMAL
        assertTrue(consume())
        shadowOf(audio).setIsMusicActive(true)
        assertFalse(consume())
        shadowOf(audio).setIsMusicActive(false)
        audio.mode = AudioManager.MODE_IN_CALL
        assertFalse(consume())
        audio.mode = AudioManager.MODE_NORMAL
        keyguard.setKeyguardLocked(false)
        assertFalse(consume())
        keyguard.setKeyguardLocked(true)
        repository.upsert(script.copy(consumeOriginalEvent = false))
        assertFalse(consume())
        repository.upsert(script)
        repository.setGloballyEnabled(false)
        assertFalse(consume())
    }
}
