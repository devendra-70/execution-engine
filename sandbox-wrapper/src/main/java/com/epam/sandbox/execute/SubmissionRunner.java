package com.epam.sandbox.execute;

import com.epam.sandbox.compile.InMemoryJavaCompiler;
import com.epam.sandbox.protocol.Frame;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-submission lifecycle owner:
 *   1. {@link #compile(String, String)}    — done once per submission (SRS §6.2 step 1)
 *   2. {@link #runTestCase(String, long)}  — called per test case (SRS §6.2 step 2)
 *   3. {@link #reset()}                    — drops bytecode at end of submission
 *   4. {@link #close()}                    — shuts the runner thread down
 *
 * Subtask EPMICMPCOD-454: timeout enforcement and resource cleanup.
 *
 * SRS §10 makes the orchestrator authoritative for TLE (it forcibly SIGKILLs
 * the container). The wrapper-side timeout implemented here is a defensive
 * soft-cap satisfying subtask 454; it does not contradict §10.
 */
public final class SubmissionRunner implements AutoCloseable {

    private final InMemoryJavaCompiler compiler;
    private final ReflectiveExecutor reflectiveExecutor;
    private final ExecutorService runner;

    private Map<String, byte[]> compiledBytecode;
    private String compiledClassName;

    public SubmissionRunner() {
        this(new InMemoryJavaCompiler(), new ReflectiveExecutor());
    }

    SubmissionRunner(InMemoryJavaCompiler compiler, ReflectiveExecutor reflectiveExecutor) {
        this.compiler = compiler;
        this.reflectiveExecutor = reflectiveExecutor;
        AtomicLong counter = new AtomicLong();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "sandbox-runner-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.runner = Executors.newSingleThreadExecutor(tf);
    }

    /**
     * Compile the source for this submission. On failure returns the
     * formatted error and clears any previously held bytecode.
     */
    public InMemoryJavaCompiler.Result compile(String className, String sourceCode) {
        InMemoryJavaCompiler.Result r = compiler.compile(className, sourceCode);
        if (r.isSuccess()) {
            this.compiledBytecode = r.getClasses();
            this.compiledClassName = className;
        } else {
            reset();
        }
        return r;
    }

    public boolean isReady() {
        return compiledBytecode != null;
    }

    /**
     * Execute a single test case against the previously-compiled bytecode.
     *
     * @param stdin        stdin payload (UTF-8)
     * @param timeoutMs    per-test-case soft cap; {@code 0} or negative disables the cap
     * @return a {@code result} frame as JSON (already serialised), ready to write
     *         to the response channel
     * @throws IllegalStateException if {@link #compile(String, String)} has not
     *         succeeded for the current submission
     */
    public String runTestCase(String id, String stdin, long timeoutMs) {
        if (!isReady()) {
            throw new IllegalStateException("Submission not compiled");
        }

        final Map<String, byte[]> bc = compiledBytecode;
        final String className = compiledClassName;

        Callable<ReflectiveExecutor.Outcome> task =
                () -> reflectiveExecutor.execute(bc, className, stdin);
        Future<ReflectiveExecutor.Outcome> future = runner.submit(task);

        try {
            ReflectiveExecutor.Outcome o = (timeoutMs > 0)
                    ? future.get(timeoutMs, TimeUnit.MILLISECONDS)
                    : future.get();

            Frame.ResultStatus status = (o.getStatus() == ReflectiveExecutor.Outcome.Status.OK)
                    ? Frame.ResultStatus.OK
                    : Frame.ResultStatus.RUNTIME_ERROR;
            return Frame.result(id, status,
                    o.getStdout(), o.getStderr(), o.getRuntimeMs(), o.getErrorMessage());

        } catch (TimeoutException te) {
            future.cancel(true);
            return Frame.result(id, Frame.ResultStatus.TIME_LIMIT_EXCEEDED,
                    "", "", timeoutMs,
                    "Submission exceeded timeout of " + timeoutMs + "ms");

        } catch (java.util.concurrent.CancellationException ce) {
            // Handle explicit cancellation (Fix #3 — CancellationException coverage)
            return Frame.result(id, Frame.ResultStatus.TIME_LIMIT_EXCEEDED,
                    "", "", timeoutMs,
                    "Task cancelled due to timeout");

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return Frame.result(id, Frame.ResultStatus.RUNTIME_ERROR,
                    "", "", 0L, "Interrupted");

        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() == null ? ee : ee.getCause();
            String errorMsg = (cause == null ? "Unknown error" :
                cause.getClass().getName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage()));
            return Frame.result(id, Frame.ResultStatus.RUNTIME_ERROR,
                    "", "", 0L, errorMsg);  // Fix #9 — null-safe error message
        }
    }

    /** Drop bytecode for the current submission (called on {@code end} or {@code compile_error}). */
    public void reset() {
        this.compiledBytecode = null;
        this.compiledClassName = null;
    }

    @Override
    public void close() {
        runner.shutdownNow();
        try {
            boolean terminated = runner.awaitTermination(2, TimeUnit.SECONDS);
            if (!terminated) {
                // Force shutdown if graceful termination times out (Fix #4 — ExecutorService robustness)
                runner.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
