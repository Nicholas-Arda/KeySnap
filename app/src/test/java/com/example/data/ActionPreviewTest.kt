package com.example.data

import com.example.bridge.ActionPayloads
import com.example.bridge.BridgeConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActionPreviewTest {
    @Test fun shellContextReflectsTheCurrentPolicy() {
        assertEquals(ShellContext.ADVANCED, shellRunsIn(true))
        assertEquals(ShellContext.APP_LIMITED, shellRunsIn(false))
    }

    @Test fun everyNewParameterIsDeclaredOptional() {
        val expected = mapOf("http_request" to setOf("headers", "content_type"), "shell_command" to setOf("timeout_sec"), "send_intent" to setOf("package", "target"))
        expected.forEach { (type, keys) ->
            val specs = ScriptActionCatalog.descriptor(type)!!.parameters.filter { it.key in keys }
            assertEquals(keys, specs.map { it.key }.toSet())
            assertTrue(specs.all { !it.required })
            assertFalse(type in ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE)
        }
        assertEquals(listOf("5", "10", "30", "60"), ScriptActionCatalog.descriptor("shell_command")!!.parameters.single { it.key == "timeout_sec" }.choices)
    }

    @Test fun httpPreviewShowsCountAndTypeButNoValuesOrBody() {
        val action = ScriptAction("http_request", mapOf("method" to "POST", "url" to "https://example.com/h", "headers" to "Authorization:secret\nX-Key:other-secret", "content_type" to "json", "body" to "private-body"))
        assertEquals("POST https://example.com/h · 2 headers · JSON", previewCommand(action))
        assertEquals("POST https://example.com/h", previewCommand(action.copy(params = action.params - setOf("headers", "content_type"))))
        assertEquals("", previewCommand(action.copy(params = action.params + ("headers" to "malformed-secret"))))
    }

    @Test fun shellPreviewIsByteForByteTheUsersCommand() {
        val command = "  printf '%s' \"\$TOKEN\"\nexit 7  "
        assertEquals(command, previewCommand(ScriptAction("shell_command", mapOf("command" to command, "timeout_sec" to "60"))))
    }

    @Test fun intentPreviewUsesSharedArgumentsAndShellQuoting() {
        val action = ScriptAction("send_intent", mapOf("action" to "test.ACTION", "package" to "test.app", "extras" to "key= value 'quoted' "))
        assertEquals("am broadcast -a test.ACTION -p test.app --es key ' value '\\''quoted'\\'' '", previewCommand(action))
        val activity = action.copy(params = action.params + ("target" to "activity"))
        assertTrue(previewCommand(activity).startsWith("am start -a test.ACTION -f 0x10000000 -p test.app"))
        assertEquals(ActionPayloads.commandLine(ActionPayloads.intentCommand(activity.params)), previewCommand(activity))
    }

    @Test fun settingPreviewPreservesWhitespaceAndEmptyValues() {
        val action = ScriptAction("modify_setting", mapOf("namespace" to "system", "key" to "screen_off_timeout", "value" to "30000"))
        assertEquals("settings put system screen_off_timeout 30000", previewCommand(action))
        assertEquals("settings put system screen_off_timeout ''", previewCommand(action.copy(params = action.params + ("value" to ""))))
        assertEquals("", previewCommand(action.copy(params = action.params + ("namespace" to "invalid"))))
    }

    @Test fun extrasValidationRejectsSeparatorsBeforeSerialization() {
        assertNull(validateIntentExtras(listOf("key" to "  value  ", "empty" to "")))
        for (value in listOf("one;two", "one=two", "one;other=value")) {
            assertEquals("separator_in_extra", validateIntentExtras(listOf("key" to value)))
        }
        assertEquals("empty_key", validateIntentExtras(listOf("" to "value")))
        assertNull(validateIntentExtras("key=  value  ;empty="))
        for (raw in listOf("key=one=two", "key=one;two", "key=one;")) assertNotNull(validateIntentExtras(raw))
        // The legacy delimiter is inherently ambiguous: valid multi-row shortcuts stay valid.
        assertNull(validateIntentExtras("one=value;two=value"))
    }

    @Test fun bridgeConfigAndAppPreviewShareTheSameParameters() {
        val action = BridgeConfig.parseActions("""{"actions":[{"type":"send_intent","params":{"action":"test.ACTION","target":"activity","package":"test.app","extras":"key=  literal  "}}]}""").single()
        assertEquals(ActionPayloads.previewCommand(action.type, action.params), previewCommand(ScriptAction(action.type, action.params)))
    }
}
