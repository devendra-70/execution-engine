package com.epam.sandbox.protocol;

import com.epam.sandbox.json.Json;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FrameTest {

    @Test
    void parsesSourceFrame() {
        String line = Json.stringify(Map.of(
                "type", "source",
                "executionId", "e1",
                "className", "Solution",
                "sourceCode", "class Solution {}"));
        Frame.ParsedFrame pf = Frame.parse(line);
        assertEquals(Frame.Type.SOURCE, pf.type);
        assertEquals("e1", pf.getString("executionId"));
        assertEquals("Solution", pf.getString("className"));
    }

    @Test
    void parsesTestCaseFrame() {
        String line = Json.stringify(Map.of(
                "type", "testcase",
                "id", "tc1",
                "stdin", "5 10",
                "timeoutMs", 3000L));
        Frame.ParsedFrame pf = Frame.parse(line);
        assertEquals(Frame.Type.TESTCASE, pf.type);
        assertEquals("tc1", pf.getString("id"));
        assertEquals("5 10", pf.getString("stdin"));
        assertEquals(3000L, pf.getLong("timeoutMs", 0L));
    }

    @Test
    void parsesEndFrame() {
        String line = Json.stringify(Map.of(
                "type", "end",
                "executionId", "e1"));
        Frame.ParsedFrame pf = Frame.parse(line);
        assertEquals(Frame.Type.END, pf.type);
        assertEquals("e1", pf.getString("executionId"));
    }

    @Test
    void getLongReturnsDefaultForMissingField() {
        String line = Json.stringify(Map.of(
                "type", "testcase",
                "id", "tc1",
                "stdin", ""));
        Frame.ParsedFrame pf = Frame.parse(line);
        assertEquals(5000L, pf.getLong("timeoutMs", 5000L));
    }

    @Test
    void getLongParsesStringAsLong() {
        String line = Json.stringify(Map.of(
                "type", "testcase",
                "id", "tc1",
                "stdin", "",
                "timeoutMs", "2500")); // string instead of number
        Frame.ParsedFrame pf = Frame.parse(line);
        assertEquals(2500L, pf.getLong("timeoutMs", 0L));
    }

    @Test
    void getLongReturnsDefaultForInvalidStringNumber() {
        String line = Json.stringify(Map.of(
                "type", "testcase",
                "id", "tc1",
                "stdin", "",
                "timeoutMs", "not_a_number"));
        Frame.ParsedFrame pf = Frame.parse(line);
        assertEquals(4000L, pf.getLong("timeoutMs", 4000L));
    }

    @Test
    void getLongHandlesNegativeNumbers() {
        String line = Json.stringify(Map.of(
                "type", "testcase",
                "id", "tc1",
                "stdin", "",
                "timeoutMs", -100L));
        Frame.ParsedFrame pf = Frame.parse(line);
        assertEquals(-100L, pf.getLong("timeoutMs", 0L));
    }

    @Test
    void getLongHandlesZero() {
        String line = Json.stringify(Map.of(
                "type", "testcase",
                "id", "tc1",
                "stdin", "",
                "timeoutMs", 0L));
        Frame.ParsedFrame pf = Frame.parse(line);
        assertEquals(0L, pf.getLong("timeoutMs", 999L));
    }

    @Test
    void compiledFrameGeneration() {
        String result = Frame.compiled("exec-1");
        assertTrue(result.contains("\"type\":\"compiled\""));
        assertTrue(result.contains("\"executionId\":\"exec-1\""));
    }

    @Test
    void compileErrorWithMessage() {
        String result = Frame.compileError("exec-1", "syntax error");
        assertTrue(result.contains("\"type\":\"compile_error\""));
        assertTrue(result.contains("\"executionId\":\"exec-1\""));
        assertTrue(result.contains("\"message\":\"syntax error\""));
    }

    @Test
    void compileErrorWithNullMessageUsesDefault() {
        String result = Frame.compileError("exec-1", null);
        assertTrue(result.contains("\"type\":\"compile_error\""));
        assertTrue(result.contains("\"message\":\"Compilation failed\""));
    }

    @Test
    void compileErrorWithEmptyMessageUsesDefault() {
        String result = Frame.compileError("exec-1", "");
        assertTrue(result.contains("\"type\":\"compile_error\""));
        assertTrue(result.contains("\"message\":\"Compilation failed\""));
    }

    @Test
    void resultFrameOkStatus() {
        String result = Frame.result("tc1", Frame.ResultStatus.OK,
                "output", "errors", 150L, null);
        assertTrue(result.contains("\"id\":\"tc1\""));
        assertTrue(result.contains("\"status\":\"OK\""));
        assertTrue(result.contains("\"stdout\":\"output\""));
        assertTrue(result.contains("\"stderr\":\"errors\""));
        assertTrue(result.contains("\"runtimeMs\":150"));
    }

    @Test
    void resultFrameRuntimeErrorWithMessage() {
        String result = Frame.result("tc1", Frame.ResultStatus.RUNTIME_ERROR,
                "", "", 0L, "NullPointerException: at line 10");
        assertTrue(result.contains("\"status\":\"RUNTIME_ERROR\""));
        assertTrue(result.contains("\"errorMessage\":\"NullPointerException: at line 10\""));
    }

    @Test
    void resultFrameTimeoutStatus() {
        String result = Frame.result("tc1", Frame.ResultStatus.TIME_LIMIT_EXCEEDED,
                "", "", 5000L, "Exceeded 5000ms");
        assertTrue(result.contains("\"status\":\"TIME_LIMIT_EXCEEDED\""));
        assertTrue(result.contains("\"errorMessage\":\"Exceeded 5000ms\""));
    }

    @Test
    void resultFrameOmitsErrorMessageWhenNull() {
        String result = Frame.result("tc1", Frame.ResultStatus.OK,
                "output", "", 100L, null);
        assertTrue(result.contains("\"id\":\"tc1\""));
        assertTrue(result.contains("\"status\":\"OK\""));
        assertFalse(result.contains("\"errorMessage\""));
    }

    @Test
    void resultFrameOmitsErrorMessageWhenEmpty() {
        String result = Frame.result("tc1", Frame.ResultStatus.OK,
                "output", "", 100L, "");
        assertTrue(result.contains("\"id\":\"tc1\""));
        assertFalse(result.contains("\"errorMessage\""));
    }

    @Test
    void resultFrameNullStdoutTreatedAsEmpty() {
        String result = Frame.result("tc1", Frame.ResultStatus.OK,
                null, "", 0L, null);
        assertTrue(result.contains("\"stdout\":\"\""));
    }

    @Test
    void resultFrameNullStderrTreatedAsEmpty() {
        String result = Frame.result("tc1", Frame.ResultStatus.OK,
                "", null, 0L, null);
        assertTrue(result.contains("\"stderr\":\"\""));
    }

    @Test
    void ackFrameGeneration() {
        String result = Frame.ack("exec-1");
        assertTrue(result.contains("\"type\":\"ack\""));
        assertTrue(result.contains("\"executionId\":\"exec-1\""));
    }

    @Test
    void protocolErrorWithMessage() {
        String result = Frame.protocolError("Invalid frame format");
        assertTrue(result.contains("\"type\":\"protocol_error\""));
        assertTrue(result.contains("\"message\":\"Invalid frame format\""));
    }

    @Test
    void protocolErrorWithNullMessageUsesDefault() {
        String result = Frame.protocolError(null);
        assertTrue(result.contains("\"type\":\"protocol_error\""));
        assertTrue(result.contains("\"message\":\"Protocol error\""));
    }

    @Test
    void protocolErrorWithEmptyMessageUsesDefault() {
        String result = Frame.protocolError("");
        assertTrue(result.contains("\"type\":\"protocol_error\""));
        assertTrue(result.contains("\"message\":\"Protocol error\""));
    }

    @Test
    void parseRejectsNonStringType() {
        String line = Json.stringify(Map.of(
                "type", 123,
                "id", "tc1"));
        assertThrows(Json.JsonException.class, () -> Frame.parse(line));
    }

    @Test
    void parseMissingTypeThrows() {
        String line = Json.stringify(Map.of(
                "id", "tc1",
                "stdin", ""));
        assertThrows(Json.JsonException.class, () -> Frame.parse(line));
    }

    @Test
    void parseUnknownTypeThrows() {
        String line = Json.stringify(Map.of(
                "type", "unknown_type",
                "id", "tc1"));
        assertThrows(Json.JsonException.class, () -> Frame.parse(line));
    }

    @Test
    void resultFrameWithNegativeRuntimeMs() {
        String result = Frame.result("tc1", Frame.ResultStatus.OK,
                "output", "", -1L, null);
        assertTrue(result.contains("\"runtimeMs\":-1"));
    }

    @Test
    void resultFrameWithVeryLargeRuntimeMs() {
        String result = Frame.result("tc1", Frame.ResultStatus.TIME_LIMIT_EXCEEDED,
                "", "", 999999999L, "Timeout");
        assertTrue(result.contains("\"runtimeMs\":999999999"));
    }

    @Test
    void getStringReturnsNullForMissingField() {
        String line = Json.stringify(Map.of(
                "type", "testcase",
                "id", "tc1"));
        Frame.ParsedFrame pf = Frame.parse(line);
        assertNull(pf.getString("nonexistent"));
    }

    @Test
    void getStringReturnsNullForNonStringField() {
        String line = Json.stringify(Map.of(
                "type", "testcase",
                "id", "tc1",
                "count", 42L));
        Frame.ParsedFrame pf = Frame.parse(line);
        assertNull(pf.getString("count")); // field is a number, not string
    }
}
