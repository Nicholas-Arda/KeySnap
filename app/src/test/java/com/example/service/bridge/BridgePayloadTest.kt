package com.example.service.bridge

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.BridgeConfig
import com.example.bridge.BridgeProtocol
import com.example.data.KeyMappingConfig
import com.example.data.ScriptAction
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Pins the app-to-bridge contract.
 *
 * The two run in different processes with no shared memory, so a config the app writes is only
 * useful if the bridge's own parser understands it. These tests write with the production writer
 * and read back with the production reader, so a change to either side that breaks the pairing
 * fails here instead of silently disabling Advanced Mode on a device.
 */
@RunWith(RobolectricTestRunner::class)
class BridgePayloadTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun usageAccessGrantAndRevocationReachTheBridgeConfig() {
        val payload = BridgePayload(context)
        val appOps = shadowOf(context.getSystemService(AppOpsManager::class.java))
        for (mode in listOf(AppOpsManager.MODE_DEFAULT, AppOpsManager.MODE_ALLOWED, AppOpsManager.MODE_IGNORED)) {
            appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, mode)
            assertTrue(payload.writeConfig(listOf(flashlightScript()), true, KeyMappingConfig()))
            assertEquals(mode == AppOpsManager.MODE_ALLOWED, BridgeConfig.read(payload.configFile()!!).foregroundReadable)
        }
    }

    @Test fun negationFlagReachesTheBridgeParserUnchanged() {
        val payload = BridgePayload(context)
        val constraints = listOf(com.example.data.ScriptConstraint("screen_on",mapOf("negate" to "true")),com.example.data.ScriptConstraint("wifi_off"))
        assertTrue(payload.writeConfig(listOf(flashlightScript().copy(constraints = constraints)),true,KeyMappingConfig()))
        val back = BridgeConfig.read(payload.configFile()!!).scripts.single().constraints
        assertEquals(constraints.map { it.params },back.map { it.params })
    }

    @Test fun sensitiveAppsReachTheBridgeWithTheirThreeSwitches() {
        val repo = com.example.data.SensitiveAppsRepository.getInstance(context)
        repo.store.value.apps.forEach { repo.remove(it.packageName) }
        repo.updateSettings { it.copy(masterEnabled = true) }
        repo.add("com.bankid", "BankID")
        repo.update(com.example.data.SensitiveApp("com.bankid", "BankID", hideDeveloperOptions = true, hideAccessibility = false, hideWirelessDebugging = true))
        repo.add("com.wifionly", "Wifi only")
        repo.update(com.example.data.SensitiveApp("com.wifionly", "Wifi only", hideDeveloperOptions = false, hideAccessibility = false, hideWirelessDebugging = true))
        repo.add("com.bank", "Bank")
        try {
            val payload = BridgePayload(context)
            assertTrue(payload.writeConfig(listOf(flashlightScript()), true, KeyMappingConfig()))
            val apps = BridgeConfig.read(payload.configFile()!!).sensitiveApps
            // DEV = 1, A11Y = 2, WIFI = 4 (com.example.bridge.SensitiveApps).
            assertEquals(mapOf("com.bankid" to (1 or 4), "com.wifionly" to 4, "com.bank" to (1 or 2)), apps)
        } finally {
            repo.store.value.apps.forEach { repo.remove(it.packageName) }
        }
    }

    private fun flashlightScript(
        keyCode: Int = 25,
        pressType: TriggerPressType = TriggerPressType.DOUBLE_PRESS,
        enabled: Boolean = true,
    ) = ShortcutScript(
        id = "script-1",
        name = "Flashlight mapping",
        enabled = enabled,
        triggers = listOf(ScriptTrigger(listOf(keyCode), pressType)),
        actions = listOf(ScriptAction.ToggleFlashlight),
    )

    @Test
    fun writtenConfigIsReadableByTheBridgeParser() {
        val payload = BridgePayload(context)
        val written = payload.writeConfig(
            scripts = listOf(flashlightScript()),
            globallyEnabled = true,
            config = KeyMappingConfig(doublePressWindowMs = 300L, longPressThresholdMs = 500L),
        )
        assertTrue(written)

        val parsed = BridgeConfig.read(payload.configFile()!!)
        assertEquals(payload.controlPort, parsed.port)
        assertEquals(payload.controlToken, parsed.token)
        assertEquals(300L, parsed.doublePressWindowMs)
        assertEquals(500L, parsed.longPressThresholdMs)
        assertEquals(1, parsed.scripts.size)
        assertEquals(BridgeConfig.ACTION_TOGGLE_FLASHLIGHT, parsed.scripts[0].actions[0].type)
    }

    @Test
    fun pressTypeAndKeyCodeSurviveTheRoundTrip() {
        val payload = BridgePayload(context)
        payload.writeConfig(
            listOf(flashlightScript(keyCode = 25, pressType = TriggerPressType.LONG_PRESS)),
            globallyEnabled = true,
            config = KeyMappingConfig(),
        )
        val parsed = BridgeConfig.read(payload.configFile()!!)

        assertTrue(parsed.isMappedKey(25))
        assertFalse(parsed.isMappedKey(24))
        assertEquals(1, parsed.scriptsFor(25, BridgeConfig.PRESS_LONG).size)
        // A press type the user did not configure must not fire the script.
        assertEquals(0, parsed.scriptsFor(25, BridgeConfig.PRESS_DOUBLE).size)
    }

    @Test
    fun pausedGlobalMappingDisablesEveryScript() {
        val payload = BridgePayload(context)
        payload.writeConfig(listOf(flashlightScript()), globallyEnabled = false, config = KeyMappingConfig())
        val parsed = BridgeConfig.read(payload.configFile()!!)

        assertFalse(parsed.isMappedKey(25))
        assertEquals(0, parsed.scriptsFor(25, BridgeConfig.PRESS_DOUBLE).size)
    }

    @Test
    fun pausedScriptDisablesOnlyThatScript() {
        val payload = BridgePayload(context)
        val disabled = flashlightScript(enabled = false).copy(id = "disabled")
        val active = flashlightScript(keyCode = 24).copy(id = "active")
        payload.writeConfig(listOf(disabled, active), globallyEnabled = true, config = KeyMappingConfig())
        val parsed = BridgeConfig.read(payload.configFile()!!)

        assertFalse(parsed.isMappedKey(25))
        assertTrue(parsed.isMappedKey(24))
        assertEquals(1, parsed.scriptsFor(24, BridgeConfig.PRESS_DOUBLE).size)
    }

    @Test
    fun controlPortAndTokenAreStableAcrossInstances() {
        // The port is written into the config for the bridge to bind and used by the client to
        // reconnect; if these ever disagreed the app could never find its own bridge.
        val first = BridgePayload(context)
        val port = first.controlPort
        val token = first.controlToken

        val second = BridgePayload(context)
        assertEquals(port, second.controlPort)
        assertEquals(token, second.controlToken)
        assertTrue(port in 38_200 until 38_700)
        assertEquals(32, token.length)
    }

    /** Versions before protocol 63 sent their token in clear, so an update must never reuse it. */
    @Test
    fun aTokenFromBeforeProtocol63IsReplacedAndDeleted() {
        val old = "fedcba9876543210fedcba9876543210"
        val prefs = context.getSharedPreferences("arda_mapper_bridge_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("control_token", old).commit()

        val token = BridgePayload(context).controlToken
        assertNotEquals(old, token)
        assertEquals(32, token.length)
        assertEquals(token, prefs.getString("control_token_v63", null))
        assertFalse(prefs.contains("control_token"))
    }

    /** start.sh kills a leftover pre-63 bridge of this build before it copies the dex that bridge has open. */
    @Test
    fun theStartScriptKillsALeftoverBridgeBeforeCopyingTheDex() {
        val payload = BridgePayload(context)
        assertTrue(payload.install())
        val script = payload.startScriptFile()!!.readText()

        val kill = script.indexOf(BridgePayload.KILL_LEFTOVER_BRIDGE)
        assertTrue(script.indexOf("chmod 700 \"\$BASE\"") in 0 until kill)
        assertTrue(kill < script.indexOf("\ncp "))
    }

    /**
     * The leftover check under a real POSIX sh, with stand-ins whose command line does or does not
     * carry the bridge's name; the legacy pid file is gone afterwards either way. Skipped where no `sh`
     * with a /proc can run (the device pass covers toybox).
     */
    @Test
    fun theLeftoverCheckKillsOnlyALiveBridgeAndNeverStopsTheScript() {
        assumeTrue("no sh with /proc here", runCatching { sh("[ -r /proc/\$\$/cmdline ]").first == 0 }.getOrDefault(false))
        val base = createTempDirectory("bridge-base").toFile().absolutePath.replace('\\', '/')
        val pidFile = "\"\$BASE/${BridgeProtocol.LEGACY_PID_NAME}\""
        fun standIn(name: String) = "sh -c 'while :; do sleep 1; done' $name >/dev/null 2>&1 &\nV=\$!\necho \$V > $pidFile\n"
        fun run(setup: String, verdict: String = ""): String {
            val (exit, out) = sh("BASE='$base'\nrm -f $pidFile\n$setup${BridgePayload.KILL_LEFTOVER_BRIDGE}[ -e $pidFile ] && echo pid-left\necho after\n$verdict")
            assertEquals(out, 0, exit)
            assertTrue(out, out.contains("after"))
            assertFalse(out, out.contains("pid-left"))
            return out
        }
        // 137: the check killed the stand-in (SIGKILL); 143: it was still alive for this SIGTERM.
        val reap = "kill \$V 2>/dev/null\nwait \$V\necho \"status=\$?\"\n"

        run(setup = "")
        run(setup = ": > $pidFile\n")
        for (garbage in listOf("12ab", "-1", "1 2", "\$(echo 1)", "999999999")) run(setup = "printf '%s' '$garbage' > $pidFile\n")
        val other = run(standIn("some_other_process"), verdict = reap)
        assertTrue(other, other.contains("status=143"))
        val bridge = run(standIn(BridgeProtocol.NICE_NAME), verdict = reap)
        assertTrue(bridge, bridge.contains("status=137"))
    }

    /** Protocol 63 writes no pid file, so start.sh's kill can never reach a bridge another start.sh spawned. */
    @Test
    fun noBridgeSourceWritesThePidFile() {
        val sources = listOf("../bridge/src", "../pro/bridge/src").map(::File).flatMap { root ->
            root.walk().filter { it.extension == "java" && it.name != "BridgeProtocol.java" }.toList()
        }
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            assertFalse(file.path, "LEGACY_PID_NAME" in text || BridgeProtocol.LEGACY_PID_NAME in text)
        }
    }

    private fun sh(script: String): Pair<Int, String> {
        val process = ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        return process.waitFor() to out
    }

    @Test
    fun parameterizedActionsAndPerTriggerTimingOverridesSurviveTheRoundTrip() {
        val payload = BridgePayload(context)
        val script = ShortcutScript(
            id = "script-2",
            name = "Brightness",
            enabled = true,
            triggers = listOf(ScriptTrigger(listOf(24), TriggerPressType.LONG_PRESS, doublePressWindowMs = 450L, longPressThresholdMs = 900L)),
            actions = listOf(com.example.data.ScriptAction("change_brightness", mapOf("value" to "3"))),
        )
        payload.writeConfig(listOf(script), globallyEnabled = true, config = KeyMappingConfig())
        val parsed = BridgeConfig.read(payload.configFile()!!)

        val parsedAction = parsed.scripts.single().actions.single()
        assertEquals("change_brightness", parsedAction.type)
        assertEquals("3", parsedAction.params["value"])
        // A trigger-level override must win over the config-wide default resolved for that key.
        assertEquals(450L, parsed.doublePressWindowFor(24))
        assertEquals(900L, parsed.longPressThresholdFor(24))
    }

    /** Automation conditions (a feed address, a calendar name) never reach the shell-readable file. */
    @Test
    fun keyLessScriptsAreNotWrittenToTheConfigFile() {
        val payload = BridgePayload(context)
        val automation = ShortcutScript(
            id = "automation",
            name = "Feed",
            triggers = emptyList(),
            actions = listOf(ScriptAction.ToggleFlashlight),
            constraints = listOf(
                com.example.data.ScriptConstraint("rss_new_item", mapOf("url" to "https://example.org/feed?token=s3cret")),
                com.example.data.ScriptConstraint("calendar_event_active", mapOf("calendar" to "me@work.example")),
            ),
            automation = true,
        )
        val surfaceOnly = automation.copy(id = "tile", automation = false, constraints = emptyList())
        payload.writeConfig(listOf(automation, surfaceOnly, flashlightScript()), globallyEnabled = true, config = KeyMappingConfig())

        val raw = payload.configFile()!!.readText()
        assertFalse(raw.contains("s3cret"))
        assertFalse(raw.contains("me@work.example"))
        assertFalse(raw.contains("\"automation\""))
        assertFalse(raw.contains("\"tile\""))
        assertEquals(listOf("script-1"), BridgeConfig.read(payload.configFile()!!).scripts.map { it.id })
    }

    @Test
    fun spawnCommandDetachesTheBridgeFromTheAdbSession() {
        val command = BridgePayload(context).spawnCommand()
        assertNotNull(command)
        // setsid gives the bridge its own session so closing the ADB stream cannot SIGHUP it,
        // and redirecting all three standard streams detaches it from the transport.
        assertTrue(command!!.contains("setsid"))
        assertTrue(command.contains("nohup"))
        assertTrue(command.contains("</dev/null"))
        assertTrue(command.contains(BridgeProtocol.SPAWN_MARKER))
    }

    private val personalActions = listOf(
        ScriptAction("toggle_flashlight"),
        ScriptAction(
            "send_message",
            mapOf("number" to "+905551234567", "contactName" to "Ayse", "text" to "secret hello", "packageName" to "com.chat.app"),
        ),
        ScriptAction("delay", mapOf("duration" to "250")),
        ScriptAction("reply_to_notification", mapOf("text" to "secret reply")),
    )

    private fun assertOnlyBridgeParamsKept(parsed: List<Pair<String, Map<String, String>>>) {
        assertEquals(listOf("toggle_flashlight", "send_message", "delay", "reply_to_notification"), parsed.map { it.first })
        assertTrue(parsed[1].second.isEmpty())
        assertTrue(parsed[3].second.isEmpty())
        assertEquals(mapOf("duration" to "250"), parsed[2].second)
    }

    @Test
    fun bridgeNeverRunsActionsKeepTheirSlotButNotTheirParamsInTheConfigFile() {
        val payload = BridgePayload(context)
        val script = ShortcutScript(
            id = "script-3",
            name = "Personal",
            enabled = true,
            triggers = listOf(ScriptTrigger(listOf(24), TriggerPressType.SINGLE_PRESS)),
            actions = personalActions,
        )
        payload.writeConfig(listOf(script), globallyEnabled = true, config = KeyMappingConfig())

        val raw = payload.configFile()!!.readText()
        assertFalse(raw.contains("secret"))
        assertFalse(raw.contains("+905551234567"))
        assertFalse(raw.contains("Ayse"))
        val actions = BridgeConfig.read(payload.configFile()!!).scripts.single().actions
        assertOnlyBridgeParamsKept(actions.map { it.type to it.params })
    }

    @Test
    fun bridgeNeverRunsActionsKeepTheirSlotButNotTheirParamsInARunRequest() {
        val body = org.json.JSONObject().put(
            "actions",
            org.json.JSONArray().apply { personalActions.forEach { put(it.toBridgeJson()) } },
        ).toString()
        assertFalse(body.contains("secret"))
        assertOnlyBridgeParamsKept(BridgeConfig.parseActions(body).map { it.type to it.params })
    }
}
