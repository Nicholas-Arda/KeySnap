package com.example.bridge;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ephemeral result. No payload-bearing toString; output must never enter logs or persistence. */
public final class ActionTestResult {
    public final boolean ok;
    public final Integer exitCode;
    public final Integer httpStatus;
    public final long durationMs;
    public final boolean timedOut;
    public final List<String> output;

    public ActionTestResult(boolean ok, Integer exitCode, Integer httpStatus, long durationMs,
            boolean timedOut, List<String> output) {
        this.ok = ok;
        this.exitCode = exitCode;
        this.httpStatus = httpStatus;
        this.durationMs = durationMs;
        this.timedOut = timedOut;
        this.output = Collections.unmodifiableList(new ArrayList<>(output.subList(Math.max(0, output.size() - 20), output.size())));
    }

    public static ActionTestResult outcome(boolean ok, long startedNanos) {
        return new ActionTestResult(ok, null, null, elapsed(startedNanos), false, Collections.emptyList());
    }

    public static long elapsed(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    /** Only for the authenticated, requesting socket. Never broadcast or retain this JSON. */
    public String toWire() throws Exception {
        return new JSONObject().put("ok", ok).put("exitCode", exitCode).put("httpStatus", httpStatus)
                .put("durationMs", durationMs).put("timedOut", timedOut).put("output", new JSONArray(output)).toString();
    }

    public static ActionTestResult fromWire(String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        List<String> lines = new ArrayList<>();
        JSONArray array = obj.getJSONArray("output");
        for (int i = Math.max(0, array.length() - 20); i < array.length(); i++) lines.add(array.getString(i));
        return new ActionTestResult(obj.getBoolean("ok"), obj.has("exitCode") ? obj.getInt("exitCode") : null,
                obj.has("httpStatus") ? obj.getInt("httpStatus") : null, obj.getLong("durationMs"), obj.getBoolean("timedOut"), lines);
    }
}
