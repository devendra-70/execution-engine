package com.epam.sandbox.execute;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Executes a compiled user class via reflection inside a fresh
 * {@link IsolatedClassLoader}, with stdin injected and stdout/stderr captured.
 *
 * Subtask EPMICMPCOD-449: reflective method invocation with exception handling.
 * Subtask EPMICMPCOD-450: System stream redirection for I/O capture.
 *
 * SRS §6.2 step 2b: "Reflection is used to load the {@code Solution} class".
 * SRS §6.2 step 2c: "Method is invoked with injected stdin. Output is captured."
 *
 * Thread model: this class is stateless. The wrapper runs submissions
 * serially (SRS §4.3 — one submission per container), so the global
 * {@code System.in/out/err} swap is safe.
 */
public final class ReflectiveExecutor {

    /** Outcome of a single test case run. */
    public static final class Outcome {
        public enum Status { OK, RUNTIME_ERROR }

        private final Status status;
        private final String stdout;
        private final String stderr;
        private final String errorMessage;
        private final long runtimeMs;

        Outcome(Status status, String stdout, String stderr, String errorMessage, long runtimeMs) {
            this.status = status;
            this.stdout = stdout;
            this.stderr = stderr;
            this.errorMessage = errorMessage;
            this.runtimeMs = runtimeMs;
        }

        public Status getStatus() { return status; }
        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
        public String getErrorMessage() { return errorMessage; }
        public long getRuntimeMs() { return runtimeMs; }
    }

    /**
     * Run {@code mainClassName#main(String[])} once with the given stdin payload.
     *
     * @param bytecode      output of the in-memory compiler (binaryName → bytes)
     * @param mainClassName fully qualified class containing
     *                      {@code public static void main(String[])}
     * @param stdin         UTF-8 stdin payload to feed the user program
     */
    public Outcome execute(Map<String, byte[]> bytecode, String mainClassName, String stdin) {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream capturedOut = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
        PrintStream capturedErr = new PrintStream(errBuf, true, StandardCharsets.UTF_8);
        InputStream capturedIn = new ByteArrayInputStream(
                stdin == null ? new byte[0] : stdin.getBytes(StandardCharsets.UTF_8));

        InputStream  originalIn  = System.in;
        PrintStream  originalOut = System.out;
        PrintStream  originalErr = System.err;

        long start = System.nanoTime();
        try {
            System.setIn(capturedIn);
            System.setOut(capturedOut);
            System.setErr(capturedErr);

            // Fresh ClassLoader per call → static fields re-initialise (SRS §6.2 step 2a).
            IsolatedClassLoader loader =
                    new IsolatedClassLoader(bytecode, ReflectiveExecutor.class.getClassLoader());
            Class<?> userClass = Class.forName(mainClassName, true, loader);
            Method main = userClass.getDeclaredMethod("main", String[].class);
            main.setAccessible(true);
            main.invoke(null, (Object) new String[0]);

            long runtimeMs = (System.nanoTime() - start) / 1_000_000L;
            capturedOut.flush();
            capturedErr.flush();
            return new Outcome(Outcome.Status.OK,
                    outBuf.toString(StandardCharsets.UTF_8),
                    errBuf.toString(StandardCharsets.UTF_8),
                    null, runtimeMs);

        } catch (InvocationTargetException ite) {
            long runtimeMs = (System.nanoTime() - start) / 1_000_000L;
            Throwable cause = ite.getCause() == null ? ite : ite.getCause();
            return failure(cause, outBuf, errBuf, capturedOut, capturedErr, runtimeMs);

        } catch (Throwable t) {
            long runtimeMs = (System.nanoTime() - start) / 1_000_000L;
            return failure(t, outBuf, errBuf, capturedOut, capturedErr, runtimeMs);

        } finally {
            // Close PrintStreams to ensure proper resource cleanup (Fix #1 — resource leak prevention)
            try {
                capturedOut.close();
                capturedErr.close();
            } catch (Exception ignored) {
                // Ignore close errors; streams are discarded anyway
            }
            // Critical: the wrapper's own System.out is the response channel.
            System.setIn(originalIn);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static Outcome failure(Throwable t,
                                   ByteArrayOutputStream outBuf,
                                   ByteArrayOutputStream errBuf,
                                   PrintStream capturedOut,
                                   PrintStream capturedErr,
                                   long runtimeMs) {
        capturedOut.flush();
        capturedErr.flush();
        String message = t.getClass().getName()
                + (t.getMessage() == null ? "" : ": " + t.getMessage());
        return new Outcome(Outcome.Status.RUNTIME_ERROR,
                outBuf.toString(StandardCharsets.UTF_8),
                errBuf.toString(StandardCharsets.UTF_8),
                message,
                runtimeMs);
    }
}
