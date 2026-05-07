package com.epam.sandbox.protocol;

import com.epam.sandbox.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire-frame helpers for the streaming sandbox protocol.
 *
 * Subtask EPMICMPCOD-452: input parsing.
 * Subtask EPMICMPCOD-455: result serialization.
 *
 * SRS §4.3:    "Test cases are fed iteratively."
 * SRS §6.2 step 2: "Result is streamed back to the Orchestrator."
 *
 * Frames (line-delimited JSON, one per line):
 *
 * <pre>
 * Orchestrator → Wrapper:
 *   {"type":"source",   "executionId":"...","className":"Solution","sourceCode":"..."}
 *   {"type":"testcase", "id":"tc1","stdin":"...","timeoutMs":3000}     // timeoutMs optional
 *   {"type":"end",      "executionId":"..."}
 *
 * Wrapper → Orchestrator:
 *   {"type":"compiled",       "executionId":"..."}
 *   {"type":"compile_error",  "executionId":"...","message":"..."}
 *   {"type":"result",         "id":"tc1","status":"OK|RUNTIME_ERROR|TIME_LIMIT_EXCEEDED",
 *                             "stdout":"...","stderr":"...","runtimeMs":12,
 *                             "errorMessage":"..."}      // present on RUNTIME_ERROR / TLE
 *   {"type":"ack",            "executionId":"..."}
 *   {"type":"protocol_error", "message":"..."}           // bad input; wrapper stays alive
 * </pre>
 */
public final class Frame {

    public enum Type {
        SOURCE, TESTCASE, END,                        // requests
        COMPILED, COMPILE_ERROR, RESULT, ACK,         // responses
        PROTOCOL_ERROR                                // out-of-band
    }

    public enum ResultStatus { OK, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED }

    private Frame() {}

    // ---------- Parsing ----------

    /**
     * Parse a single JSON line into a generic map and read its {@code type}.
     * Throws {@link Json.JsonException} on malformed JSON or missing/unknown type.
     */
    public static ParsedFrame parse(String line) {
        Map<String, Object> obj = Json.parseObject(line);
        Object t = obj.get("type");
        if (!(t instanceof String s)) {
            throw new Json.JsonException("Frame missing 'type'");
        }
        Type type;
        try {
            type = Type.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new Json.JsonException("Unknown frame type: " + s);
        }
        return new ParsedFrame(type, obj);
    }

    public static final class ParsedFrame {
        public final Type type;
        public final Map<String, Object> body;

        ParsedFrame(Type type, Map<String, Object> body) {
            this.type = type;
            this.body = body;
        }

        public String getString(String field) {
            Object v = body.get(field);
            return v instanceof String s ? s : null;
        }

        public long getLong(String field, long defaultValue) {
            Object v = body.get(field);
            if (v instanceof Number n) return n.longValue();
            if (v instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            }
            return defaultValue;
        }
    }

    // ---------- Serialisation (response frames) ----------

    public static String compiled(String executionId) {
        return Json.stringify(map("compiled", "executionId", executionId));
    }

    public static String compileError(String executionId, String message) {
        String safeMsg = (message == null || message.isEmpty()) ? "Compilation failed" : message;
        Map<String, Object> m = map("compile_error", "executionId", executionId);
        m.put("message", safeMsg);
        return Json.stringify(m);
    }

    public static String result(String id, ResultStatus status,
                                String stdout, String stderr,
                                long runtimeMs, String errorMessage) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "result");
        m.put("id", id);
        m.put("status", status.name());
        m.put("stdout", stdout == null ? "" : stdout);
        m.put("stderr", stderr == null ? "" : stderr);
        m.put("runtimeMs", runtimeMs);
        if (errorMessage != null && !errorMessage.isEmpty()) m.put("errorMessage", errorMessage);
        return Json.stringify(m);
    }

    public static String ack(String executionId) {
        return Json.stringify(map("ack", "executionId", executionId));
    }

    public static String protocolError(String message) {
        String safeMsg = (message == null || message.isEmpty()) ? "Protocol error" : message;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "protocol_error");
        m.put("message", safeMsg);
        return Json.stringify(m);
    }

    private static Map<String, Object> map(String type, String key, String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put(key, value == null ? "" : value);
        return m;
    }
}
