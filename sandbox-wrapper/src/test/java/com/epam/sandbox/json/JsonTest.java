package com.epam.sandbox.json;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void parsesObjectWithMixedTypes() {
        Map<String, Object> m = Json.parseObject(
                "{\"a\":1,\"b\":\"x\",\"c\":true,\"d\":null,\"e\":[1,2.5,\"y\"]}");
        assertEquals(1L, m.get("a"));
        assertEquals("x", m.get("b"));
        assertEquals(Boolean.TRUE, m.get("c"));
        assertNull(m.get("d"));
        assertEquals(List.of(1L, 2.5, "y"), m.get("e"));
    }

    @Test
    void parsesEscapesIncludingUnicode() {
        Map<String, Object> m = Json.parseObject("{\"s\":\"line1\\nline2\\t\\u00e9\"}");
        assertEquals("line1\nline2\t\u00e9", m.get("s"));
    }

    @Test
    void roundTripsObject() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "result");
        m.put("status", "OK");
        m.put("stdout", "hi\n\"world\"");
        m.put("runtimeMs", 12L);
        String s = Json.stringify(m);
        Map<String, Object> back = Json.parseObject(s);
        assertEquals("result", back.get("type"));
        assertEquals("hi\n\"world\"", back.get("stdout"));
        assertEquals(12L, back.get("runtimeMs"));
    }

    @Test
    void rejectsTrailingGarbage() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{}xxx"));
    }

    @Test
    void rejectsRootNonObjectForParseObject() {
        assertThrows(Json.JsonException.class, () -> Json.parseObject("[1,2]"));
    }

    @Test
    void rejectsMalformed() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":1,"));
        assertThrows(Json.JsonException.class, () -> Json.parse("\"unterminated"));
    }

    @Test
    void writesNullsAndArraysAndNonFiniteAsNull() {
        assertEquals("null", Json.stringify(null));
        assertEquals("[1,2,3]", Json.stringify(List.of(1, 2, 3)));
        assertEquals("null", Json.stringify(Double.NaN));
        assertEquals("null", Json.stringify(Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(Json.JsonException.class, () -> Json.parse(null));
    }

    @Test
    void parsesLargeNumbers() {
        long bigNum = Long.MAX_VALUE;
        String json = "{\"big\": " + bigNum + "}";
        Map<String, Object> m = Json.parseObject(json);
        assertEquals(bigNum, m.get("big"));
    }

    @Test
    void parsesNegativeNumbers() {
        String json = "{\"neg\": -42, \"negFloat\": -3.14}";
        Map<String, Object> m = Json.parseObject(json);
        assertEquals(-42L, m.get("neg"));
        assertEquals(-3.14, m.get("negFloat"));
    }

    @Test
    void parsesScientificNotation() {
        String json = "{\"sci\": 1.5e3, \"negSci\": -2.5e-2}";
        Map<String, Object> m = Json.parseObject(json);
        assertEquals(1500.0, m.get("sci"));
        assertEquals(-0.025, m.get("negSci"));
    }

    @Test
    void parsesEmptyObject() {
        Map<String, Object> m = Json.parseObject("{}");
        assertEquals(0, m.size());
    }

    @Test
    void parsesEmptyArray() {
        List<Object> arr = (List<Object>) Json.parse("[]");
        assertEquals(0, arr.size());
    }

    @Test
    void parsesNestedStructures() {
        String json = "{\"outer\": {\"inner\": [1, 2, {\"deep\": \"value\"}]}}";
        Map<String, Object> m = Json.parseObject(json);
        Map<String, Object> outer = (Map<String, Object>) m.get("outer");
        List<Object> inner = (List<Object>) outer.get("inner");
        assertEquals(3, inner.size());
        assertEquals("value", ((Map<String, Object>) inner.get(2)).get("deep"));
    }

    @Test
    void stringifyPreservesKeyOrder() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("z", "last");
        m.put("a", "first");
        m.put("m", "middle");
        String json = Json.stringify(m);
        // Parse back and verify order by checking substring positions
        assertTrue(json.indexOf("\"z\"") < json.indexOf("\"a\""));
        assertTrue(json.indexOf("\"a\"") < json.indexOf("\"m\""));
    }

    @Test
    void parsesBooleanCaseInsensitive() {
        // true/false are lowercase
        Map<String, Object> m = Json.parseObject("{\"t\":true, \"f\":false}");
        assertEquals(Boolean.TRUE, m.get("t"));
        assertEquals(Boolean.FALSE, m.get("f"));
    }

    @Test
    void rejectsBooleanCaseVariants() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"t\":True}"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"t\":FALSE}"));
    }

    @Test
    void stringifyBooleans() {
        assertEquals("true", Json.stringify(true));
        assertEquals("false", Json.stringify(false));
    }

    @Test
    void parsesAllEscapeSequences() {
        String json = "{\"esc\":\"\\\\\\\"\\b\\f\\n\\r\\t\"}";
        Map<String, Object> m = Json.parseObject(json);
        String val = (String) m.get("esc");
        assertTrue(val.contains("\\"));
        assertTrue(val.contains("\""));
        assertTrue(val.contains("\b"));
        assertTrue(val.contains("\f"));
        assertTrue(val.contains("\n"));
        assertTrue(val.contains("\r"));
        assertTrue(val.contains("\t"));
    }

    @Test
    void parsesUnicodeEscapes() {
        String json = "{\"unicode\":\"\\u0041\\u00e9\\u65e5\"}"; // A, é, 日
        Map<String, Object> m = Json.parseObject(json);
        String val = (String) m.get("unicode");
        assertEquals("Aé日", val);
    }

    @Test
    void rejectsInvalidUnicodeEscape() {
        // Invalid hex codes throw NumberFormatException
        assertThrows(Exception.class, () -> Json.parse("{\"x\":\"\\u00g0\"}"));
        assertThrows(Exception.class, () -> Json.parse("{\"x\":\"\\u00\"}"));
    }

    @Test
    void rejectsInvalidEscapeSequence() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"x\":\"\\q\"}"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"x\":\"\\x\"}"));
    }

    @Test
    void stringifyEscapesProperlyInStrings() {
        String withEscapes = "tab\there\nnewline\rreturn\"quote\\backslash";
        String json = Json.stringify(withEscapes);
        Map<String, Object> parsed = Json.parseObject("{\"val\":" + json + "}");
        assertEquals(withEscapes, parsed.get("val"));
    }

    @Test
    void stringifyPreservesControlCharacters() {
        String withCtrl = "before\u0001after";
        String json = Json.stringify(withCtrl);
        Map<String, Object> parsed = Json.parseObject("{\"val\":" + json + "}");
        assertEquals(withCtrl, parsed.get("val"));
    }

    @Test
    void parsesNumberBoundaries() {
        // Test edge cases for number parsing
        String json = "{\"zero\":0, \"one\":1, \"neg\":-1}";
        Map<String, Object> m = Json.parseObject(json);
        assertEquals(0L, m.get("zero"));
        assertEquals(1L, m.get("one"));
        assertEquals(-1L, m.get("neg"));
    }

    @Test
    void stringifyNumbers() {
        assertEquals("42", Json.stringify(42L));
        assertEquals("3.14", Json.stringify(3.14));
        assertEquals("0", Json.stringify(0L));
    }

    @Test
    void stringifyNonFiniteDoublesAsNull() {
        assertEquals("null", Json.stringify(Double.NaN));
        assertEquals("null", Json.stringify(Double.POSITIVE_INFINITY));
        assertEquals("null", Json.stringify(Double.NEGATIVE_INFINITY));
    }

    @Test
    void stringifyNonFiniteFloatsAsNull() {
        assertEquals("null", Json.stringify(Float.NaN));
        assertEquals("null", Json.stringify(Float.POSITIVE_INFINITY));
        assertEquals("null", Json.stringify(Float.NEGATIVE_INFINITY));
    }

    @Test
    void roundTripsComplexStructure() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("name", "test");
        java.util.List<Object> valuesList = new java.util.ArrayList<>();
        valuesList.add(1L);
        valuesList.add(2.5);
        valuesList.add("three");
        valuesList.add(null);
        original.put("values", valuesList);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("flag", true);
        nested.put("nullable", null);
        original.put("nested", nested);

        String json = Json.stringify(original);
        Map<String, Object> parsed = Json.parseObject(json);

        assertEquals("test", parsed.get("name"));
        java.util.List<Object> expectedValues = new java.util.ArrayList<>();
        expectedValues.add(1L);
        expectedValues.add(2.5);
        expectedValues.add("three");
        expectedValues.add(null);
        assertEquals(expectedValues, parsed.get("values"));
        Map<String, Object> parsedNested = (Map<String, Object>) parsed.get("nested");
        assertEquals(true, parsedNested.get("flag"));
        assertNull(parsedNested.get("nullable"));
    }

    @Test
    void parseObjectRejectsArray() {
        assertThrows(Json.JsonException.class, () -> Json.parseObject("[1, 2, 3]"));
    }

    @Test
    void parseObjectRejectsString() {
        assertThrows(Json.JsonException.class, () -> Json.parseObject("\"string\""));
    }

    @Test
    void parseObjectRejectsNumber() {
        assertThrows(Json.JsonException.class, () -> Json.parseObject("42"));
    }

    @Test
    void parsesWhitespaceAroundElements() {
        String json = "{ \"a\" : 1 , \"b\" : 2 }";
        Map<String, Object> m = Json.parseObject(json);
        assertEquals(1L, m.get("a"));
        assertEquals(2L, m.get("b"));
    }

    @Test
    void parsesObjectWithTrailingCommaRejects() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\": 1,}"));
    }

    @Test
    void rejectsLoneLiteralAfterValue() {
        assertThrows(Json.JsonException.class, () -> Json.parse("1 2"));
    }

    @Test
    void parsesZeroAsLong() {
        Map<String, Object> m = Json.parseObject("{\"z\": 0}");
        assertEquals(0L, m.get("z"));
        assertTrue(m.get("z") instanceof Long);
    }

    @Test
    void parsesFloatWithLeadingDecimal() {
        // JSON technically allows numbers like 0.5 or 1.0
        Map<String, Object> m = Json.parseObject("{\"dec\": 0.5}");
        assertEquals(0.5, m.get("dec"));
    }

    @Test
    void stringifyListWithMixedTypes() {
        java.util.List<Object> list = new java.util.ArrayList<>();
        list.add(1L);
        list.add("str");
        list.add(true);
        list.add(null);
        String json = Json.stringify(list);
        assertTrue(json.contains("1"));
        assertTrue(json.contains("\"str\""));
        assertTrue(json.contains("true"));
        assertTrue(json.contains("null"));
    }
}
