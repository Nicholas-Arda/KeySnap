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
        val text = encode(actions = listOf(reply), constraints = constraints, automation = true)
        val back = ok(text)
        assertEquals(listOf(reply - "chat"), back.actions)
        assertEquals(constraints, back.constraints)
        assertFalse(back.startsEnabled)
        assertTrue("reply_to_notification" in ScriptActionCatalog.REVIEWED_LITERALLY)
    }

    private operator fun ScriptAction.minus(key: String) = copy(params = params - key)

    /** Contact and chat names are third-party data: absent from the payload, and the import answers every chat, so it arrives off with the warning. */
    @Test fun aContactAndAChatStayOnThisPhone() {
        val reply = ScriptAction("reply_to_notification", mapOf("packageName" to "com.whatsapp", "chat" to "Annem", "text" to "On my way"))
        val constraints = listOf(ScriptConstraint("notification_received", mapOf("packageName" to "com.whatsapp", "contact" to "Ayse")))
        val text = encode(actions = listOf(reply), constraints = constraints, automation = true)
        assertFalse(text.contains("Annem"))
        assertFalse(text.contains("Ayse"))
        val back = ok(text)
        assertFalse(back.startsEnabled)
        assertTrue(ActionEditorForms.repliesToEveryChat(back.automation, back.constraints, back.actions))
    }

    @Test fun privilegedImportsNeverStartEnabled() {
        assertTrue(ok(encode()).startsEnabled)
        assertFalse(ok(encode(actions = listOf(torch, hook))).startsEnabled)
        ScriptActionCatalog.PRIVILEGED.forEach { type ->
            val shortcut = ImportedShortcut("n", false, TriggerPressType.SINGLE_PRESS, listOf(ScriptAction(type)), emptyList())
            assertFalse(type, shortcut.startsEnabled)
        }
    }

    /**
     * A message's contact is a third party's name and number: it never leaves this phone. The app
     * and the text travel, and the text is shown as written in the share and import review.
     */
    @Test fun aMessagesContactStaysOnThisPhone() {
        val message = ScriptAction(
            "send_message",
            mapOf("contactName" to "Annem", "number" to "+905551234567", "packageName" to "com.example.chat", "text" to "On my way"),
        )
        val json = encode(actions = listOf(message))
        assertFalse(json.contains("Annem"))
        assertFalse(json.contains("5551234567"))
        assertEquals(mapOf("packageName" to "com.example.chat", "text" to "On my way"), ok(json).actions.single().params)
        val crafted = json.replace("\"text\":\"On my way\"", "\"text\":\"On my way\",\"number\":\"123\"")
        assertEquals(mapOf("packageName" to "com.example.chat", "text" to "On my way"), ok(crafted).actions.single().params)
        // The share review prints what actually travels.
        assertEquals(mapOf("packageName" to "com.example.chat", "text" to "On my way"), ShortcutShare.portable(message).params)
        assertTrue("send_message" in ScriptActionCatalog.REVIEWED_LITERALLY)
        // An imported automation could follow it with taps on Send, so it arrives off.
        assertFalse(ok(json).startsEnabled)
    }

    /** "Send automatically" is the shortcut's own setting, not personal data, so it travels; the import still arrives off. */
    @Test fun aMessagesSendAutomaticallySwitchTravels() {
        val message = ScriptAction("send_message", mapOf("packageName" to "com.example.chat", "text" to "On my way", "autoSend" to "true"))
        val imported = ok(encode(actions = listOf(message)))
        assertEquals("true", imported.actions.single().params["autoSend"])
        assertFalse(imported.startsEnabled)
        // Only "true" or "false" is accepted.
        val crafted = encode(actions = listOf(message)).replace("\"autoSend\":\"true\"", "\"autoSend\":\"yes\"")
        assertTrue(ShortcutShare.decode(crafted) is ImportResult.Error)
    }

    /**
     * A feed address can carry a private token, and a crafted share must not make this phone poll a
     * host the user never typed, so it travels neither way; the recipient's editor asks for their own.
     */
    @Test fun aFeedAddressNeverTravels() {
        val feed = ScriptConstraint("rss_new_item", mapOf("url" to "https://example.org/feed?token=s3cret"))
        val json = encode(constraints = listOf(feed), automation = true)
        assertFalse(json.contains("s3cret"))
        assertEquals(emptyMap<String, String>(), ok(json).constraints.single().params)
        val crafted = json.replace("\"type\":\"rss_new_item\",\"params\":{}", "\"type\":\"rss_new_item\",\"params\":{\"url\":\"https://tracker.example\"}")
        assertTrue(crafted.contains("tracker"))
        assertEquals(emptyMap<String, String>(), ok(crafted).constraints.single().params)
    }

    /** A calendar's name is often an email address and stays on this phone; the title filter travels. */
    @Test fun aCalendarNameNeverTravelsButTheTitleFilterDoes() {
        val meeting = ScriptConstraint("calendar_event_active", mapOf("calendar" to "me@work.example", "title" to "Standup"))
        val json = encode(constraints = listOf(meeting), automation = true)
        assertFalse(json.contains("me@work.example"))
        assertEquals(mapOf("title" to "Standup"), ok(json).constraints.single().params)
        val crafted = json.replace("\"title\":\"Standup\"", "\"title\":\"Standup\",\"calendar\":\"other@example.org\"")
        assertTrue(crafted.contains("other@example.org"))
        assertEquals(mapOf("title" to "Standup"), ok(crafted).constraints.single().params)
    }

    /** The editor saves an automation-only condition only on an automation; an import may not either. */
    @Test fun anAutomationOnlyConditionOnAKeyShortcutIsRefused() {
        ScriptConstraintCatalog.AUTOMATION_ONLY.forEach { type ->
            val keyed = encode(constraints = listOf(ScriptConstraint(type, mapOf("packageName" to "com.example.chat"))))
            assertEquals(type, R.string.import_error_invalid, error(keyed))
            val surface = encode(constraints = listOf(ScriptConstraint(type)), surfaceOnly = true)
            assertEquals(type, R.string.import_error_invalid, error(surface))
            assertTrue(type, ok(encode(constraints = listOf(ScriptConstraint(type)), automation = true)).automation)
        }
    }
}
