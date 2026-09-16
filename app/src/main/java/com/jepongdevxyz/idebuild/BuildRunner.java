package com.jepongdevxyz.idebuild;

import com.jepongdevxyz.idebuild.core.build.BuildArtifact;
import com.jepongdevxyz.idebuild.core.build.BuildOutputScanner;
import com.jepongdevxyz.idebuild.core.build.BuildPlan;
import com.jepongdevxyz.idebuild.core.build.BuildPlanner;
import com.jepongdevxyz.idebuild.core.build.BuildTaskPolicy;
import com.jepongdevxyz.idebuild.core.build.ProjectAnalyzer;
import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;
import com.jepongdevxyz.idebuild.core.process.ProcessEngine;
import com.jepongdevxyz.idebuild.core.process.ProcessRequest;
import com.jepongdevxyz.idebuild.core.process.ProcessResult;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainProvisioningPlan;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BuildRunner {
    public interface Listener {
        void onLine(String line);
        void onFinished(int exitCode, File apk);
    }

    public static final class BuildHandle {
        private volatile boolean cancelled;
        private volatile boolean finished;
        private volatile ProcessEngine.RunningProcess runningProcess;

        private BuildHandle() { }

        public void cancel() {
            cancelled = true;
            ProcessEngine.RunningProcess running = runningProcess;
            if (running != null) running.cancel();
        }

        public boolean isCancelled() { return cancelled; }
        public boolean isFinished() { return finished; }
    }

    private BuildRunner() {}

    public static BuildHandle runDebugBuild(File projectRoot, File appFilesDir, Listener listener) {
        return runBuild(projectRoot, appFilesDir, "assembleDebug", false, listener);
    }

    public static File findGradleExecutable(File projectRoot, File appFilesDir) {
        try {
            ProjectRequirements requirements = ProjectAnalyzer.analyze(projectRoot);
            File wrapper = new File(projectRoot, "gradlew");
            if (requirements.isWrapperComplete() && wrapper.isFile()) return wrapper;
            String minimumInternalGradle = com.jepongdevxyz.idebuild.core.build.GradleCompatibility.minimumGradleForAgp(requirements.getAgpVersion());
            return com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout.findGradleExecutable(appFilesDir, minimumInternalGradle);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static BuildHandle runBuild(final File projectRoot,
                                       final File appFilesDir,
                                       final String task,
                                       final boolean offline,
                                       final Listener listener) {
        if (listener == null) throw new IllegalArgumentException("listener == null");
        final BuildHandle handle = new BuildHandle();
        new Thread(new Runnable() {
            @Override public void run() {
                BuildRunner.prepareAndStart(projectRoot, appFilesDir, task, offline, listener, handle);
            }
        }, "DevxyzIDE-Gradle-Prepare").start();
        return handle;
    }

    private static void prepareAndStart(final File projectRoot,
                                        final File appFilesDir,
                                        final String task,
                                        final boolean offline,
                                        final Listener listener,
                                        final BuildHandle handle) {
        try {
            if (handle.cancelled) {
                finish(handle, listener, 130, null);
                return;
            }

            ProjectRequirements requirements = ProjectAnalyzer.analyze(projectRoot);
            emitProjectReport(requirements, listener);
            ToolchainProvisioningPlan provisioning = ToolchainProvisioningPlan.create(requirements, appFilesDir);
            listener.onLine("Toolchain provisioning: " + provisioning.summary());

            BuildPlan plan = BuildPlanner.planBuild(requirements, projectRoot, appFilesDir, task, offline);
            for (String warning : plan.getWarnings()) listener.onLine("PRECHECK WARNING: " + warning);
            if (!plan.canBuild()) {
                for (String blocker : plan.getBlockers()) listener.onLine("PRECHECK BLOCKER: " + blocker);
                listener.onLine("BUILD NOT STARTED: install the missing DevxyzIDE toolchain components first.");
                finish(handle, listener, 3, null);
                return;
            }

            File wrapper = new File(projectRoot, "gradlew");
            String minimumInternalGradle = com.jepongdevxyz.idebuild.core.build.GradleCompatibility.minimumGradleForAgp(requirements.getAgpVersion());
            File internalGradle = com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout.findGradleExecutable(appFilesDir, minimumInternalGradle);
            final File gradleLauncher = requirements.isWrapperComplete() ? wrapper : internalGradle;
            if (gradleLauncher == null || !gradleLauncher.isFile()) throw new IOException("No usable Gradle launcher found");
            File javaBinary = new File(plan.getJavaHome(), "bin/java");
            File aapt2 = plan.getAapt2();
            ensureExecutable(javaBinary);
            if (aapt2 != null && aapt2.isFile()) ensureExecutable(aapt2);

            File gradleHome = new File(plan.getEnvironment().get("GRADLE_USER_HOME"));
            if (!gradleHome.exists() && !gradleHome.mkdirs()) {
                throw new IOException("Cannot create Gradle cache: " + gradleHome);
            }

            List<String> command = new ArrayList<String>();
            command.add("/system/bin/sh");
            command.add(gradleLauncher.getAbsolutePath());
            command.addAll(plan.getArguments());

            listener.onLine("JDK: " + plan.getJavaMajor() + " @ " + plan.getJavaHome().getAbsolutePath());
            if (requirements.isAndroidProject()) {
                listener.onLine("Android SDK: " + plan.getAndroidSdk().getAbsolutePath());
                listener.onLine("Android aapt2 override: " + plan.getAapt2().getAbsolutePath());
            }
            listener.onLine("Gradle launcher: " + gradleLauncher.getAbsolutePath());
            listener.onLine("Gradle cache: " + gradleHome.getAbsolutePath());
            listener.onLine("Maven dependency mode: " + (offline ? "OFFLINE (cache only)" : "ONLINE (download + persistent cache)"));
            listener.onLine("Maven cache: " + new File(gradleHome, "caches/modules-2/files-2.1").getAbsolutePath());
            listener.onLine("Gradle command: " + gradleLauncher.getName() + " " + joinArgumentsForDisplay(plan.getArguments()));

            Map<String, String> environment = new HashMap<String, String>();
            environment.putAll(plan.getEnvironment());
            environment.put("HOME", appFilesDir.getAbsolutePath());
            String oldPath = System.getenv("PATH");
            environment.put("PATH", javaBinary.getParentFile().getAbsolutePath() + File.pathSeparator + (oldPath == null ? "" : oldPath));

            if (handle.cancelled) {
                finish(handle, listener, 130, null);
                return;
            }

            ProcessRequest request = new ProcessRequest(
                    command,
                    projectRoot,
                    environment,
                    true,
                    new ArrayList<String>());

            ProcessEngine.RunningProcess running = ProcessEngine.start(request, new ProcessEngine.Listener() {
                @Override public void onStdout(String line) { listener.onLine(line); }
                @Override public void onStderr(String line) { listener.onLine(line); }
                @Override public void onFinished(ProcessResult result) {
                    int exit = result.isCancelled() ? 130 : result.getExitCode();
                    File apk = null;
                    if (exit == 0) {
                        reportBuildArtifacts(projectRoot, listener);
                        String expectedVariant = BuildTaskPolicy.expectedApkVariant(task);
                        if (expectedVariant != null) {
                            apk = ApkLocator.findApk(projectRoot, expectedVariant);
                            if (apk == null) {
                                listener.onLine("BUILD OUTPUT ERROR: Gradle exited successfully but no structurally valid " + expectedVariant + " APK was found.");
                                exit = 4;
                            } else {
                                listener.onLine("Verified APK output: " + apk.getAbsolutePath());
                            }
                        } else {
                            listener.onLine("Gradle task completed successfully; this task does not require an APK output.");
                        }
                    }
                    finish(handle, listener, exit, apk);
                }
            });
            handle.runningProcess = running;
            if (handle.cancelled) running.cancel();
        } catch (Exception e) {
            listener.onLine("BUILD ERROR: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
            finish(handle, listener, handle.cancelled ? 130 : 1, null);
        }
    }

    private static void reportBuildArtifacts(File projectRoot, Listener listener) {
        try {
            List<BuildArtifact> artifacts = BuildOutputScanner.scan(projectRoot, 50000);
            if (artifacts.isEmpty()) {
                listener.onLine("Build artifacts: none detected under */build/outputs.");
                return;
            }
            listener.onLine("Build artifacts (" + artifacts.size() + "):");
            for (BuildArtifact artifact : artifacts) listener.onLine("  - " + describeArtifact(artifact));
        } catch (Exception e) {
            listener.onLine("BUILD OUTPUT WARNING: artifact scan failed: " + safeMessage(e));
        }
    }

    public static String describeArtifact(BuildArtifact artifact) {
        if (artifact == null) throw new IllegalArgumentException("artifact == null");
        return artifact.getType()
                + " · variant=" + artifact.getVariant()
                + " · " + formatBytes(artifact.getSizeBytes())
                + " · modified=" + formatModifiedTime(artifact.getModifiedTimeMillis())
                + " · " + artifact.getRelativePath();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes / 1024.0;
        if (value < 1024.0) return String.format(Locale.US, "%.1f KB", value);
        value /= 1024.0;
        if (value < 1024.0) return String.format(Locale.US, "%.1f MB", value);
        value /= 1024.0;
        return String.format(Locale.US, "%.1f GB", value);
    }

    private static String formatModifiedTime(long millis) {
        if (millis <= 0L) return "unknown";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    private static synchronized void finish(BuildHandle handle, Listener listener, int exitCode, File apk) {
        if (handle.finished) return;
        handle.finished = true;
        handle.runningProcess = null;
        listener.onFinished(exitCode, apk);
    }

    public static String describeProject(ProjectRequirements requirements) {
        StringBuilder out = new StringBuilder();
        out.append("Project: ").append(requirements.summary());
        if (!requirements.getRepositories().isEmpty()) {
            out.append("\nRepositories:");
            for (String repo : requirements.getRepositories()) out.append("\n  - ").append(repo);
        }
        return out.toString();
    }

    private static void emitProjectReport(ProjectRequirements requirements, Listener listener) {
        String[] lines = describeProject(requirements).split("\\n");
        for (String line : lines) listener.onLine(line);
    }

    private static void ensureExecutable(File file) throws IOException {
        if (!file.isFile()) throw new IOException("Required executable missing: " + file);
        if (!file.canExecute() && !file.setExecutable(true, false)) {
            throw new IOException("Cannot mark executable: " + file);
        }
    }

    private static String joinArgumentsForDisplay(List<String> args) {
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            if (out.length() > 0) out.append(' ');
            out.append(arg.indexOf(' ') >= 0 ? '"' + arg + '"' : arg);
        }
        return out.toString();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? "No details" : message;
    }
}
