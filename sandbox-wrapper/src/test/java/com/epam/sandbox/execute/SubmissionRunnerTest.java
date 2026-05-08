package com.epam.sandbox.execute;

import com.epam.sandbox.compile.InMemoryJavaCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionRunnerTest {

    @Test
    void compileFailureClearsRunnerState() {
        try (SubmissionRunner runner = new SubmissionRunner()) {
            InMemoryJavaCompiler.Result r = runner.compile("Solution", "garbage");
            assertFalse(r.isSuccess());
            assertFalse(runner.isReady());
            assertThrows(IllegalStateException.class,
                    () -> runner.runTestCase("tc1", "", 0L));
        }
    }

    @Test
    void runsMultipleTestCasesAfterSingleCompile() {
        String src = """
                import java.util.Scanner;
                public class Solution {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        System.out.println(sc.nextInt() * 2);
                    }
                }
                """;
        try (SubmissionRunner runner = new SubmissionRunner()) {
            assertTrue(runner.compile("Solution", src).isSuccess());
            String r1 = runner.runTestCase("tc1", "5", 0L);
            String r2 = runner.runTestCase("tc2", "7", 0L);
            assertTrue(r1.contains("\"status\":\"OK\""));
            assertTrue(r1.contains("10"));
            assertTrue(r2.contains("\"status\":\"OK\""));
            assertTrue(r2.contains("14"));
        }
    }

    @Test
    void timeoutEnforcedAsTle() {
        String src = """
                public class Solution {
                    public static void main(String[] args) throws Exception {
                        Thread.sleep(60_000);
                    }
                }
                """;
        try (SubmissionRunner runner = new SubmissionRunner()) {
            assertTrue(runner.compile("Solution", src).isSuccess());
            String result = runner.runTestCase("tc1", "", 200L);
            assertTrue(result.contains("\"status\":\"TIME_LIMIT_EXCEEDED\""),
                    "Expected TLE, got: " + result);
        }
    }

    @Test
    void runtimeErrorDoesNotPreventSubsequentTestCases() {
        String src = """
                import java.util.Scanner;
                public class Solution {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = Integer.parseInt(sc.next());
                        if (n == 0) throw new RuntimeException("boom");
                        System.out.println("ok=" + n);
                    }
                }
                """;
        try (SubmissionRunner runner = new SubmissionRunner()) {
            assertTrue(runner.compile("Solution", src).isSuccess());
            String bad  = runner.runTestCase("tc1", "0", 0L);
            String good = runner.runTestCase("tc2", "9", 0L);
            assertTrue(bad.contains("\"status\":\"RUNTIME_ERROR\""));
            assertTrue(good.contains("\"status\":\"OK\""));
            assertTrue(good.contains("ok=9"));
        }
    }

    @Test
    void resetClearsBytecode() {
        try (SubmissionRunner runner = new SubmissionRunner()) {
            assertTrue(runner.compile("Solution",
                    "public class Solution { public static void main(String[] a){} }").isSuccess());
            assertTrue(runner.isReady());
            runner.reset();
            assertFalse(runner.isReady());
        }
    }

    @Test
    void outOfMemoryErrorHandledInExecutionException() {
        String src = """
                public class Solution {
                    public static void main(String[] args) {
                        throw new OutOfMemoryError("heap exhausted");
                    }
                }
                """;
        try (SubmissionRunner runner = new SubmissionRunner()) {
            assertTrue(runner.compile("Solution", src).isSuccess());
            String result = runner.runTestCase("tc1", "", 0L);
            assertTrue(result.contains("\"status\":\"RUNTIME_ERROR\""));
            assertTrue(result.contains("OutOfMemoryError") || result.contains("heap exhausted"));
        }
    }

    @Test
    void throwableWithNullMessageHandledSafely() {
        String src = """
                public class Solution {
                    public static void main(String[] args) {
                        throw new RuntimeException();
                    }
                }
                """;
        try (SubmissionRunner runner = new SubmissionRunner()) {
            assertTrue(runner.compile("Solution", src).isSuccess());
            String result = runner.runTestCase("tc1", "", 0L);
            assertTrue(result.contains("\"status\":\"RUNTIME_ERROR\""));
            assertTrue(result.contains("RuntimeException"));
        }
    }

    @Test
    void stdoutWithSpecialCharactersSerializedProperly() {
        String src = """
                public class Solution {
                    public static void main(String[] args) {
                        System.out.println("line1\\nline2\\ttab\\"quote");
                    }
                }
                """;
        try (SubmissionRunner runner = new SubmissionRunner()) {
            assertTrue(runner.compile("Solution", src).isSuccess());
            String result = runner.runTestCase("tc1", "", 0L);
            assertTrue(result.contains("\"status\":\"OK\""));
            // Verify JSON escaping works
            assertTrue(result.contains("\\\\n") || result.contains("\\n"));
        }
    }

    @Test
    void multipleCompileAttemptsResetStateCorrectly() {
        String goodSrc = "public class Solution { public static void main(String[] a){} }";
        String badSrc = "garbage code here";
        try (SubmissionRunner runner = new SubmissionRunner()) {
            // First: successful compile
            assertTrue(runner.compile("Solution", goodSrc).isSuccess());
            assertTrue(runner.isReady());
            // Second: failed compile
            assertFalse(runner.compile("Solution", badSrc).isSuccess());
            assertFalse(runner.isReady());
            // Third: successful compile again
            assertTrue(runner.compile("Solution", goodSrc).isSuccess());
            assertTrue(runner.isReady());
        }
    }

    @Test
    void testCaseWithVeryLargeStdin() {
        String src = """
                import java.util.Scanner;
                public class Solution {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int count = 0;
                        while (sc.hasNext()) {
                            sc.next();
                            count++;
                        }
                        System.out.println(count);
                    }
                }
                """;
        try (SubmissionRunner runner = new SubmissionRunner()) {
            assertTrue(runner.compile("Solution", src).isSuccess());
            StringBuilder largInput = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                largInput.append(i).append(" ");
            }
            String result = runner.runTestCase("tc1", largInput.toString(), 0L);
            assertTrue(result.contains("\"status\":\"OK\""));
            assertTrue(result.contains("10000"));
        }
    }

    @Test
    void closureWithoutCompilingClosesCleanly() {
        SubmissionRunner runner = new SubmissionRunner();
        assertFalse(runner.isReady());
        runner.close(); // Should not throw
    }

    @Test
    void throwableWithLongStackTraceHandledSafely() {
        String src = """
                public class Solution {
                    static void depth(int n) {
                        if (n == 0) throw new RuntimeException("deep error");
                        depth(n - 1);
                    }
                    public static void main(String[] args) {
                        depth(100);
                    }
                }
                """;
        try (SubmissionRunner runner = new SubmissionRunner()) {
            assertTrue(runner.compile("Solution", src).isSuccess());
            String result = runner.runTestCase("tc1", "", 0L);
            assertTrue(result.contains("\"status\":\"RUNTIME_ERROR\""));
            assertTrue(result.contains("deep error"));
        }
    }
}
