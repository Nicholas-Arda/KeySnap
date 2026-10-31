package com.example.bridge;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import static org.junit.Assert.*;

public class ActionPayloadsTest {
    @Test public void timeoutDefaultsAndAllChoices() {
        assertEquals(10, ActionPayloads.timeoutSeconds(Collections.emptyMap()));
        for (String value : Arrays.asList("5", "10", "30", "60")) {
            assertEquals(Integer.parseInt(value), ActionPayloads.timeoutSeconds(Map.of("timeout_sec", value)));
        }
        for (String value : Arrays.asList("", "0", "11", "-1", " 10", "9999999999")) {
            assertThrows(IllegalArgumentException.class, () -> ActionPayloads.timeoutSeconds(Map.of("timeout_sec", value)));
        }
    }

    @Test public void headersSplitOnlyAtFirstColonAndNeverTrimValues() {
        var headers = ActionPayloads.headers("Authorization: Bearer abc:xyz  \r\nX-Empty:\nX-Tab:\tvalue\t\n");
        assertEquals(3, headers.size());
        assertEquals(" Bearer abc:xyz  ", headers.get(0).getValue());
        assertEquals("", headers.get(1).getValue());
        assertEquals("\tvalue\t", headers.get(2).getValue());
        assertTrue(ActionPayloads.headers(null).isEmpty());
        assertEquals(2, ActionPayloads.headers("X:1\nX:2").size());
    }

    @Test public void malformedHeadersDoNotEchoSecrets() {
        for (String header : Arrays.asList("secret", ":secret", "Bad Name:secret", "X:secret\rInjected:x")) {
            Exception error = assertThrows(IllegalArgumentException.class, () -> ActionPayloads.headers(header));
            assertFalse(error.getMessage().contains("secret"));
        }
    }

    @Test public void contentTypesAreOptionalAndStrict() {
        assertNull(ActionPayloads.contentType(Collections.emptyMap()));
        assertNull(ActionPayloads.contentType(Map.of("content_type", "none")));
        assertEquals("application/json", ActionPayloads.contentType(Map.of("content_type", "json")));
        assertEquals("application/x-www-form-urlencoded", ActionPayloads.contentType(Map.of("content_type", "form")));
        assertEquals("text/plain", ActionPayloads.contentType(Map.of("content_type", "text")));
        assertThrows(IllegalArgumentException.class, () -> ActionPayloads.contentType(Map.of("content_type", "JSON")));
    }

    @Test public void extrasAreLiteralAndMalformedRowsFailClosed() {
        var extras = ActionPayloads.intentExtras("key=  value  ;empty=; spaced key =x");
        assertEquals("  value  ", extras.get(0).getValue());
        assertEquals("", extras.get(1).getValue());
        assertEquals(" spaced key ", extras.get(2).getKey());
        for (String raw : Arrays.asList("x=a=b", "x=a;broken", "=value", "x=a;", ";x=a")) {
            assertThrows(IllegalArgumentException.class, () -> ActionPayloads.intentExtras(raw));
        }
        assertEquals("separator_in_extra", ActionPayloads.validateIntentExtra("key", "one;two=value"));
        assertNull(ActionPayloads.validateIntentExtra("key", " value "));
    }

    @Test public void intentDefaultPackageAndActivityUseTheSameLiteralArguments() {
        assertArrayEquals(new String[]{"am", "broadcast", "-a", "test.ACTION"},
                ActionPayloads.intentCommand(Map.of("action", "test.ACTION")));
        assertArrayEquals(new String[]{"am", "start", "-a", "test.ACTION", "-f", "0x10000000", "-d", "demo://x?a=b",
                        "-p", "test.app", "--es", "key", "  literal  "},
                ActionPayloads.intentCommand(Map.of("action", "test.ACTION", "target", "activity", "package", "test.app",
                        "uri", "demo://x?a=b", "extras", "key=  literal  ")));
        assertEquals("am broadcast -a test.ACTION -p test.app", ActionPayloads.previewCommand("send_intent",
                Map.of("action", "test.ACTION", "package", "test.app")));
        assertThrows(IllegalArgumentException.class, () -> ActionPayloads.isActivity(Map.of("target", "service")));
    }

    @Test public void previewsAreLiteralAndHttpNeverContainsHeaderValues() {
        String shell = "  printf '%s' \"$TOKEN\"\nexit 7  ";
        assertEquals(shell, ActionPayloads.previewCommand("shell_command", Map.of("command", shell)));
        assertEquals("POST https://example.org/h · 2 headers · JSON", ActionPayloads.previewCommand("http_request",
                Map.of("method", "POST", "url", "https://example.org/h", "content_type", "json", "headers", "Authorization:secret\nX:secret2")));
        assertEquals("settings put system key '  literal '\\''value  '", ActionPayloads.previewCommand("modify_setting",
                Map.of("namespace", "system", "key", "key", "value", "  literal 'value  ")));
    }

    @Test public void bridgeConfigRetainsEveryOptionalParamVerbatim() throws Exception {
        String json = "{\"actions\":[{\"type\":\"http_request\",\"params\":{\"headers\":\"X: secret  \",\"content_type\":\"json\"}},"
                + "{\"type\":\"shell_command\",\"params\":{\"timeout_sec\":\"60\"}},"
                + "{\"type\":\"send_intent\",\"params\":{\"package\":\"test.app\",\"target\":\"activity\",\"extras\":\"k= value \"}}]}";
        var actions = BridgeConfig.parseActions(json);
        assertEquals("X: secret  ", actions.get(0).params.get("headers"));
        assertEquals("application/json", ActionPayloads.contentType(actions.get(0).params));
        assertEquals(60, ActionPayloads.timeoutSeconds(actions.get(1).params));
        assertTrue(ActionPayloads.isActivity(actions.get(2).params));
        assertEquals("test.app", actions.get(2).params.get("package"));
        assertEquals(" value ", ActionPayloads.intentExtras(actions.get(2).params.get("extras")).get(0).getValue());
    }
}
