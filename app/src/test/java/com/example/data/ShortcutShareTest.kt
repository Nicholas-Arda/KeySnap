package com.example.data

import io.github.nicholasarda.keysnap.R
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutShareTest {

    private val torch = ScriptAction("toggle_flashlight")
    private val hook = ScriptAction("http_request", mapOf("method" to "POST", "url" to "https://example.org/h", "body" to "a=1"))

    private fun encode(
        actions: List<ScriptAction> = listOf(torch),
        constraints: List<ScriptConstraint> = emptyList(),
        name: String = "Torch",
        automation: Boolean = false,
        pressType: TriggerPressType = TriggerPressType.DOUBLE_PRESS,
        surfaceOnly: Boolean = false,
    ) = ShortcutShare.encode(name, automation, pressType, actions, constraints, surfaceOnly)

    private fun ok(text: String): ImportedShortcut = when (val result = ShortcutShare.decode(text)) {
        is ImportResult.Ok -> result.shortcut
        is ImportResult.Error -> throw AssertionError("expected Ok, got error ${result.messageRes}")
    }

    private fun error(text: String): Int = (ShortcutShare.decode(text) as ImportResult.Error).messageRes

    @Test fun roundTripKeepsEverythingButTheKey() {
        val constraints = listOf(ScriptConstraint("time", mapOf("start" to "22:00", "end" to "07:00")))
        val back = ok(encode(actions = listOf(torch, hook), constraints = constraints, automation = true))
        assertEquals("Torch", back.name)
        assertTrue(back.automation)
        assertFalse(back.surfaceOnly)
        assertEquals(TriggerPressType.DOUBLE_PRESS, back.pressType)
        assertEquals(listOf(torch, hook), back.actions)
        assertEquals(constraints, back.constraints)
    }

    /** Plan 3's key-less "Widget or tile" shortcut stays one; its widget and tile bindings never travel. */
    @Test fun aWidgetOrTileShortcutRoundTrips() {
        val back = ok(encode(surfaceOnly = true))
        assertTrue(back.surfaceOnly)
        assertFalse(back.automation)
        assertFalse(ok(encode()).surfaceOnly)
    }

    @Test fun anAutomationCannotAlsoBeAWidgetOrTileShortcut() {
        val both = encode(automation = true).replace("\"surfaceOnly\":false", "\"surfaceOnly\":true")
        assertTrue(both.contains("\"automation\":true,\"surfaceOnly\":true"))
        assertEquals(R.string.import_error_invalid, error(both))
    }

    /** The picked image is a content:// grant on this phone; the recipient's editor asks for their own. */
    @Test fun aWallpaperImageStaysOnThisPhone() {
        val wallpaper = ScriptAction("set_wallpaper", mapOf("imageUri" to "content://media/picker/0/1234", "target" to "home"))
        val json = encode(actions = listOf(wallpaper))
        assertFalse(json.contains("content://"))
        val back = ok(json).actions.single()
        assertEquals(mapOf("target" to "home"), back.params)
        assertEquals(listOf("imageUri"), ActionEditorForms.missingRequired(back))
        // A crafted file that carries one anyway gets it dropped too.
        val crafted = json.replace("\"target\":\"home\"", "\"target\":\"home\",\"imageUri\":\"content://x/y\"")
        assertEquals(mapOf("target" to "home"), ok(crafted).actions.single().params)
    }

    @Test fun bluetoothDeviceIdentityStaysOnThisPhone() {
        val json = encode(
            automation = true,
            constraints = listOf(ScriptConstraint("bluetooth_device_connected", mapOf("address" to "00:11:22:33:44:55", "name" to "Car"))),
        )
        assertFalse(json.contains("00:11:22:33:44:55"))
        assertFalse(json.contains("\"Car\""))
        assertEquals(emptyMap<String, String>(), ok(json).constraints.single().params)
    }

    @Test fun survivesChatAppWrapping() {
        val wrapped = "\uFEFFKeySnap shortcut. Share this to KeySnap.\r\n\r\n" + encode() + "\r\n\nSent from my phone"
        assertEquals(listOf(torch), ok(wrapped).actions)
    }

    @Test fun rejectsTextThatIsNotAShortcut() {
        assertEquals(R.string.import_error_invalid, error("hello"))
        assertEquals(R.string.import_error_invalid, error("{\"foo\":1}"))
        assertEquals(R.string.import_error_invalid, error("{not json}"))
        assertEquals(R.string.import_error_invalid, error(encode().replace("\"keysnap\":1", "\"keysnap\":0")))
    }

    @Test fun aNewerExportSaysSoInsteadOfDroppingSteps() {
        assertEquals(R.string.import_error_newer, error(encode().replace("\"keysnap\":1", "\"keysnap\":2")))
        assertEquals(R.string.import_error_unsupported, error(encode(actions = listOf(torch, ScriptAction("teleport")))))
        // Coming-soon catalog entries are known ids that cannot run yet.
        assertEquals(R.string.import_error_unsupported, error(encode(actions = listOf(ScriptAction("toggle_nfc")))))
        assertEquals(R.string.import_error_unsupported, error(encode(constraints = listOf(ScriptConstraint("hinge_open")))))
    }

    @Test fun rejectsMalformedParameters() {
        assertEquals(R.string.import_error_invalid, error(encode(actions = listOf(hook.copy(params = hook.params + ("method" to "PATCH"))))))
        assertEquals(R.string.import_error_invalid, error(encode(actions = listOf(ScriptAction("tap_screen", mapOf("x" to "ten", "y" to "5"))))))
        assertEquals(R.string.import_error_invalid, error(encode(actions = emptyList())))
        assertEquals(R.string.import_error_invalid, error(encode().replace("DOUBLE_PRESS", "TRIPLE_PRESS")))
    }

    @Test fun nullsHiddenInsideListsAreRejectedNotCrashed() {
        assertEquals(R.string.import_error_invalid, error("""{"keysnap":1,"name":"n","actions":[null]}"""))
        assertEquals(
            R.string.import_error_invalid,
            error("""{"keysnap":1,"name":"n","actions":[{"type":"toggle_flashlight","params":{"k":null}}]}"""),
        )
    }

    @Test fun dropsParametersTheActionDoesNotDeclare() {
        val smuggled = hook.copy(params = hook.params + ("x-extra" to "anything"))
        assertEquals(hook, ok(encode(actions = listOf(smuggled))).actions.single())
    }

    @Test fun rejectsBidiControlsThatCouldDisguiseAPayload() {
        val disguised = ScriptAction("shell_command", mapOf("command" to "echo safe ‮fr- mr"))
        assertEquals(R.string.import_error_hidden, error(encode(actions = listOf(disguised))))
        assertEquals(R.string.import_error_hidden, error(encode(name = "Torch⁦")))
    }

    /** The review prints a payload "as written"; invisible or line-breaking characters would lie. */
    @Test fun rejectsInvisibleAndLineBreakingCharactersInParameters() {
        listOf("\u200B", "\u200E", "\u2060", "\uFEFF", "\r", "\u2028", "\u0085", "\u0007").forEach { hidden ->
            val disguised = ScriptAction("shell_command", mapOf("command" to "echo safe${hidden}rm"))
            assertEquals(hidden.codePointAt(0).toString(16), R.string.import_error_hidden, error(encode(actions = listOf(disguised))))
        }
        // Real multi-line text and tabs still import, and an emoji name keeps its joiner.
        val multiLine = ScriptAction("shell_command", mapOf("command" to "echo a\n\techo b"))
        assertEquals(listOf(multiLine), ok(encode(actions = listOf(multiLine))).actions)
        assertEquals("Family 👨\u200D👧", ok(encode(name = "Family 👨\u200D👧")).name)
    }

    /** A sound is a file or ringtone on this phone, or a URL a crafted share could make every press fetch. */
    @Test fun aSoundStaysOnThisPhone() {
        val sound = ScriptAction("play_sound", mapOf("uri" to "https://tracker.example/ping.mp3"))
        val json = encode(actions = listOf(sound))
        assertFalse(json.contains("tracker.example"))
        val crafted = json.replace("\"params\":{}", "\"params\":{\"uri\":\"https://tracker.example/ping.mp3\"}")
        assertTrue(crafted.contains("tracker.example"))
        assertEquals(emptyMap<String, String>(), ok(crafted).actions.single().params)
    }

    /** Typed text is shown and warned about, but it does not make an import arrive switched off. */
    @Test fun typedTextIsReviewedLiterallyButNotPrivileged() {
        assertTrue("input_text" in ScriptActionCatalog.REVIEWED_LITERALLY)
        assertTrue(ScriptActionCatalog.PRIVILEGED.all { it in ScriptActionCatalog.REVIEWED_LITERALLY })
        assertTrue(ok(encode(actions = listOf(ScriptAction("input_text", mapOf("text" to "hunter2"))))).startsEnabled)
    }

    @Test fun oversizedOrBinaryFilesAreRejectedWithoutReadingThemWhole() {
        val huge = ShortcutShare.readCapped(ByteArrayInputStream(ByteArray(10_000_000) { 'a'.code.toByte() }))
        assertEquals(ShortcutShare.MAX_LENGTH + 1, huge.length)
        assertEquals(R.string.import_error_too_large, error(huge))
        val binary = ShortcutShare.readCapped(ByteArrayInputStream(ByteArray(4096) { (it * 31).toByte() }))
        assertTrue(ShortcutShare.decode(binary) is ImportResult.Error)
        assertEquals(encode(), ShortcutShare.readCapped(ByteArrayInputStream(encode().toByteArray())))
    }

    @Test fun nameIsCleanedAndCapped() {
        assertEquals("Torch", ok(encode(name = "  Tor\u0007ch \n")).name)
        assertEquals(80, ok(encode(name = "x".repeat(500))).name.length)
    }

    /**
     * The notification condition, the cooldown and the reply travel intact. A reply sends someone
     * else's text to the recipient's own chats, so it always arrives off and is shown as written.
     */
    @Test fun notificationConditionsCooldownAndReplyRoundTrip() {
        val reply = ScriptAction("reply_to_notification", mapOf("packageName" to "com.whatsapp", "chat" to "Annem", "text" to "On my way"))
        val constraints = listOf(
            ScriptConstraint("notification_received", mapOf("packageName" to "com.whatsapp", "contains" to "annem")),
            ScriptConstraint("cooldown", mapOf("minutes" to "10")),
        )
        val back = ok(encode(actions = listOf(reply), constraints = constraints, automation = true))
        assertEquals(listOf(reply), back.actions)
        assertEquals(constraints, back.constraints)
        assertFalse(back.startsEnabled)
        assertTrue("reply_to_notification" in ScriptActionCatalog.REVIEWED_LITERALLY)
    }

    @Test fun privilegedImportsNeverStartEnabled() {
        assertTrue(ok(encode()).startsEnabled)
        assertFalse(ok(encode(actions = listOf(torch, hook))).startsEnabled)
        ScriptActionCatalog.PRIVILEGED.forEach { type ->
            val shortcut = ImportedShortcut("n", false, TriggerPressType.SINGLE_PRESS, listOf(ScriptAction(type)), emptyList())
            assertFalse(type, shortcut.startsEnabled)
        }
    }
}
