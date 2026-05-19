package org.codeval.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("InMemoryClassLoader")
class InMemoryClassLoaderTest {

    // Compile a minimal "Solution" class and return its class bytes
    private byte[] compileSolution(String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diag = new DiagnosticCollector<>();
        InMemoryJavaFileManager fm = new InMemoryJavaFileManager(
                compiler.getStandardFileManager(diag, null, null));

        JavaFileObject sourceFile = new SimpleJavaFileObject(
                URI.create("string:///Solution.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };

        compiler.getTask(null, fm, diag, null, null, List.of(sourceFile)).call();
        byte[] bytes = fm.getClassBytes("Solution");
        if (bytes == null) throw new IllegalStateException("Compilation failed");
        return bytes;
    }

    @Nested
    @DisplayName("findClass")
    class FindClass {

        @Test
        @DisplayName("loads Solution class from byte array")
        void loadsSolutionClass() throws Exception {
            byte[] bytes = compileSolution(
                    "public class Solution { public static void main(String[] a) {} }");

            try (InMemoryClassLoader loader = new InMemoryClassLoader(bytes)) {
                Class<?> clazz = loader.loadClass("Solution");
                assertThat(clazz).isNotNull();
                assertThat(clazz.getName()).isEqualTo("Solution");
            }
        }

        @Test
        @DisplayName("loaded class can be instantiated")
        void loadedClassCanBeInstantiated() throws Exception {
            byte[] bytes = compileSolution(
                    "public class Solution { public static void main(String[] a) {} }");

            try (InMemoryClassLoader loader = new InMemoryClassLoader(bytes)) {
                Class<?> clazz = loader.loadClass("Solution");
                Object instance = clazz.getDeclaredConstructor().newInstance();
                assertThat(instance).isNotNull();
            }
        }

        @Test
        @DisplayName("delegates unknown class to parent loader")
        void delegatesUnknownClassToParent() throws Exception {
            byte[] bytes = compileSolution(
                    "public class Solution { public static void main(String[] a) {} }");

            try (InMemoryClassLoader loader = new InMemoryClassLoader(bytes)) {
                // java.lang.String should be resolved by parent
                Class<?> clazz = loader.loadClass("java.lang.String");
                assertThat(clazz).isEqualTo(String.class);
            }
        }

        @Test
        @DisplayName("throws ClassNotFoundException for unknown non-Solution class")
        void throwsForUnknownClass() throws Exception {
            byte[] bytes = compileSolution(
                    "public class Solution { public static void main(String[] a) {} }");

            try (InMemoryClassLoader loader = new InMemoryClassLoader(bytes)) {
                assertThatThrownBy(() -> loader.loadClass("com.nonexistent.Foo"))
                        .isInstanceOf(ClassNotFoundException.class);
            }
        }

        @Test
        @DisplayName("two separate loaders return distinct Class objects")
        void twoLoadersReturnDistinctClasses() throws Exception {
            byte[] bytes = compileSolution(
                    "public class Solution { public static int counter = 0; " +
                    "public static void main(String[] a) {} }");

            try (InMemoryClassLoader loader1 = new InMemoryClassLoader(bytes);
                 InMemoryClassLoader loader2 = new InMemoryClassLoader(bytes)) {
                Class<?> c1 = loader1.loadClass("Solution");
                Class<?> c2 = loader2.loadClass("Solution");
                assertThat(c1).isNotSameAs(c2);
            }
        }

        @Test
        @DisplayName("static state is isolated between loaders")
        void staticStateIsIsolated() throws Exception {
            String source = "public class Solution { " +
                    "public static int counter = 0; " +
                    "public static void main(String[] a) { counter++; } }";
            byte[] bytes = compileSolution(source);

            try (InMemoryClassLoader loader1 = new InMemoryClassLoader(bytes);
                 InMemoryClassLoader loader2 = new InMemoryClassLoader(bytes)) {
                Class<?> c1 = loader1.loadClass("Solution");
                Class<?> c2 = loader2.loadClass("Solution");

                c1.getMethod("main", String[].class).invoke(null, (Object) new String[]{});
                c1.getMethod("main", String[].class).invoke(null, (Object) new String[]{});

                int counterC1 = (int) c1.getField("counter").get(null);
                int counterC2 = (int) c2.getField("counter").get(null);

                assertThat(counterC1).isEqualTo(2);
                assertThat(counterC2).isEqualTo(0); // untouched
            }
        }
    }

    @Nested
    @DisplayName("close")
    class Close {

        @Test
        @DisplayName("close() is a no-op and does not throw")
        void closeIsNoOp() throws Exception {
            byte[] bytes = compileSolution(
                    "public class Solution { public static void main(String[] a) {} }");

            InMemoryClassLoader loader = new InMemoryClassLoader(bytes);
            assertThatCode(loader::close).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("try-with-resources closes without exception")
        void tryWithResourcesClosesCleanly() throws Exception {
            byte[] bytes = compileSolution(
                    "public class Solution { public static void main(String[] a) {} }");

            assertThatCode(() -> {
                try (InMemoryClassLoader loader = new InMemoryClassLoader(bytes)) {
                    loader.loadClass("Solution");
                }
            }).doesNotThrowAnyException();
        }
    }
}
