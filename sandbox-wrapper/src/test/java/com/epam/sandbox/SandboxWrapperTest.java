package com.epam.sandbox;

import com.epam.sandbox.execute.SubmissionRunner;
import com.epam.sandbox.json.Json;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SandboxWrapperTest {

    /**
     * End-to-end protocol drive (SRS §6.2):
     *   source → compiled → testcase → result → testcase → result → end → ack
     */
    @Test
    void streamingProtocolHappyPath() throws Exception {
        String src = """
                import java.util.Scanner;
                public class Solution {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        System.out.println(sc.nextInt() + sc.nextInt());
                    }
                }
                """;
        StringBuilder input = new StringBuilder();
        input.append(Json.stringify(Map.of(
                "type", "source",
                "executionId", "exec-1",
                "className", "Solution",
                "sourceCode", src))).append('\n');
        input.append(Json.stringify(Map.of(
                "type", "testcase",
                "id", "tc1",
                "stdin", "1 2\n"))).append('\n');
        input.append(Json.stringify(Map.of(
                "type", "testcase",
                "id", "tc2",
                "stdin", "10 20\n"))).append('\n');
        input.append(Json.stringify(Map.of(
                "type", "end",
                "executionId", "exec-1"))).append('\n');

        String[] lines = drive(input.toString());
        assertEquals(4, lines.length, () -> "Unexpected response stream:\n" + String.join("\n", lines));
        assertTrue(lines[0].contains("\"type\":\"compiled\""));
        assertTrue(lines[1].contains("\"id\":\"tc1\"") && lines[1].contains("\"status\":\"OK\""));
        assertTrue(lines[1].contains("3"));
        assertTrue(lines[2].contains("\"id\":\"tc2\"") && lines[2].contains("\"status\":\"OK\""));
        assertTrue(lines[2].contains("30"));
        assertTrue(lines[3].contains("\"type\":\"ack\""));
    }

    /**
     * SRS §10: COMPILE_ERROR short-circuits — no test cases evaluated.
     */
    @Test
    void compileErrorShortCircuits() throws Exception {
        String input = Json.stringify(Map.of(
                "type", "source",
                "executionId", "exec-2",
                "className", "Solution",
                "sourceCode", "public class Solution { not java }")) + "\n"
                + Json.stringify(Map.of(
                "type", "testcase",
                "id", "tc1",
                "stdin", "")) + "\n";

        String[] lines = drive(input);
        assertTrue(lines.length >= 1);
        assertTrue(lines[0].contains("\"type\":\"compile_error\""));
        // Subsequent testcase frame must surface a protocol_error rather than a result.
        assertTrue(lines[1].contains("\"type\":\"protocol_error\""));
    }

    @Test
    void malformedJsonProducesProtocolError() throws Exception {
        String[] lines = drive("{not json\n");
        assertEquals(1, lines.length);
        assertTrue(lines[0].contains("\"type\":\"protocol_error\""));
    }

    @Test
    void unknownFrameTypeProducesProtocolError() throws Exception {
        String[] lines = drive(Json.stringify(Map.of("type", "wat")) + "\n");
        assertEquals(1, lines.length);
        assertTrue(lines[0].contains("\"type\":\"protocol_error\""));
    }

    @Test
    void protocolStateTransitions() throws Exception {
        // Test: source → testcase (VALID) → source (reset) → testcase (new)
        String src1 = """
                public class Solution { public static void main(String[] a){ System.out.println("1"); } }
                """;
        String src2 = """
                public class Solution { public static void main(String[] a){ System.out.println("2"); } }
                """;
        StringBuilder input = new StringBuilder();
        input.append(Json.stringify(Map.of("type", "source", "executionId", "e1", "className", "Solution", "sourceCode", src1))).append('\n');
        input.append(Json.stringify(Map.of("type", "testcase", "id", "tc1", "stdin", ""))).append('\n');
        input.append(Json.stringify(Map.of("type", "end", "executionId", "e1"))).append('\n');
        // New submission
        input.append(Json.stringify(Map.of("type", "source", "executionId", "e2", "className", "Solution", "sourceCode", src2))).append('\n');
        input.append(Json.stringify(Map.of("type", "testcase", "id", "tc2", "stdin", ""))).append('\n');
        input.append(Json.stringify(Map.of("type", "end", "executionId", "e2"))).append('\n');

        String[] lines = drive(input.toString());
        assertEquals(6, lines.length);
        assertTrue(lines[0].contains("\"type\":\"compiled\"") && lines[0].contains("\"executionId\":\"e1\""));
        assertTrue(lines[1].contains("\"id\":\"tc1\""));
        assertTrue(lines[2].contains("\"type\":\"ack\"") && lines[2].contains("\"executionId\":\"e1\""));
        assertTrue(lines[3].contains("\"type\":\"compiled\"") && lines[3].contains("\"executionId\":\"e2\""));
        assertTrue(lines[4].contains("\"id\":\"tc2\""));
        assertTrue(lines[5].contains("\"type\":\"ack\"") && lines[5].contains("\"executionId\":\"e2\""));
    }

    @Test
    void missingSourceCodeHandledGracefully() throws Exception {
        // Missing sourceCode — wrapper should handle gracefully
        String input = Json.stringify(Map.of("type", "source", "executionId", "e1", "className", "Solution")) + "\n";
        String[] lines = drive(input);
        // Should be compile_error (null sourceCode compiles with diagnostic)
        assertTrue(lines[0].contains("\"type\":\"compile_error\"") || lines[0].contains("\"type\":\"protocol_error\""),
                "Expected error for missing sourceCode, got: " + lines[0]);
    }

    @Test
    void testcaseWithoutCompiledSourceProducesProtocolError() throws Exception {
        String input = Json.stringify(Map.of("type", "testcase", "id", "tc1", "stdin", "")) + "\n";
        String[] lines = drive(input);
        assertTrue(lines[0].contains("\"type\":\"protocol_error\""));
    }

    @Test
    void multipleTestCasesExecuteSequentially() throws Exception {
        String src = """
                import java.util.Scanner;
                public class Solution {
                    public static void main(String[] a) {
                        Scanner sc = new Scanner(System.in);
                        System.out.println(sc.nextInt() * 2);
                    }
                }
                """;
        StringBuilder input = new StringBuilder();
        input.append(Json.stringify(Map.of("type", "source", "executionId", "e1", "className", "Solution", "sourceCode", src))).append('\n');
        for (int i = 1; i <= 5; i++) {
            input.append(Json.stringify(Map.of("type", "testcase", "id", "tc" + i, "stdin", i + "\n"))).append('\n');
        }
        input.append(Json.stringify(Map.of("type", "end", "executionId", "e1"))).append('\n');

        String[] lines = drive(input.toString());
        assertEquals(7, lines.length); // compiled + 5 results + ack
        for (int i = 1; i <= 5; i++) {
            assertTrue(lines[i].contains("\"id\":\"tc" + i + "\""), "Missing result for tc" + i);
            assertTrue(lines[i].contains("\"status\":\"OK\""), "Expected OK for tc" + i);
            assertTrue(lines[i].contains(String.valueOf(i * 2)), "Expected " + (i*2) + " in output for tc" + i);
        }
    }

    @Test
    void runtimeErrorInOneTestCaseDoesNotAffectNextTestCase() throws Exception {
        String src = """
                import java.util.Scanner;
                public class Solution {
                    public static void main(String[] a) {
                        Scanner sc = new Scanner(System.in);
                        int x = Integer.parseInt(sc.nextLine());
                        if (x < 0) throw new RuntimeException("negative");
                        System.out.println(x);
                    }
                }
                """;
        StringBuilder input = new StringBuilder();
        input.append(Json.stringify(Map.of("type", "source", "executionId", "e1", "className", "Solution", "sourceCode", src))).append('\n');
        input.append(Json.stringify(Map.of("type", "testcase", "id", "tc1", "stdin", "-5\n"))).append('\n');
        input.append(Json.stringify(Map.of("type", "testcase", "id", "tc2", "stdin", "10\n"))).append('\n');
        input.append(Json.stringify(Map.of("type", "end", "executionId", "e1"))).append('\n');

        String[] lines = drive(input.toString());
        assertEquals(4, lines.length); // compiled + 2 results + ack
        assertTrue(lines[1].contains("\"id\":\"tc1\"") && lines[1].contains("\"status\":\"RUNTIME_ERROR\""));
        assertTrue(lines[2].contains("\"id\":\"tc2\"") && lines[2].contains("\"status\":\"OK\"") && lines[2].contains("10"));
    }

    @Test
    void frameWithUnknownFieldsIgnoredGracefully() throws Exception {
        String src = "public class Solution { public static void main(String[] a){} }";
        String input = Json.stringify(Map.of(
                "type", "source",
                "executionId", "e1",
                "className", "Solution",
                "sourceCode", src,
                "unknownField", "should be ignored",
                "anotherUnknown", 12345)) + "\n"
                + Json.stringify(Map.of("type", "end", "executionId", "e1")) + "\n";
        String[] lines = drive(input);
        assertTrue(lines[0].contains("\"type\":\"compiled\""));
        assertTrue(lines[1].contains("\"type\":\"ack\""));
    }

    @Test
    void emptyLinesSkipped() throws Exception {
        String src = "public class Solution { public static void main(String[] a){} }";
        String input = "\n\n" // empty lines
                + Json.stringify(Map.of("type", "source", "executionId", "e1", "className", "Solution", "sourceCode", src)) + "\n"
                + "\n" // empty line
                + Json.stringify(Map.of("type", "end", "executionId", "e1")) + "\n";
        String[] lines = drive(input);
        assertEquals(2, lines.length); // compiled + ack only (empty lines filtered)
        assertTrue(lines[0].contains("\"type\":\"compiled\""));
    }

    private static String[] drive(String input) throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(raw, true, StandardCharsets.UTF_8);
        try (SubmissionRunner runner = new SubmissionRunner()) {
            SandboxWrapper.runStdinLoop(runner, in, out);
        }
        String response = raw.toString(StandardCharsets.UTF_8);
        if (response.isEmpty()) return new String[0];
        return response.split("\\R");
    }
}
