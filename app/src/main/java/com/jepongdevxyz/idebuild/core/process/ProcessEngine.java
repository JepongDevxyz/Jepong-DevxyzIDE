package com.jepongdevxyz.idebuild.core.process;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Shared process runner for build, terminal, Git and signing tools.
 * It separates stdout/stderr, supports cancellation, and redacts configured secrets.
 */
public final class ProcessEngine {
    public interface Listener {
        void onStdout(String line);
        void onStderr(String line);
        void onFinished(ProcessResult result);
    }

    public static final class RunningProcess {
        private volatile Process process;
        private volatile boolean cancelled;
        private volatile boolean finished;

        private RunningProcess() { }

        public void cancel() {
            cancelled = true;
            Process current = process;
            if (current != null) {
                current.destroy();
                closeQuietly(current.getInputStream());
                closeQuietly(current.getErrorStream());
                closeQuietly(current.getOutputStream());
            }
        }

        public boolean isCancelled() { return cancelled; }
        public boolean isFinished() { return finished; }
    }

    private ProcessEngine() { }

    public static RunningProcess start(final ProcessRequest request, final Listener listener) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        final RunningProcess handle = new RunningProcess();
        Thread worker = new Thread(new Runnable() {
            @Override public void run() { execute(request, listener, handle); }
        }, "DevxyzIDE-Process");
        worker.start();
        return handle;
    }

    private static void execute(ProcessRequest request, Listener listener, RunningProcess handle) {
        long startedAt = System.currentTimeMillis();
        int exitCode = 1;
        Process process = null;
        Thread stdoutThread = null;
        Thread stderrThread = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(request.getCommand());
            if (request.getWorkingDirectory() != null) builder.directory(request.getWorkingDirectory());
            builder.redirectErrorStream(request.isMergeErrorStream());
            Map<String, String> environment = builder.environment();
            environment.putAll(request.getEnvironment());

            if (handle.cancelled) {
                exitCode = 130;
                return;
            }

            process = builder.start();
            handle.process = process;
            if (handle.cancelled) process.destroy();

            stdoutThread = streamThread(
                    process.getInputStream(),
                    request.getSecretsToRedact(),
                    listener,
                    true,
                    "DevxyzIDE-stdout");
            stdoutThread.start();

            if (!request.isMergeErrorStream()) {
                stderrThread = streamThread(
                        process.getErrorStream(),
                        request.getSecretsToRedact(),
                        listener,
                        false,
                        "DevxyzIDE-stderr");
                stderrThread.start();
            }

            exitCode = process.waitFor();
            joinQuietly(stdoutThread);
            joinQuietly(stderrThread);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            handle.cancelled = true;
            if (process != null) process.destroy();
            exitCode = 130;
        } catch (IOException launchFailure) {
            safeStderr(listener, redact(
                    "PROCESS ERROR: " + safeMessage(launchFailure),
                    request.getSecretsToRedact()));
            exitCode = 1;
        } finally {
            handle.process = null;
            handle.finished = true;
            long duration = System.currentTimeMillis() - startedAt;
            safeFinished(listener, new ProcessResult(exitCode, handle.cancelled, duration));
        }
    }

    private static Thread streamThread(final InputStream stream,
                                       final List<String> secrets,
                                       final Listener listener,
                                       final boolean stdout,
                                       String name) {
        return new Thread(new Runnable() {
            @Override public void run() {
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String safe = redact(line, secrets);
                        if (stdout) safeStdout(listener, safe);
                        else safeStderr(listener, safe);
                    }
                } catch (IOException ignored) {
                    // Stream closure is expected during cancellation.
                } finally {
                    closeQuietly(reader);
                }
            }
        }, name);
    }

    public static String redact(String value, List<String> secrets) {
        String safe = value == null ? "" : value;
        if (secrets == null) return safe;
        for (String secret : secrets) {
            if (secret != null && secret.length() > 0) safe = safe.replace(secret, "***");
        }
        return safe;
    }

    private static void safeStdout(Listener listener, String line) {
        try { listener.onStdout(line); } catch (RuntimeException ignored) { }
    }

    private static void safeStderr(Listener listener, String line) {
        try { listener.onStderr(line); } catch (RuntimeException ignored) { }
    }

    private static void safeFinished(Listener listener, ProcessResult result) {
        try { listener.onFinished(result); } catch (RuntimeException ignored) { }
    }

    private static void joinQuietly(Thread thread) throws InterruptedException {
        if (thread != null) thread.join(2000L);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().length() == 0 ? exception.getClass().getSimpleName() : message;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) try { closeable.close(); } catch (IOException ignored) { }
    }
}
