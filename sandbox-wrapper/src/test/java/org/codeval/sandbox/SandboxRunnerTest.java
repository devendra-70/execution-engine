package org.codeval.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SandboxRunner")
class SandboxRunnerTest {

    private SandboxRunner runner;

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static TestCaseInput tc(long id, String input, String expected) {
        return new TestCaseInput(id, 1L, input, expected, 0);
    }

    private static SandboxRequest req(String source, TestCaseInput... testCases) {
        return new SandboxRequest(source, List.of(testCases), 5000L);
    }

    @BeforeEach
    void setUp() {
        runner = new SandboxRunner();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Compile-error path
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Compile error")
    class CompileError {

        @Test
        @DisplayName("all test cases get COMPILE_ERROR when source is invalid")
        void allTestCasesGetCompileError() {
            SandboxRequest request = req("this is not java",
                    tc(1L, "", "1"),
                    tc(2L, "", "2"));

            List<TestCaseResult> results = runner.run(request);

            assertThat(results).hasSize(2);
            assertThat(results).allSatisfy(r -> {
                assertThat(r.getVerdict()).isEqualTo("COMPILE_ERROR");
                assertThat(r.getErrorMessage()).isNotBlank();
            });
        }

        @Test
        @DisplayName("COMPILE_ERROR result carries error message with line info")
        void errorMessageContainsLineInfo() {
            SandboxRequest request = req("public class Solution { BROKEN }",
                    tc(1L, "", "x"));

            TestCaseResult result = runner.run(request).get(0);
            assertThat(result.getVerdict()).isEqualTo("COMPILE_ERROR");
            assertThat(result.getErrorMessage()).contains("Line");
        }

        @Test
        @DisplayName("COMPILE_ERROR result has the original testCaseId")
        void errorResultHasCorrectTestCaseId() {
            SandboxRequest request = req("bad source", tc(42L, "", ""));

            TestCaseResult result = runner.run(request).get(0);
            assertThat(result.getTestCaseId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("empty source code produces COMPILE_ERROR")
        void emptySourceProducesCompileError() {
            SandboxRequest request = req("", tc(1L, "", ""));

            TestCaseResult result = runner.run(request).get(0);
            assertThat(result.getVerdict()).isEqualTo("COMPILE_ERROR");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ACCEPTED
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ACCEPTED verdict")
    class Accepted {

        @Test
        @DisplayName("prints exact expected output → ACCEPTED")
        void exactMatchIsAccepted() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"Hello, World!\");\n" +
                    "    }\n" +
                    "}";

            List<TestCaseResult> results = runner.run(req(source, tc(1L, "", "Hello, World!")));

            assertThat(results.get(0).getVerdict()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("output is trimmed before comparison — trailing newline is ACCEPTED")
        void trailingNewlineIsIgnored() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.print(\"42\\n\");\n" +
                    "    }\n" +
                    "}";

            TestCaseResult result = runner.run(req(source, tc(1L, "", "42"))).get(0);
            assertThat(result.getVerdict()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("reads stdin and produces correct output → ACCEPTED")
        void readsStdinCorrectly() {
            String source =
                    "import java.util.Scanner;\n" +
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        Scanner sc = new Scanner(System.in);\n" +
                    "        int n = sc.nextInt();\n" +
                    "        System.out.println(n * 2);\n" +
                    "    }\n" +
                    "}";

            TestCaseResult result = runner.run(req(source, tc(1L, "21", "42"))).get(0);
            assertThat(result.getVerdict()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("multiple test cases — each gets independent ACCEPTED result")
        void multipleTestCasesAllAccepted() {
            String source =
                    "import java.util.Scanner;\n" +
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        Scanner sc = new Scanner(System.in);\n" +
                    "        System.out.println(sc.nextInt() + 1);\n" +
                    "    }\n" +
                    "}";

            SandboxRequest request = new SandboxRequest(source, List.of(
                    tc(1L, "1", "2"),
                    tc(2L, "9", "10"),
                    tc(3L, "99", "100")
            ), 5000L);

            List<TestCaseResult> results = runner.run(request);
            assertThat(results).hasSize(3);
            assertThat(results).allSatisfy(r -> assertThat(r.getVerdict()).isEqualTo("ACCEPTED"));
        }

        @Test
        @DisplayName("result contains runtimeMs > 0")
        void resultContainsPositiveRuntime() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"ok\");\n" +
                    "    }\n" +
                    "}";

            TestCaseResult result = runner.run(req(source, tc(1L, "", "ok"))).get(0);
            assertThat(result.getRuntimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("result carries actualOutput equal to expected")
        void resultActualOutputPopulated() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"hello\");\n" +
                    "    }\n" +
                    "}";

            TestCaseResult result = runner.run(req(source, tc(1L, "", "hello"))).get(0);
            assertThat(result.getActualOutput()).isEqualTo("hello");
            assertThat(result.getExpectedOutput()).isEqualTo("hello");
        }

        @Test
        @DisplayName("static counter does not bleed between two test cases")
        void staticStateDoesNotBleedBetweenTestCases() {
            String source =
                    "public class Solution {\n" +
                    "    static int counter = 0;\n" +
                    "    public static void main(String[] args) {\n" +
                    "        counter++;\n" +
                    "        System.out.println(counter);\n" +
                    "    }\n" +
                    "}";

            SandboxRequest request = new SandboxRequest(source, List.of(
                    tc(1L, "", "1"),
                    tc(2L, "", "1")   // fresh class loader — counter starts at 0 again
            ), 5000L);

            List<TestCaseResult> results = runner.run(request);
            assertThat(results.get(0).getVerdict()).isEqualTo("ACCEPTED");
            assertThat(results.get(1).getVerdict()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("null input is treated as empty stdin")
        void nullInputTreatedAsEmpty() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"done\");\n" +
                    "    }\n" +
                    "}";

            TestCaseInput tcNull = new TestCaseInput(1L, 1L, null, "done", 0);
            TestCaseResult result = runner.run(new SandboxRequest(source, List.of(tcNull), 5000L)).get(0);
            assertThat(result.getVerdict()).isEqualTo("ACCEPTED");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WRONG_ANSWER
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("WRONG_ANSWER verdict")
    class WrongAnswer {

        @Test
        @DisplayName("output mismatch → WRONG_ANSWER")
        void outputMismatchIsWrongAnswer() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"wrong\");\n" +
                    "    }\n" +
                    "}";

            TestCaseResult result = runner.run(req(source, tc(1L, "", "correct"))).get(0);
            assertThat(result.getVerdict()).isEqualTo("WRONG_ANSWER");
        }

        @Test
        @DisplayName("WRONG_ANSWER result contains both actual and expected outputs")
        void wrongAnswerContainsBothOutputs() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"actual\");\n" +
                    "    }\n" +
                    "}";

            TestCaseResult result = runner.run(req(source, tc(1L, "", "expected"))).get(0);
            assertThat(result.getActualOutput()).isEqualTo("actual");
            assertThat(result.getExpectedOutput()).isEqualTo("expected");
        }

        @Test
        @DisplayName("null expectedOutput is treated as empty string")
        void nullExpectedOutputTreatedAsEmpty() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"something\");\n" +
                    "    }\n" +
                    "}";

            TestCaseInput tcNullExpected = new TestCaseInput(1L, 1L, "", null, 0);
            TestCaseResult result = runner.run(new SandboxRequest(source, List.of(tcNullExpected), 5000L)).get(0);
            assertThat(result.getVerdict()).isEqualTo("WRONG_ANSWER");
            assertThat(result.getExpectedOutput()).isEqualTo("");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // RUNTIME_ERROR
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("RUNTIME_ERROR verdict")
    class RuntimeError {

        @Test
        @DisplayName("throwing exception in main → RUNTIME_ERROR")
        void uncaughtExceptionIsRuntimeError() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        throw new RuntimeException(\"boom\");\n" +
                    "    }\n" +
                    "}";

            TestCaseResult result = runner.run(req(source, tc(1L, "", ""))).get(0);
            assertThat(result.getVerdict()).isEqualTo("RUNTIME_ERROR");
        }

        @Test
        @DisplayName("RUNTIME_ERROR result contains error message")
        void runtimeErrorHasMessage() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        int[] arr = new int[0];\n" +
                    "        System.out.println(arr[5]);\n" +
                    "    }\n" +
                    "}";

            TestCaseResult result = runner.run(req(source, tc(1L, "", ""))).get(0);
            assertThat(result.getVerdict()).isEqualTo("RUNTIME_ERROR");
            assertThat(result.getErrorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("StackOverflowError → RUNTIME_ERROR")
        void stackOverflowIsRuntimeError() {
            String source =
                    "public class Solution {\n" +
                    "    static void inf() { inf(); }\n" +
                    "    public static void main(String[] args) { inf(); }\n" +
                    "}";

            TestCaseResult result = runner.run(req(source, tc(1L, "", ""))).get(0);
            assertThat(result.getVerdict()).isEqualTo("RUNTIME_ERROR");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TIME_LIMIT_EXCEEDED
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TIME_LIMIT_EXCEEDED verdict")
    class TimeLimitExceeded {

        @Test
        @DisplayName("infinite loop exceeds tight timeout → TIME_LIMIT_EXCEEDED")
        void infiniteLoopExceedsTimeout() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) throws Exception {\n" +
                    "        while (true) { Thread.sleep(50); }\n" +
                    "    }\n" +
                    "}";

            SandboxRequest request = new SandboxRequest(source, List.of(tc(1L, "", "")), 200L);
            TestCaseResult result = runner.run(request).get(0);

            assertThat(result.getVerdict()).isEqualTo("TIME_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("TLE result runtimeMs equals the configured timeout")
        void tleRuntimeEqualsTimeout() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) throws Exception {\n" +
                    "        while (true) { Thread.sleep(50); }\n" +
                    "    }\n" +
                    "}";

            long timeout = 200L;
            SandboxRequest request = new SandboxRequest(source, List.of(tc(1L, "", "")), timeout);
            TestCaseResult result = runner.run(request).get(0);

            assertThat(result.getRuntimeMs()).isEqualTo(timeout);
        }

        @Test
        @DisplayName("TLE result carries descriptive error message")
        void tleHasErrorMessage() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) throws Exception {\n" +
                    "        while (true) { Thread.sleep(50); }\n" +
                    "    }\n" +
                    "}";

            SandboxRequest request = new SandboxRequest(source, List.of(tc(1L, "", "")), 200L);
            TestCaseResult result = runner.run(request).get(0);

            assertThat(result.getErrorMessage()).contains("200");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Default timeout
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Default timeout (timeoutMs = 0)")
    class DefaultTimeout {

        @Test
        @DisplayName("timeoutMs=0 falls back to 3000ms default — fast code still passes")
        void zeroTimeoutUsesDefault() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"fast\");\n" +
                    "    }\n" +
                    "}";

            SandboxRequest request = new SandboxRequest(source, List.of(tc(1L, "", "fast")), 0L);
            TestCaseResult result = runner.run(request).get(0);
            assertThat(result.getVerdict()).isEqualTo("ACCEPTED");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mixed test cases
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Mixed verdicts")
    class MixedVerdicts {

        @Test
        @DisplayName("one accepted, one wrong answer in the same run")
        void mixedAcceptedAndWrongAnswer() {
            String source =
                    "import java.util.Scanner;\n" +
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        Scanner sc = new Scanner(System.in);\n" +
                    "        System.out.println(sc.nextInt());\n" +
                    "    }\n" +
                    "}";

            SandboxRequest request = new SandboxRequest(source, List.of(
                    tc(1L, "5", "5"),   // ACCEPTED
                    tc(2L, "5", "99")   // WRONG_ANSWER
            ), 5000L);

            List<TestCaseResult> results = runner.run(request);
            assertThat(results.get(0).getVerdict()).isEqualTo("ACCEPTED");
            assertThat(results.get(1).getVerdict()).isEqualTo("WRONG_ANSWER");
        }

        @Test
        @DisplayName("result list size equals number of test cases")
        void resultCountMatchesTestCaseCount() {
            String source =
                    "public class Solution {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"x\");\n" +
                    "    }\n" +
                    "}";

            SandboxRequest request = new SandboxRequest(source, List.of(
                    tc(1L, "", "x"),
                    tc(2L, "", "x"),
                    tc(3L, "", "x"),
                    tc(4L, "", "x"),
                    tc(5L, "", "x")
            ), 5000L);

            assertThat(runner.run(request)).hasSize(5);
        }
    }
}
