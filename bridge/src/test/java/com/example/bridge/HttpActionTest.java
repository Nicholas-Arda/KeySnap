package com.example.bridge;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class HttpActionTest {
    private static class Received {
        String method;
        String body;
        Map<String, List<String>> headers;
    }

    private Received request(Map<String, String> supplied, int status) throws Exception {
        AtomicReference<Received> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Received r = new Received();
            r.method = exchange.getRequestMethod();
            r.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            r.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            r.headers.putAll(exchange.getRequestHeaders());
            received.set(r);
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            Map<String, String> params = new HashMap<>(supplied);
            params.put("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/");
            ActionTestResult result = HttpAction.execute(params);
            assertEquals(Integer.valueOf(status), result.httpStatus);
            assertEquals(status < 400, result.ok);
            assertTrue(result.durationMs >= 0);
            assertTrue(result.output.isEmpty());
            return received.get();
        } finally {
            server.stop(0);
        }
    }

    @Test public void jsonAndUnicodeBodyAreSentVerbatimWithCustomAndDuplicateHeaders() throws Exception {
        String body = "  {\"text\":\"çığ\"}\n ";
        Received r = request(Map.of("method", "POST", "body", body, "content_type", "json",
                "headers", "Authorization:Bearer secret\nX-Test:first\nX-Test:second"), 201);
        assertEquals(body, r.body);
        assertEquals("application/json", r.headers.get("Content-Type").get(0));
        assertEquals("Bearer secret", r.headers.get("Authorization").get(0));
        assertEquals(Arrays.asList("first", "second"), r.headers.get("X-Test"));
    }

    @Test public void absentParamsKeepLegacyFormBodyBehavior() throws Exception {
        Received r = request(Map.of("method", "POST", "body", "a=1&b=two words"), 200);
        assertEquals("a=1&b=two words", r.body);
        assertEquals("application/x-www-form-urlencoded", r.headers.get("Content-Type").get(0));
        assertFalse(r.headers.containsKey("Authorization"));
    }

    @Test public void allContentTypesAndMatchingExplicitHeader() throws Exception {
        for (String type : List.of("none", "form", "text")) {
            Received r = request(Map.of("method", "PUT", "body", " literal ", "content_type", type), 200);
            if (type.equals("none")) assertNull(r.headers.get("Content-Type"));
            else assertEquals(type.equals("text") ? "text/plain" : "application/x-www-form-urlencoded", r.headers.get("Content-Type").get(0));
            assertEquals(" literal ", r.body);
        }
        Received r = request(Map.of("method", "POST", "body", "literal", "content_type", "json", "headers", "Content-Type:application/json"), 200);
        assertEquals(List.of("application/json"), r.headers.get("Content-Type"));
    }

    @Test public void contradictoryContentTypeFailsBeforeOpeningConnection() {
        ActionTestResult result = HttpAction.execute(Map.of("method", "POST", "url", "http://127.0.0.1:1/", "content_type", "json", "headers", "Content-Type:custom/type"));
        assertFalse(result.ok);
        assertNull(result.httpStatus);
    }

    @Test public void getAndDeleteIgnoreBodyAsBeforeAndErrorsExposeOnlyStatus() throws Exception {
        for (String method : List.of("GET", "DELETE")) {
            Received r = request(Map.of("method", method, "body", "not sent", "headers", "X-Test:value"), 403);
            assertEquals(method, r.method);
            assertEquals("", r.body);
        }
    }

    @Test public void invalidRequestsFailWithoutPayloadInResult() {
        ActionTestResult result = HttpAction.execute(Map.of("method", "POST", "url", "not a url secret", "headers", "Authorization:secret"));
        assertFalse(result.ok);
        assertNull(result.httpStatus);
        assertTrue(result.output.isEmpty());
        assertFalse(result.toString().contains("secret"));
    }
}
