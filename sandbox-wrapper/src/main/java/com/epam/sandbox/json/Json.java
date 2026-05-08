package com.epam.sandbox.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal hand-rolled JSON parser/serializer.
 *
 * SRS §6.1 mandates a "tiny, dependency-free Java application", so the wrapper
 * deliberately avoids Jackson/Gson. Only the JSON subset actually required by
 * the wrapper protocol is supported.
 *
 * Subtasks: EPMICMPCOD-452 (input parsing) and EPMICMPCOD-455 (result serialization).
 *
 * Parsed value mapping:
 *   object  -> {@code Map<String,Object>}
 *   array   -> {@code List<Object>}
 *   string  -> {@code String}
 *   number  -> {@code Long} (integral) or {@code Double}
 *   boolean -> {@code Boolean}
 *   null    -> {@code null}
 */
public final class Json {

    private Json() {}

    // ---------- Parser ----------

    public static Object parse(String input) {
        if (input == null) throw new JsonException("null input");
        Parser p = new Parser(input);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (!p.eof()) {
            throw new JsonException("Trailing characters at position " + p.pos);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String input) {
        Object v = parse(input);
        if (!(v instanceof Map)) {
            throw new JsonException("Expected JSON object at root");
        }
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) { this.s = s; }

        boolean eof() { return pos >= s.length(); }

        void skipWhitespace() {
            while (!eof()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++;
                else break;
            }
        }

        Object readValue() {
            skipWhitespace();
            if (eof()) throw new JsonException("Unexpected end of input");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> obj = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') { pos++; return obj; }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                obj.put(key, value);
                skipWhitespace();
                char c = next();
                if (c == ',') continue;
                if (c == '}') return obj;
                throw new JsonException("Expected ',' or '}' at " + pos);
            }
        }

        List<Object> readArray() {
            expect('[');
            List<Object> arr = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') { pos++; return arr; }
            while (true) {
                arr.add(readValue());
                skipWhitespace();
                char c = next();
                if (c == ',') continue;
                if (c == ']') return arr;
                throw new JsonException("Expected ',' or ']' at " + pos);
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!eof()) {
                char c = next();
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (eof()) throw new JsonException("Bad escape at end of input");
                    char esc = next();
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > s.length()) throw new JsonException("Bad unicode escape");
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new JsonException("Bad escape \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new JsonException("Unterminated string");
        }

        Object readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (!eof()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    pos++;
                } else break;
            }
            String num = s.substring(start, pos);
            if (num.isEmpty()) throw new JsonException("Expected number at " + start);
            if (num.contains(".") || num.contains("e") || num.contains("E")) {
                return Double.parseDouble(num);
            }
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                return Double.parseDouble(num);
            }
        }

        Boolean readBoolean() {
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new JsonException("Expected boolean at " + pos);
        }

        Object readNull() {
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            throw new JsonException("Expected null at " + pos);
        }

        char peek() {
            if (eof()) throw new JsonException("Unexpected end of input");
            return s.charAt(pos);
        }

        char next() {
            if (eof()) throw new JsonException("Unexpected end of input");
            return s.charAt(pos++);
        }

        void expect(char c) {
            if (eof() || s.charAt(pos) != c) {
                throw new JsonException("Expected '" + c + "' at " + pos);
            }
            pos++;
        }
    }

    // ---------- Serializer ----------

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) { sb.append("null"); return; }
        if (value instanceof CharSequence cs) { writeString(sb, cs.toString()); return; }
        if (value instanceof Boolean b) { sb.append(b ? "true" : "false"); return; }
        if (value instanceof Number n) { writeNumber(sb, n); return; }
        if (value instanceof Map<?, ?> m) { writeObject(sb, m); return; }
        if (value instanceof Iterable<?> it) { writeArray(sb, it); return; }
        writeString(sb, value.toString());
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        if (n instanceof Double d && (d.isNaN() || d.isInfinite())) { sb.append("null"); return; }
        if (n instanceof Float f && (f.isNaN() || f.isInfinite())) { sb.append("null"); return; }
        sb.append(n.toString());
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            write(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Iterable<?> values) {
        sb.append('[');
        boolean first = true;
        for (Object v : values) {
            if (!first) sb.append(',');
            first = false;
            write(sb, v);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    /** Thrown for any malformed JSON input. */
    public static final class JsonException extends RuntimeException {
        public JsonException(String message) { super(message); }
    }
}
