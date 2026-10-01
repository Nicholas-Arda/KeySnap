package com.example.data

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunLogTest {

    private val allGranted = RunEnvironment(accessibility = true, dndAccess = true, writeSettings = true)

    private fun record(scriptId: String, at: Long, ok: Boolean = true) = RunRecord(
        scriptId, at, RunRecord.SOURCE_KEY, 24, "SINGLE_PRESS",
        listOf(ActionResult("toggle_flashlight", ok, if (ok) null else RunFailure.FAILED.name)),
    )

    @Test fun appOnlyRunKeepsEachActionsResultAndExplainsTheFailure() {
        val actions = listOf(ScriptAction("toggle_flashlight"), ScriptAction("toggle_wifi"), ScriptAction("go_back"))
        val env = RunEnvironment(accessibility = false, dndAccess = true, writeSettings = true)

        val run = buildRunRecord("s", 1L, RunRecord.SOURCE_KEY, 24, "SINGLE_PRESS", actions, listOf(true, false, false), null, env)

        assertEquals(listOf(true, false, false), run.actions.map { it.ok })
        assertEquals(listOf(null, "ADVANCED_MODE_OFF", "ACCESSIBILITY_OFF"), run.actions.map { it.reason })
    }

    @Test fun bridgeRunTakesTheBridgesResultsForWhatItRanAndTheAppsForTheRest() {
        // take_screenshot is in UNSUPPORTED_BY_BRIDGE, so the app ran it; toggle_wifi was the bridge's.
        val actions = listOf(ScriptAction("toggle_wifi"), ScriptAction("take_screenshot"))
        val env = RunEnvironment(accessibility = false, dndAccess = true, writeSettings = true)

        val run = buildRunRecord(
            "s", 1L, RunRecord.SOURCE_KEY, 26, "DOUBLE_PRESS", actions,
            appResults = listOf(null, false),
            bridgeReport = listOf("toggle_wifi" to false, "take_screenshot" to false),
            env = env,
        )

        assertEquals(listOf(false, false), run.actions.map { it.ok })
        // The bridge gives no reason, and a prerequisite the app never needed must not be blamed.
        assertEquals(listOf("FAILED", "ACCESSIBILITY_OFF"), run.actions.map { it.reason })
    }

    @Test fun aBridgeReportForADifferentActionListIsNotMergedByPosition() {
        val actions = listOf(ScriptAction("toggle_wifi"), ScriptAction("take_screenshot"))
        val run = buildRunRecord(
            "s", 1L, RunRecord.SOURCE_KEY, 26, "SINGLE_PRESS", actions,
            appResults = listOf(null, true),
            bridgeReport = listOf("toggle_bluetooth" to false),
            env = allGranted,
        )
        assertEquals(listOf(true, true), run.actions.map { it.ok })
    }

    @Test fun anOlderBridgeWithNoReportCountsItsShareAsDone() {
        val actions = listOf(ScriptAction("toggle_wifi"), ScriptAction("take_screenshot"))
        val run = buildRunRecord("s", 1L, RunRecord.SOURCE_KEY, 26, "SINGLE_PRESS", actions, listOf(null, false), null, allGranted)
        assertEquals(listOf(true, false), run.actions.map { it.ok })
    }

    @Test fun permissionReasonsFollowWhatIsMissing() {
        val none = RunEnvironment(accessibility = false, dndAccess = false, writeSettings = false)
        assertEquals(RunFailure.DND_ACCESS_MISSING, failureReason("change_ringer_mode", none))
        assertEquals(RunFailure.WRITE_SETTINGS_MISSING, failureReason("change_brightness", none))
        assertEquals(RunFailure.FAILED, failureReason("change_brightness", allGranted))
        assertEquals(RunFailure.FAILED, failureReason("launch_app", none))
        assertEquals(RunFailure.ADVANCED_MODE_OFF, failureReason("shell_command", allGranted))
    }

    @Test fun aShortcutNotificationThatCouldNotPostBlamesBlockedNotifications() {
        assertEquals(RunFailure.NOTIFICATIONS_OFF, failureReason("show_notification", allGranted.copy(notifications = false)))
        assertEquals(RunFailure.FAILED, failureReason("show_notification", allGranted))
        // Only the action that posts one; nothing else needs notifications to run.
        assertEquals(RunFailure.FAILED, failureReason("launch_app", allGranted.copy(notifications = false)))
    }

    @Test fun aReplyWithoutNotificationAccessBlamesTheMissingAccess() {
        assertEquals(RunFailure.NOTIFICATION_ACCESS_MISSING, failureReason("reply_to_notification", allGranted.copy(notificationAccess = false)))
        // With access on, a reply that found no chat to answer is the generic failure.
        assertEquals(RunFailure.FAILED, failureReason("reply_to_notification", allGranted))
        assertEquals(RunFailure.FAILED, failureReason("launch_app", allGranted.copy(notificationAccess = false)))
    }

    @Test fun aFailureWhileAdvancedModeIsOnIsNeverBlamedOnAdvancedModeBeingOff() {
        // The app ran the whole chain although the bridge was up (its RUN request fell through).
        val env = allGranted.copy(advancedMode = true)
        val actions = listOf(ScriptAction("toggle_wifi"), ScriptAction("shell_command"))

        val run = buildRunRecord("s", 1L, RunRecord.SOURCE_AUTOMATION, null, null, actions, listOf(false, false), null, env)

        assertEquals(listOf("FAILED", "FAILED"), run.actions.map { it.reason })
    }

    /** The reasons must agree with the requirement badges the editor already shows for each action. */
    @Test fun reasonSetsCoverEveryActionTheEditorBadges() {
        ScriptActionCatalog.all.forEach { descriptor ->
            val type = descriptor.type
            val form = ActionEditorForms.forType(type) ?: return@forEach
            if (ActionEditorForms.needsAdvancedMode(type)) assertTrue(type, type in ADVANCED_MODE_TYPES)
            if (form.requirementRes == NOTIFICATION_POLICY_BADGE) assertTrue(type, type in DND_ACCESS_TYPES)
            if (form.requirementRes == WRITE_SETTINGS_BADGE) assertTrue(type, type in WRITE_SETTINGS_TYPES)
            if (form.requirementRes == NOTIFICATION_ACCESS_BADGE) assertTrue(type, type in NOTIFICATION_ACCESS_TYPES)
        }
    }

    @Test fun reasonSetsNameOnlyAvailableActionsAndNoAppRunActionIsBlamedOnAdvancedMode() {
        (ADVANCED_MODE_TYPES + ACCESSIBILITY_TYPES + DND_ACCESS_TYPES + WRITE_SETTINGS_TYPES + NOTIFICATION_TYPES + NOTIFICATION_ACCESS_TYPES).forEach {
            assertTrue(it, ScriptActionCatalog.isAvailable(it))
        }
        assertTrue((ADVANCED_MODE_TYPES intersect ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE).isEmpty())
    }

    @Test fun storedRecordHoldsNoActionArguments() {
        val actions = listOf(
            ScriptAction("http_request", mapOf("method" to "POST", "url" to "https://hooks.example/x?token=SECRET123", "body" to "hunter2")),
            ScriptAction("shell_command", mapOf("command" to "echo hunter2")),
            ScriptAction("input_text", mapOf("text" to "my password hunter2")),
        )
        val run = buildRunRecord("s", 1L, RunRecord.SOURCE_AUTOMATION, null, null, actions, listOf(false, false, false), null, allGranted)

        val json = Moshi.Builder().build().adapter(PersistedRunLog::class.java).toJson(PersistedRunLog(listOf(run)))

        listOf("SECRET123", "hunter2", "hooks.example", "echo").forEach { assertFalse(it, json.contains(it)) }
    }

    @Test fun aNoisyShortcutCannotPushOthersOut() {
        var log = listOf(record("quiet", 0L))
        repeat(50) { log = appendRun(log, record("noisy", it + 1L)) }

        assertEquals(MAX_RUNS_PER_SCRIPT, log.count { it.scriptId == "noisy" })
        assertEquals(1, log.count { it.scriptId == "quiet" })
        assertEquals(50L, log.first().at) // newest first
    }

    @Test fun theWholeLogIsCapped() {
        var log = emptyList<RunRecord>()
        repeat(MAX_RUNS + 10) { i -> log = appendRun(log, record("s$i", i.toLong())) }

        assertEquals(MAX_RUNS, log.size)
        assertEquals((MAX_RUNS + 9).toLong(), log.first().at)
    }

    @Test fun anUnknownReasonFromANewerBuildReadsAsFailed() {
        assertEquals(RunFailure.FAILED, RunFailure.from("SOMETHING_NEW"))
        assertEquals(RunFailure.FAILED, RunFailure.from(null))
        assertEquals(RunFailure.ACCESSIBILITY_OFF, RunFailure.from("ACCESSIBILITY_OFF"))
    }

    @Test fun onlyAShortcutsLatestRunDecidesWhetherItIsFlagged() {
        val log = listOf(record("a", 3L, ok = true), record("a", 2L, ok = false), record("b", 1L, ok = false))
        assertEquals(setOf("b"), latestFailedScriptIds(log))
    }
}
