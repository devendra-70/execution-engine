package org.codeval.sandbox;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Core execution engine.
 * 1. Compiles user source code once via javax.tools.JavaCompiler
 * 2. For each test case: creates a fresh ClassLoader to prevent static state-bleed
 * 3. Executes Solution.main() with injected stdin, captures stdout
 */
public class SandboxRunner {

    public List<TestCaseResult> run(SandboxRequest request) {
        // Step 1: Compile once
        CompileResult compileResult = compile(request.getSourceCode());
        if (!compileResult.success()) {
            // Short-circuit: all test cases get COMPILE_ERROR
            return request.getTestCases().stream()
                    .map(tc -> TestCaseResult.builder()
                            .testCaseId(tc.getId())
                            .verdict("COMPILE_ERROR")
                            .errorMessage(compileResult.error())
                            .build())
                    .toList();
        }

        byte[] classBytes = compileResult.classBytes();
        long timeout = request.getTimeoutMs() > 0 ? request.getTimeoutMs() : 3000L;

        return request.getTestCases().stream()
                .map(tc -> executeTestCase(tc, classBytes, timeout))
                .toList();
    }

    private TestCaseResult executeTestCase(TestCaseInput tc, byte[] classBytes, long timeoutMs) {
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {
            // New ClassLoader per test case - prevents static variable bleed
            try (InMemoryClassLoader loader = new InMemoryClassLoader(classBytes)) {
                Class<?> solutionClass = loader.loadClass("Solution");
                Method mainMethod = solutionClass.getMethod("main", String[].class);

                // Inject stdin
                InputStream originalIn = System.in;
                PrintStream originalOut = System.out;
                ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();

                try (InputStream inputStream = new ByteArrayInputStream(
                        tc.getInput() != null ? tc.getInput().getBytes() : new byte[0])) {
                    System.setIn(inputStream);
                    System.setOut(new PrintStream(capturedOut));
                    mainMethod.invoke(null, (Object) new String[]{});
                } finally {
                    System.setIn(originalIn);
                    System.setOut(originalOut);
                }

                return capturedOut.toString().trim();
            }
        });

        try {
            String actualOutput = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - start;
            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long memUsed = Math.max(0, memAfter - memBefore);

            String expectedOutput = tc.getExpectedOutput() != null ? tc.getExpectedOutput().trim() : "";
            String verdict = actualOutput.equals(expectedOutput) ? "ACCEPTED" : "WRONG_ANSWER";

            return TestCaseResult.builder()
                    .testCaseId(tc.getId())
                    .verdict(verdict)
                    .actualOutput(actualOutput)
                    .expectedOutput(expectedOutput)
                    .runtimeMs(elapsed)
                    .memoryBytes(memUsed)
                    .build();

        } catch (TimeoutException e) {
            future.cancel(true);
            return TestCaseResult.builder()
                    .testCaseId(tc.getId())
                    .verdict("TIME_LIMIT_EXCEEDED")
                    .runtimeMs(timeoutMs)
                    .errorMessage("Execution exceeded " + timeoutMs + "ms")
                    .build();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return TestCaseResult.builder()
                    .testCaseId(tc.getId())
                    .verdict("RUNTIME_ERROR")
                    .runtimeMs(System.currentTimeMillis() - start)
                    .errorMessage(cause.getClass().getSimpleName() + ": " + cause.getMessage())
                    .build();
        } finally {
            executor.shutdownNow();
        }
    }

    private CompileResult compile(String sourceCode) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return CompileResult.failure("JavaCompiler not available. Ensure JDK is used, not JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
                compiler.getStandardFileManager(diagnostics, null, null));

        JavaFileObject sourceFile = new SimpleJavaFileObject(
                URI.create("string:///Solution.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return sourceCode;
            }
        };

        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics,
                Arrays.asList("-source", "21", "-target", "21"),
                null,
                List.of(sourceFile));

        boolean success = task.call();
        if (!success) {
            StringBuilder errors = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    errors.append("Line ").append(d.getLineNumber())
                          .append(": ").append(d.getMessage(null)).append("\n");
                }
            }
            return CompileResult.failure(errors.toString());
        }

        byte[] classBytes = fileManager.getClassBytes("Solution");
        if (classBytes == null) {
            return CompileResult.failure("Compiled class 'Solution' not found. Ensure class is named 'Solution'.");
        }
        return CompileResult.success(classBytes);
    }

    record CompileResult(boolean success, byte[] classBytes, String error) {
        static CompileResult success(byte[] bytes) { return new CompileResult(true, bytes, null); }
        static CompileResult failure(String err) { return new CompileResult(false, null, err); }
    }
}


