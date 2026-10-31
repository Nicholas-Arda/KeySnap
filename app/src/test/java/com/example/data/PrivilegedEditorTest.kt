package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivilegedEditorTest {

    @Test
    fun urlIssuesClassifyWithoutChangingTheStoredText() {
        assertEquals(UrlIssue.EMPTY, PrivilegedEditor.urlIssue(""))
        assertEquals(UrlIssue.EMPTY, PrivilegedEditor.urlIssue("  "))
        assertEquals(UrlIssue.NOT_WEB, PrivilegedEditor.urlIssue("ftp://files"))
        assertEquals(UrlIssue.PLAIN, PrivilegedEditor.urlIssue(" http://example.com "))
        assertNull(PrivilegedEditor.urlIssue(" HTTPS://example.com/a "))
        val values = mapOf("method" to "GET", "url" to " https://x ", "content_type" to "none")
        assertEquals(" https://x ", PrivilegedEditor.confirmedParams(PrivilegedEditor.HTTP, values)["url"])
    }

    @Test
    fun headersRoundTripKeepSpacesAndColonsAndMaskOnlyAuthorization() {
        val rows = listOf(HeaderRow("Authorization", " Bearer token"), HeaderRow("X-A", "a:b"))
        assertEquals(rows, PrivilegedEditor.headerRows(PrivilegedEditor.serializeHeaders(rows, false)))
        assertTrue(PrivilegedEditor.masksAuthorization("authorization"))
        assertFalse(PrivilegedEditor.masksAuthorization("X-Token"))
        assertTrue(PrivilegedEditor.needsBearer(listOf(HeaderRow("Authorization", "token"))))
        assertFalse(PrivilegedEditor.needsBearer(listOf(HeaderRow("Authorization", "Bearer token"))))
        assertFalse(PrivilegedEditor.needsBearer(listOf(HeaderRow("Authorization", ""))))
        val prefixed = PrivilegedEditor.withBearer(
            listOf(
                HeaderRow("Authorization", "token"),
                HeaderRow("Authorization", "Bearer token"),
                HeaderRow("X-Token", "token"),
            ),
        )
        assertEquals("Bearer token", prefixed[0].value)
        assertEquals("Bearer token", prefixed[1].value)
        assertEquals("token", prefixed[2].value)
    }

    @Test
    fun aHeaderThatWouldParseAsADifferentHeaderIsNotSent() {
        val raw = "not a header"
        assertEquals(listOf(HeaderRow("", raw)), PrivilegedEditor.headerRows(raw))
        assertEquals(raw, PrivilegedEditor.headersForRun(PrivilegedEditor.headerRows(raw), raw))
        assertNull(PrivilegedEditor.headersForRun(listOf(HeaderRow("a:b", "c")), null))
        assertNull(
            PrivilegedEditor.testAction(
                PrivilegedEditor.HTTP,
                mapOf("method" to "GET", "url" to "https://example.com"),
                listOf(HeaderRow("a:b", "c")),
                emptyList(),
                null,
                null,
            ),
        )
    }

    @Test
    fun contentTypeConflictIsOnlyABooleanAndGetDropsTheType() {
        assertTrue(PrivilegedEditor.contentTypeConflicts("json", listOf(HeaderRow("Content-Type", "text/plain"))))
        assertFalse(PrivilegedEditor.contentTypeConflicts("json", listOf(HeaderRow("Content-Type", " application/json "))))
        assertTrue(PrivilegedEditor.contentTypeInvalid("xml"))
        assertFalse(PrivilegedEditor.contentTypeInvalid("none"))
        assertFalse(PrivilegedEditor.contentTypeInvalid(null))
        val form = ActionEditorForms.forType(PrivilegedEditor.HTTP)!!
        val initial = ActionEditorForms.initialValues(form, mapOf("url" to "https://old.example/hook"))
        assertEquals("GET", initial["method"])
        assertEquals("none", initial["content_type"])
        assertEquals("https://old.example/hook", initial["url"])
        val saved = PrivilegedEditor.savedParams(PrivilegedEditor.HTTP, initial, emptyList(), emptyList())
        assertFalse(saved.containsKey("content_type"))
        assertEquals("{\"a\":1}", PrivilegedEditor.confirmedParams(PrivilegedEditor.HTTP, initial + mapOf("body" to "{\"a\":1}"))["body"])
        val post = mapOf("method" to "POST", "url" to "https://x", "content_type" to "xml")
        assertFalse(PrivilegedEditor.saveEnabled(PrivilegedEditor.HTTP, post))
        val clash = mapOf("method" to "POST", "url" to "https://x", "content_type" to "json")
        assertFalse(PrivilegedEditor.saveEnabled(PrivilegedEditor.HTTP, clash, listOf(HeaderRow("Content-Type", "text/plain"))))
        assertTrue(PrivilegedEditor.saveEnabled(PrivilegedEditor.HTTP, mapOf("method" to "GET", "url" to "https://x", "content_type" to "none")))
        assertFalse(PrivilegedEditor.saveEnabled(PrivilegedEditor.HTTP, mapOf("method" to "GET", "url" to "", "content_type" to "none")))
        assertFalse(PrivilegedEditor.saveEnabled(PrivilegedEditor.HTTP, mapOf("method" to "GET", "url" to "ftp://x", "content_type" to "none")))
    }

    @Test
    fun previewCountsHeadersAndNeverIncludesTheirValues() {
        val params = PrivilegedEditor.previewParams(
            PrivilegedEditor.HTTP,
            mapOf("method" to "POST", "url" to "https://example.com/h", "content_type" to "json"),
            listOf(HeaderRow("Authorization", "super-secret")),
            emptyList(),
        )
        val text = previewCommand(ScriptAction(PrivilegedEditor.HTTP, params))
        assertEquals("POST https://example.com/h · 1 header · JSON", text)
        assertFalse(text.contains("super-secret"))
        assertFalse(text.contains("Authorization"))
    }

    @Test
    fun shellReadyCommandsAreOnlyPrefilledPayloads() {
        val ready = PrivilegedEditor.shellReadyCommands()
        assertEquals(13, ready.size)
        assertEquals(ready.map { it.command }.distinct(), ready.map { it.command })
        assertTrue(ready.all { it.command in SuggestedShortcuts.PREFILLED_PAYLOADS })
        assertTrue(ready.none { it.command.contains("http://") || it.command.contains("https://") })
        assertTrue(ready.none { it.command.contains("screen_off_timeout") })
        assertEquals("  ls  ", PrivilegedEditor.confirmedParams(PrivilegedEditor.SHELL, mapOf("command" to "  ls  "))["command"])
        assertEquals("  ls  ", previewCommand(ScriptAction(PrivilegedEditor.SHELL, mapOf("command" to "  ls  "))))
    }

    @Test
    fun timeoutDisplaysTenUntilAChipIsTappedAndAnInvalidValueIsNotCoerced() {
        assertEquals("10", PrivilegedEditor.timeoutSelection(null))
        assertEquals("10", PrivilegedEditor.timeoutSelection(""))
        assertEquals("30", PrivilegedEditor.timeoutSelection("30"))
        assertNull(PrivilegedEditor.timeoutSelection("15"))
        assertFalse(PrivilegedEditor.timeoutInvalid(null))
        assertTrue(PrivilegedEditor.timeoutInvalid("15"))
        val form = ActionEditorForms.forType(PrivilegedEditor.SHELL)!!
        val initial = ActionEditorForms.initialValues(form, mapOf("command" to "ls"))
        assertFalse(initial.containsKey("timeout_sec"))
        assertFalse(PrivilegedEditor.confirmedParams(PrivilegedEditor.SHELL, initial).containsKey("timeout_sec"))
        assertFalse(PrivilegedEditor.confirmedParams(PrivilegedEditor.SHELL, initial + mapOf("timeout_sec" to "15")).containsKey("timeout_sec"))
        assertEquals("30", PrivilegedEditor.confirmedParams(PrivilegedEditor.SHELL, initial + mapOf("timeout_sec" to "30"))["timeout_sec"])
        assertFalse(PrivilegedEditor.saveEnabled(PrivilegedEditor.SHELL, mapOf("command" to "ls", "timeout_sec" to "15")))
        assertEquals(
            "15",
            PrivilegedEditor.testAction(
                PrivilegedEditor.SHELL,
                mapOf("command" to "ls", "timeout_sec" to "15"),
                emptyList(),
                emptyList(),
                null,
                null,
            )!!.params["timeout_sec"],
        )
    }

    @Test
    fun extrasKeepAnInvalidLegacyBlobAndRejectSeparatorsBeforeJoining() {
        assertEquals(listOf(ExtraRow("", "key=one=two")), PrivilegedEditor.extraRows("key=one=two"))
        assertEquals("key=one=two", PrivilegedEditor.extrasForRun(PrivilegedEditor.extraRows("key=one=two"), "key=one=two"))
        assertEquals("empty_key", PrivilegedEditor.extraError(ExtraRow("", "v")))
        assertEquals("separator_in_extra", PrivilegedEditor.extraError(ExtraRow("k", "a;b")))
        assertNull(PrivilegedEditor.extraError(ExtraRow("", "")))
        val rows = listOf(ExtraRow("name", "Gym"), ExtraRow("k", "a;b"))
        assertEquals("name=Gym", PrivilegedEditor.serializeExtras(rows, true))
        assertEquals("name=Gym;k=a;b", PrivilegedEditor.serializeExtras(rows, false))
        val values = mapOf("action" to "A", "target" to "broadcast")
        assertEquals("name=Gym", PrivilegedEditor.previewParams(PrivilegedEditor.INTENT, values, emptyList(), rows)["extras"])
        assertEquals(
            "name=Gym;k=a;b",
            PrivilegedEditor.testAction(PrivilegedEditor.INTENT, values, emptyList(), rows, null, null)!!.params["extras"],
        )
        assertFalse(PrivilegedEditor.saveEnabled(PrivilegedEditor.INTENT, values, extraRows = rows))
        assertTrue(PrivilegedEditor.saveEnabled(PrivilegedEditor.INTENT, values, extraRows = listOf(ExtraRow("name", "Gym"))))
        val form = ActionEditorForms.forType(PrivilegedEditor.INTENT)!!
        assertEquals("broadcast", ActionEditorForms.initialValues(form, mapOf("action" to "A"))["target"])
        assertTrue(PrivilegedEditor.targetInvalid("service"))
        assertFalse(PrivilegedEditor.confirmedParams(PrivilegedEditor.INTENT, values + mapOf("target" to "service")).containsKey("target"))
    }

    @Test
    fun settingProbeAndSuggestionsStayOnHarmlessKeys() {
        assertEquals(SettingProbe.APP, PrivilegedEditor.settingProbe("system", false))
        assertEquals(SettingProbe.BRIDGE, PrivilegedEditor.settingProbe("system", true))
        assertEquals(SettingProbe.BLOCKED, PrivilegedEditor.settingProbe("secure", false))
        assertEquals(SettingProbe.BRIDGE, PrivilegedEditor.settingProbe("secure", true))
        assertEquals(SettingProbe.BLOCKED, PrivilegedEditor.settingProbe("global", false))
        assertEquals(SettingProbe.BLOCKED, PrivilegedEditor.settingProbe("other", true))
        assertEquals("settings get system screen_off_timeout", PrivilegedEditor.bridgeGetCommand("system", "screen_off_timeout"))
        assertNull(PrivilegedEditor.bridgeGetCommand("system", "a b"))
        assertNull(PrivilegedEditor.bridgeGetCommand("system", "a;id"))
        assertNull(PrivilegedEditor.bridgeGetCommand("system", "$(id)"))
        assertNull(PrivilegedEditor.bridgeGetCommand("shell", "screen_off_timeout"))
        assertEquals(
            listOf(
                "screen_off_timeout",
                "font_scale",
                "screen_brightness",
                "window_animation_scale",
                "transition_animation_scale",
                "animator_duration_scale",
            ),
            PrivilegedEditor.suggestions.map { it.key },
        )
        assertTrue(PrivilegedEditor.suggestions.none { it.key.contains("adb") })
        assertTrue(PrivilegedEditor.matchingSuggestions("system", "adb") { it.key }.isEmpty())
        assertEquals(
            listOf("font_scale"),
            PrivilegedEditor.matchingSuggestions("secure", "font") { it.key }.map { it.key },
        )
        assertEquals(
            listOf("screen_off_timeout", "font_scale", "screen_brightness"),
            PrivilegedEditor.matchingSuggestions("system", "") { it.key }.map { it.key },
        )
    }

    @Test
    fun formatSecondsUsesOneDecimalUntilTenSeconds() {
        assertEquals("0.4 s", PrivilegedEditor.formatSeconds(400))
        assertEquals("0.2 s", PrivilegedEditor.formatSeconds(200))
        assertEquals("10 s", PrivilegedEditor.formatSeconds(10_000))
        assertEquals("11 s", PrivilegedEditor.formatSeconds(11_500))
    }
}
