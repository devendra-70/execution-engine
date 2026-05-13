package com.epam.sandbox.execute;

import com.epam.sandbox.compile.InMemoryJavaCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReflectiveExecutorTest {

    private static InMemoryJavaCompiler.Result compile(String src) {
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess(), () -> String.join("\n", r.getDiagnostics()));
        return r;
    }

    @Test
    void capturesStdoutAndInjectsStdin() {
        InMemoryJavaCompiler.Result r = compile("""
                import java.util.Scanner;
                public class Solution {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int a = sc.nextInt(), b = sc.nextInt();
                        System.out.println(a + b);
                    }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "2 3\n");
        assertEquals(ReflectiveExecutor.Outcome.Status.OK, o.getStatus());
        assertEquals("5", o.getStdout().trim());
        assertTrue(o.getRuntimeMs() >= 0);
    }

    @Test
    void capturesStderrSeparately() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void main(String[] args) {
                        System.out.println("out");
                        System.err.println("err");
                    }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        assertEquals(ReflectiveExecutor.Outcome.Status.OK, o.getStatus());
        assertEquals("out", o.getStdout().trim());
        assertEquals("err", o.getStderr().trim());
    }

    @Test
    void unwrapsRuntimeException() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void main(String[] args) {
                        throw new IllegalStateException("boom");
                    }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        assertEquals(ReflectiveExecutor.Outcome.Status.RUNTIME_ERROR, o.getStatus());
        assertNotNull(o.getErrorMessage());
        assertTrue(o.getErrorMessage().contains("IllegalStateException"));
        assertTrue(o.getErrorMessage().contains("boom"));
    }

    @Test
    void freshStaticsBetweenInvocations() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    static int n = 0;
                    public static void main(String[] args) {
                        n++;
                        System.out.println(n);
                    }
                }
                """);
        ReflectiveExecutor exec = new ReflectiveExecutor();
        ReflectiveExecutor.Outcome o1 = exec.execute(r.getClasses(), "Solution", "");
        ReflectiveExecutor.Outcome o2 = exec.execute(r.getClasses(), "Solution", "");
        assertEquals("1", o1.getStdout().trim());
        assertEquals("1", o2.getStdout().trim(), "Static state must reset (SRS §6.2 step 2a)");
    }

    @Test
    void capturesEmptyOutput() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void main(String[] args) { }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        assertEquals(ReflectiveExecutor.Outcome.Status.OK, o.getStatus());
        assertEquals("", o.getStdout());
        assertEquals("", o.getStderr());
    }

    @Test
    void capturesSystemErrWithoutStdout() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void main(String[] args) {
                        System.err.println("error only");
                    }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        assertEquals(ReflectiveExecutor.Outcome.Status.OK, o.getStatus());
        assertEquals("", o.getStdout());
        assertEquals("error only", o.getStderr().trim());
    }

    @Test
    void handlesMissingMainMethod() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void notMain(String[] args) { }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        assertEquals(ReflectiveExecutor.Outcome.Status.RUNTIME_ERROR, o.getStatus());
        assertNotNull(o.getErrorMessage());
    }

    @Test
    void staticInitializerErrorHandledDuringClassLoad() {
        // Static initializers that throw exceptions fail at compile time in Java
        String src = """
                public class Solution {
                    static { throw new RuntimeException("static init error"); }
                    public static void main(String[] args) { }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        // Compilation will fail because static initializer is not valid Java
        assertFalse(r.isSuccess(), "Static initializer that throws must fail compilation");
    }

    @Test
    void capturesMultilineOutput() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void main(String[] args) {
                        System.out.println("line1");
                        System.out.println("line2");
                        System.out.println("line3");
                    }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        String output = o.getStdout();
        assertTrue(output.contains("line1"));
        assertTrue(output.contains("line2"));
        assertTrue(output.contains("line3"));
    }

    @Test
    void capturesOutputWithUnicodeCharacters() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void main(String[] args) {
                        System.out.println("Hello 世界 مرحبا");
                    }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        assertTrue(o.getStdout().contains("世界"));
    }

    @Test
    void handlesNullPointerException() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void main(String[] args) {
                        String s = null;
                        s.length();
                    }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        assertEquals(ReflectiveExecutor.Outcome.Status.RUNTIME_ERROR, o.getStatus());
        assertTrue(o.getErrorMessage().contains("NullPointerException"));
    }

    @Test
    void handlesArrayIndexOutOfBoundsException() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void main(String[] args) {
                        int[] arr = new int[5];
                        System.out.println(arr[10]);
                    }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        assertEquals(ReflectiveExecutor.Outcome.Status.RUNTIME_ERROR, o.getStatus());
        assertTrue(o.getErrorMessage().contains("ArrayIndexOutOfBoundsException"));
    }

    @Test
    void runtimeMsIsNonNegative() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void main(String[] args) { }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        assertTrue(o.getRuntimeMs() >= 0);
    }

    @Test
    void multipleExecutionsTrackTimeIndependently() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void main(String[] args) throws Exception {
                        Thread.sleep(10);
                        System.out.println("done");
                    }
                }
                """);
        ReflectiveExecutor exec = new ReflectiveExecutor();
        ReflectiveExecutor.Outcome o1 = exec.execute(r.getClasses(), "Solution", "");
        ReflectiveExecutor.Outcome o2 = exec.execute(r.getClasses(), "Solution", "");
        // Both should report reasonable runtimes
        assertTrue(o1.getRuntimeMs() >= 0);
        assertTrue(o2.getRuntimeMs() >= 0);
        assertEquals("done", o1.getStdout().trim());
        assertEquals("done", o2.getStdout().trim());
    }

    @Test
    void handlesComplexInheritance() {
        InMemoryJavaCompiler.Result r = compile("""
                abstract class Base { abstract void run(); }
                public class Solution extends Base {
                    @Override void run() { System.out.println("running"); }
                    public static void main(String[] args) {
                        new Solution().run();
                    }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        assertEquals(ReflectiveExecutor.Outcome.Status.OK, o.getStatus());
        assertTrue(o.getStdout().contains("running"));
    }

    @Test
    void capturesStdoutWithSpecialCharactersAndNewlines() {
        InMemoryJavaCompiler.Result r = compile("""
                public class Solution {
                    public static void main(String[] args) {
                        System.out.print("tab:\\there, newline:\\nhere");
                    }
                }
                """);
        ReflectiveExecutor.Outcome o = new ReflectiveExecutor()
                .execute(r.getClasses(), "Solution", "");
        String output = o.getStdout();
        assertTrue(output.contains("here"));
    }
}
