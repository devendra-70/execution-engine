package org.codeval.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.*;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("InMemoryJavaFileManager")
class InMemoryJavaFileManagerTest {

    private InMemoryJavaFileManager fileManager;

    @BeforeEach
    void setUp() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager std = compiler.getStandardFileManager(null, null, null);
        fileManager = new InMemoryJavaFileManager(std);
    }

    @Nested
    @DisplayName("getJavaFileForOutput")
    class GetJavaFileForOutput {

        @Test
        @DisplayName("returns non-null JavaFileObject for CLASS kind")
        void returnsNonNullForClassKind() throws Exception {
            JavaFileObject fo = fileManager.getJavaFileForOutput(
                    StandardLocation.CLASS_OUTPUT, "Solution",
                    JavaFileObject.Kind.CLASS, null);
            assertThat(fo).isNotNull();
        }

        @Test
        @DisplayName("openOutputStream returns a writable stream")
        void openOutputStreamIsWritable() throws Exception {
            JavaFileObject fo = fileManager.getJavaFileForOutput(
                    StandardLocation.CLASS_OUTPUT, "Solution",
                    JavaFileObject.Kind.CLASS, null);

            OutputStream os = fo.openOutputStream();
            assertThatCode(() -> os.write(new byte[]{0x42})).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("written bytes can be retrieved via getClassBytes")
        void writtenBytesRetrievable() throws Exception {
            JavaFileObject fo = fileManager.getJavaFileForOutput(
                    StandardLocation.CLASS_OUTPUT, "MyClass",
                    JavaFileObject.Kind.CLASS, null);

            byte[] data = {1, 2, 3, 4, 5};
            try (OutputStream os = fo.openOutputStream()) {
                os.write(data);
            }

            byte[] retrieved = fileManager.getClassBytes("MyClass");
            assertThat(retrieved).isEqualTo(data);
        }

        @Test
        @DisplayName("multiple classes stored independently")
        void multipleClassesStoredIndependently() throws Exception {
            byte[] a = {10, 20};
            byte[] b = {30, 40, 50};

            try (OutputStream osA = fileManager.getJavaFileForOutput(
                    StandardLocation.CLASS_OUTPUT, "ClassA",
                    JavaFileObject.Kind.CLASS, null).openOutputStream()) {
                osA.write(a);
            }
            try (OutputStream osB = fileManager.getJavaFileForOutput(
                    StandardLocation.CLASS_OUTPUT, "ClassB",
                    JavaFileObject.Kind.CLASS, null).openOutputStream()) {
                osB.write(b);
            }

            assertThat(fileManager.getClassBytes("ClassA")).isEqualTo(a);
            assertThat(fileManager.getClassBytes("ClassB")).isEqualTo(b);
        }

        @Test
        @DisplayName("URI of returned object contains class name")
        void uriContainsClassName() throws Exception {
            JavaFileObject fo = fileManager.getJavaFileForOutput(
                    StandardLocation.CLASS_OUTPUT, "Solution",
                    JavaFileObject.Kind.CLASS, null);
            assertThat(fo.toUri().toString()).contains("Solution");
        }
    }

    @Nested
    @DisplayName("getClassBytes")
    class GetClassBytes {

        @Test
        @DisplayName("returns null for unknown class name")
        void returnsNullForUnknown() {
            assertThat(fileManager.getClassBytes("NonExistent")).isNull();
        }

        @Test
        @DisplayName("returns empty byte array when nothing written")
        void returnsEmptyWhenNothingWritten() throws Exception {
            // create the output object but write nothing
            fileManager.getJavaFileForOutput(
                    StandardLocation.CLASS_OUTPUT, "Empty",
                    JavaFileObject.Kind.CLASS, null);
            // stream not opened yet — bytes are empty array (stream not flushed)
            byte[] bytes = fileManager.getClassBytes("Empty");
            assertThat(bytes).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("Full compilation integration")
    class FullCompilation {

        @Test
        @DisplayName("compiling valid Solution produces non-null class bytes")
        void compilingValidSourceProducesBytes() {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diag = new DiagnosticCollector<>();
            InMemoryJavaFileManager fm = new InMemoryJavaFileManager(
                    compiler.getStandardFileManager(diag, null, null));

            JavaFileObject src = new SimpleJavaFileObject(
                    URI.create("string:///Solution.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean b) {
                    return "public class Solution { public static void main(String[] a){} }";
                }
            };

            boolean ok = compiler.getTask(null, fm, diag, null, null, List.of(src)).call();
            assertThat(ok).isTrue();
            assertThat(fm.getClassBytes("Solution")).isNotNull().isNotEmpty();
        }
    }
}
