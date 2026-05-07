package com.epam.sandbox.compile;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compiles a single Java source unit in memory using
 * {@link javax.tools.JavaCompiler} and {@link DiagnosticCollector}.
 *
 * Subtask EPMICMPCOD-446: JavaCompiler API + DiagnosticCollector.
 * SRS §6.2 step 1: "Wrapper receives source code and compiles it in-memory
 *                   via JavaCompiler to a byte[]".
 */
public final class InMemoryJavaCompiler {

    /** Result of one compilation attempt. */
    public static final class Result {
        private final boolean success;
        private final Map<String, byte[]> classes;
        private final List<String> diagnostics;
        private final String formattedErrorMessage;

        Result(boolean success,
               Map<String, byte[]> classes,
               List<String> diagnostics,
               String formattedErrorMessage) {
            this.success = success;
            this.classes = classes;
            this.diagnostics = diagnostics;
            this.formattedErrorMessage = formattedErrorMessage;
        }

        public boolean isSuccess() { return success; }
        public Map<String, byte[]> getClasses() { return classes; }
        public List<String> getDiagnostics() { return diagnostics; }
        public String getFormattedErrorMessage() { return formattedErrorMessage; }
    }

    public Result compile(String className, String sourceCode) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            // The container must run on a JDK image, not a JRE-only image.
            return new Result(false, Map.of(),
                    List.of("No system Java compiler available; JDK required."),
                    "No system Java compiler available; JDK required.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager standardFm = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
             InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(standardFm)) {
            List<JavaFileObject> units = List.of(new InMemorySourceFile(className, sourceCode));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, null, null, units);

            boolean ok = Boolean.TRUE.equals(task.call());

            List<String> messages = new ArrayList<>();
            StringBuilder formatted = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                String line = formatDiagnostic(d);
                messages.add(line);
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    formatted.append(line).append(System.lineSeparator());
                }
            }

            if (!ok) {
                return new Result(false, Map.of(), messages,
                        formatted.length() == 0 ? "Compilation failed." : formatted.toString().trim());
            }

            return new Result(true, fileManager.getCompiledBytecode(), messages, null);
        } catch (Exception e) {
            return new Result(false, Map.of(),
                    List.of("Compiler error: " + e.getMessage()),
                    "Compiler error: " + e.getMessage());
        }
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> d) {
        String src = d.getSource() == null ? "<unknown>" : d.getSource().getName();
        return String.format(Locale.ROOT, "[%s] %s:%d:%d %s",
                d.getKind(), src, d.getLineNumber(), d.getColumnNumber(),
                d.getMessage(Locale.ROOT));
    }
}
