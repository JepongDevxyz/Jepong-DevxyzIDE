package com.jepongdevxyz.idebuild.core.signing;

import com.jepongdevxyz.idebuild.core.process.ProcessEngine;
import com.jepongdevxyz.idebuild.core.process.ProcessRequest;
import com.jepongdevxyz.idebuild.core.process.ProcessResult;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Executes Android SDK apksigner without putting passwords in command arguments. */
public final class ApkSignerService {
    private static final String STORE_PASSWORD_ENV = "DEVXYZ_KS_PASS";
    private static final String KEY_PASSWORD_ENV = "DEVXYZ_KEY_PASS";
    private static final long TIMEOUT_SECONDS = 120L;

    private ApkSignerService() { }

    public static ApkSigningResult signAndVerify(File apksigner,
                                                 File inputApk,
                                                 File outputApk,
                                                 File keyStore,
                                                 String alias,
                                                 String storePassword,
                                                 String keyPassword) {
        requireExecutable(apksigner);
        requireFile(inputApk, "input APK");
        requireFile(keyStore, "keystore");
        if (outputApk == null) throw new IllegalArgumentException("output APK must not be null");
        requireText(alias, "alias");
        requireText(storePassword, "store password");
        if (keyPassword == null || keyPassword.length() == 0) keyPassword = storePassword;

        File parent = outputApk.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Could not create output directory");
        }
        if (outputApk.exists() && !outputApk.delete()) {
            throw new IllegalArgumentException("Could not replace output APK");
        }

        Map<String, String> environment = new HashMap<String, String>();
        environment.put(STORE_PASSWORD_ENV, storePassword);
        environment.put(KEY_PASSWORD_ENV, keyPassword);
        ArrayList<String> secrets = new ArrayList<String>();
        secrets.add(storePassword);
        if (!storePassword.equals(keyPassword)) secrets.add(keyPassword);

        List<String> signCommand = list(
                apksigner.getAbsolutePath(),
                "sign",
                "--ks", keyStore.getAbsolutePath(),
                "--ks-key-alias", alias,
                "--ks-pass", "env:" + STORE_PASSWORD_ENV,
                "--key-pass", "env:" + KEY_PASSWORD_ENV,
                "--out", outputApk.getAbsolutePath(),
                inputApk.getAbsolutePath());
        CapturedProcess sign = run(signCommand, inputApk.getParentFile(), environment, secrets);
        if (!sign.success() || !outputApk.isFile() || outputApk.length() <= 0L) {
            return new ApkSigningResult(false, sign.exitCode, -1,
                    sign.stdout, sign.stderr, "", "Signed APK was not produced or sign command failed.\n");
        }

        List<String> verifyCommand = list(
                apksigner.getAbsolutePath(),
                "verify",
                "--verbose",
                "--print-certs",
                outputApk.getAbsolutePath());
        CapturedProcess verify = run(verifyCommand, outputApk.getParentFile(),
                new HashMap<String, String>(), secrets);
        boolean success = verify.success() && outputApk.isFile() && outputApk.length() > 0L;
        return new ApkSigningResult(success,
                sign.exitCode,
                verify.exitCode,
                sign.stdout,
                sign.stderr,
                verify.stdout,
                verify.stderr);
    }

    private static CapturedProcess run(List<String> command,
                                       File workingDirectory,
                                       Map<String, String> environment,
                                       List<String> secrets) {
        final StringBuilder stdout = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();
        final ProcessResult[] finalResult = new ProcessResult[1];
        final CountDownLatch done = new CountDownLatch(1);

        ProcessRequest request = new ProcessRequest(command, workingDirectory, environment, false, secrets);
        final ProcessEngine.RunningProcess running = ProcessEngine.start(request, new ProcessEngine.Listener() {
            @Override public void onStdout(String line) { append(stdout, line); }
            @Override public void onStderr(String line) { append(stderr, line); }
            @Override public void onFinished(ProcessResult result) {
                finalResult[0] = result;
                done.countDown();
            }
        });

        try {
            if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                running.cancel();
                done.await(5L, TimeUnit.SECONDS);
                if (finalResult[0] == null) {
                    append(stderr, "APK signer timed out");
                    return new CapturedProcess(124, true, stdout.toString(), stderr.toString());
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            running.cancel();
            append(stderr, "APK signer interrupted");
            return new CapturedProcess(130, true, stdout.toString(), stderr.toString());
        }

        ProcessResult result = finalResult[0];
        if (result == null) return new CapturedProcess(1, false, stdout.toString(), stderr.toString());
        return new CapturedProcess(result.getExitCode(), result.isCancelled(), stdout.toString(), stderr.toString());
    }

    private static void requireExecutable(File file) {
        if (file == null || !file.isFile()) throw new IllegalArgumentException("apksigner executable is missing");
        if (!file.canExecute()) throw new IllegalArgumentException("apksigner is not executable: " + file.getAbsolutePath());
    }

    private static void requireFile(File file, String label) {
        if (file == null || !file.isFile()) throw new IllegalArgumentException(label + " is missing");
    }

    private static void requireText(String value, String label) {
        if (value == null || value.trim().length() == 0) throw new IllegalArgumentException(label + " must not be blank");
    }

    private static void append(StringBuilder builder, String line) {
        if (line != null) builder.append(line).append('\n');
    }

    private static List<String> list(String... values) {
        ArrayList<String> result = new ArrayList<String>();
        if (values != null) for (String value : values) result.add(value);
        return result;
    }

    private static final class CapturedProcess {
        private final int exitCode;
        private final boolean cancelled;
        private final String stdout;
        private final String stderr;

        private CapturedProcess(int exitCode, boolean cancelled, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.cancelled = cancelled;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }

        private boolean success() { return !cancelled && exitCode == 0; }
    }
}
