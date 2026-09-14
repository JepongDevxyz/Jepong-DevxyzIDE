package com.jepongdevxyz.idebuild;

import com.jepongdevxyz.idebuild.core.build.BuildPlan;
import com.jepongdevxyz.idebuild.core.build.BuildPlanner;
import com.jepongdevxyz.idebuild.core.build.ProjectAnalyzer;
import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainProvisioningPlan;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BuildRunner {
    public interface Listener {
        void onLine(String line);
        void onFinished(int exitCode, File apk);
    }

    private BuildRunner() {}

    public static void runDebugBuild(File projectRoot, File appFilesDir, Listener listener) {
        runBuild(projectRoot, appFilesDir, "assembleDebug", false, listener);
    }

    public static void runBuild(final File projectRoot, final File appFilesDir, final String task, final boolean offline, final Listener listener) {
        if (listener == null) throw new IllegalArgumentException("listener == null");
        new Thread(new Runnable() {
            @Override public void run() {
                BuildRunner.run(projectRoot, appFilesDir, task, offline, listener);
            }
        }, "DevxyzIDE-Gradle").start();
    }

    private static void run(File projectRoot, File appFilesDir, String task, boolean offline, Listener listener) {
        try {
            ProjectRequirements requirements = ProjectAnalyzer.analyze(projectRoot);
            emitProjectReport(requirements, listener);
            ToolchainProvisioningPlan provisioning = ToolchainProvisioningPlan.create(requirements, appFilesDir);
            listener.onLine("Toolchain provisioning: " + provisioning.summary());

            BuildPlan plan = BuildPlanner.planBuild(requirements, projectRoot, appFilesDir, task, offline);
            for (String warning : plan.getWarnings()) listener.onLine("PRECHECK WARNING: " + warning);
            if (!plan.canBuild()) {
                for (String blocker : plan.getBlockers()) listener.onLine("PRECHECK BLOCKER: " + blocker);
                listener.onLine("BUILD NOT STARTED: install the missing DevxyzIDE toolchain components first.");
                listener.onFinished(3, null);
                return;
            }

            File wrapper = new File(projectRoot, "gradlew");
            File internalGradle = com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout.findGradleExecutable(appFilesDir);
            File gradleLauncher = requirements.isWrapperComplete() ? wrapper : internalGradle;
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

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectRoot);
            pb.redirectErrorStream(true);
            Map<String, String> env = pb.environment();
            env.putAll(plan.getEnvironment());
            env.put("HOME", appFilesDir.getAbsolutePath());
            String oldPath = env.get("PATH");
            env.put("PATH", javaBinary.getParentFile().getAbsolutePath() + File.pathSeparator + (oldPath == null ? "" : oldPath));

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) listener.onLine(line);
            }
            int exit = process.waitFor();
            File apk = exit == 0 ? ApkLocator.findDebugApk(projectRoot) : null;
            listener.onFinished(exit, apk);
        } catch (Exception e) {
            listener.onLine("BUILD ERROR: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
            listener.onFinished(1, null);
        }
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
