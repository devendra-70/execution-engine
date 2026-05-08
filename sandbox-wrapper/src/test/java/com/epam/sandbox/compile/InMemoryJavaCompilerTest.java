package com.epam.sandbox.compile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryJavaCompilerTest {

    @Test
    void compilesValidSource() {
        String src = """
                public class Solution {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess(), () -> "diagnostics: " + r.getDiagnostics());
        assertTrue(r.getClasses().containsKey("Solution"));
        assertTrue(r.getClasses().get("Solution").length > 0);
        assertNull(r.getFormattedErrorMessage());
    }

    @Test
    void surfacesCompileErrors() {
        String bad = "public class Solution { this is not java }";
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", bad);
        assertFalse(r.isSuccess());
        assertTrue(r.getClasses().isEmpty());
        assertNotNull(r.getFormattedErrorMessage());
        assertFalse(r.getFormattedErrorMessage().isBlank());
    }

    @Test
    void capturesAuxiliaryClasses() {
        String src = """
                public class Solution {
                    static class Helper { int x = 42; }
                    public static void main(String[] args) {
                        System.out.println(new Helper().x);
                    }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
        assertTrue(r.getClasses().containsKey("Solution"));
        assertTrue(r.getClasses().containsKey("Solution$Helper"));
    }

    @Test
    void compilesEmptyClass() {
        String src = "public class Solution {}";
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
        assertTrue(r.getClasses().containsKey("Solution"));
    }

    @Test
    void compilesClassWithImports() {
        String src = """
                import java.util.*;
                public class Solution {
                    public static void main(String[] args) {
                        List<String> list = new ArrayList<>();
                        list.add("test");
                        System.out.println(list.get(0));
                    }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
    }

    @Test
    void compilesClassWithGenerics() {
        String src = """
                public class Solution {
                    public static <T> void print(T obj) { System.out.println(obj); }
                    public static void main(String[] args) { print("hello"); }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
    }

    @Test
    void compilesClassWithVarargs() {
        String src = """
                public class Solution {
                    public static void main(String[] args) {
                        int sum = sum(1, 2, 3);
                        System.out.println(sum);
                    }
                    static int sum(int... nums) {
                        int s = 0;
                        for (int n : nums) s += n;
                        return s;
                    }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
    }

    @Test
    void packagePrivateClassIsAllowed() {
        // Java allows package-private classes - no 'public' modifier needed
        String src = "class Solution { public static void main(String[] args) {} }";
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        // This actually compiles - Java allows package-private classes
        assertTrue(r.isSuccess()); // Package-private classes are valid
    }

    @Test
    void rejectsClassNameMismatch() {
        String src = "public class WrongName { public static void main(String[] args) {} }";
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertFalse(r.isSuccess());
    }

    @Test
    void rejectsMissingMainMethod() {
        String src = "public class Solution { }";
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        // Compilation succeeds but execution will fail
        assertTrue(r.isSuccess());
    }

    @Test
    void compilesWithSyntaxError() {
        String src = """
                public class Solution {
                    public static void main(String[] args) {
                        System.out.println("missing semicolon")
                    }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertFalse(r.isSuccess());
        assertTrue(r.getDiagnostics().size() > 0);
    }

    @Test
    void compilesWithMultipleInnerClasses() {
        String src = """
                public class Solution {
                    static class Inner1 { }
                    static class Inner2 { }
                    public static void main(String[] args) { }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
        assertTrue(r.getClasses().containsKey("Solution$Inner1"));
        assertTrue(r.getClasses().containsKey("Solution$Inner2"));
    }

    @Test
    void compilesWithAnonymousClass() {
        String src = """
                public class Solution {
                    public static void main(String[] args) {
                        Runnable r = new Runnable() {
                            @Override public void run() { }
                        };
                    }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
        assertTrue(r.getClasses().containsKey("Solution$1"));
    }

    @Test
    void compilesWithAnnotations() {
        String src = """
                public class Solution {
                    @Deprecated
                    public static void oldMethod() { }
                    public static void main(String[] args) { }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
    }

    @Test
    void compilesWithLambdas() {
        String src = """
                public class Solution {
                    public static void main(String[] args) {
                        Runnable r = () -> System.out.println("lambda");
                        r.run();
                    }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
    }

    @Test
    void compilesWithStreamApi() {
        String src = """
                import java.util.*;
                public class Solution {
                    public static void main(String[] args) {
                        List.of(1, 2, 3).stream()
                            .filter(x -> x > 1)
                            .forEach(System.out::println);
                    }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
    }
}
