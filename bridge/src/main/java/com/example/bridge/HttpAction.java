package com.example.bridge;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One implementation for both UIDs. No response bodies, exception messages or headers are logged. */
public final class HttpAction {
    private HttpAction() { }

    public static ActionTestResult execute(Map<String, String> params) {
        long start = System.nanoTime();
        HttpURLConnection connection = null;
        try {
            String method = params.getOrDefault("method", "").toUpperCase(Locale.ROOT);
            String url = params.getOrDefault("url", "");
            if (method.isEmpty() || url.isEmpty()) return ActionTestResult.outcome(false, start);
            // Validate everything before opening the connection.
            String contentType = ActionPayloads.contentType(params);
            List<Map.Entry<String, String>> headers = ActionPayloads.headers(params.get("headers"));
            if (contentType != null && headers.stream().anyMatch(h -> h.getKey().equalsIgnoreCase("Content-Type")
                    && !contentType.equalsIgnoreCase(h.getValue().trim()))) {
                // Two contradictory explicit choices must not silently rewrite either payload.
                return ActionTestResult.outcome(false, start);
            }
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            // A matching explicit line keeps its literal value, otherwise use the selector.
            if (contentType != null && headers.stream().noneMatch(h -> h.getKey().equalsIgnoreCase("Content-Type"))) {
                connection.setRequestProperty("Content-Type", contentType);
            }
            for (Map.Entry<String, String> header : headers) connection.addRequestProperty(header.getKey(), header.getValue());
            String body = params.get("body");
            if (body != null && !body.isEmpty() && ("POST".equals(method) || "PUT".equals(method))) {
                connection.setDoOutput(true);
                try (java.io.OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            return new ActionTestResult(status >= 200 && status < 400, null, status,
                    ActionTestResult.elapsed(start), false, Collections.emptyList());
        } catch (Exception ignored) {
            return ActionTestResult.outcome(false, start);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
