package com.jepongdevxyz.idebuild.core.build;

import com.jepongdevxyz.idebuild.core.toolchain.ToolchainInventory;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimeCapabilities;

import java.io.File;
import java.util.*;

public final class BuildPlanner {
    private BuildPlanner() {}

    public static BuildPlan planDebugBuild(ProjectRequirements req, File projectRoot, File appFilesDir, boolean offline) {
        return planBuild(req, projectRoot, appFilesDir, "assembleDebug", offline);
    }

    public static BuildPlan planBuild(ProjectRequirements req, File projectRoot, File appFilesDir, String task, boolean offline) {
        if (req == null) throw new IllegalArgumentException("req == null");
        if (projectRoot == null) throw new IllegalArgumentException("projectRoot == null");
        if (appFilesDir == null) throw new IllegalArgumentException("appFilesDir == null");
        String normalizedTask = validateTask(task);

        List<String> blockers = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>(req.getWarnings());
        List<String> args = new ArrayList<String>();
        LinkedHashMap<String, String> env = new LinkedHashMap<String, String>();
        ToolchainInventory inventory = ToolchainInventory.scan(appFilesDir);
        RuntimeCapabilities capabilities = RuntimeCapabilities.inspect(appFilesDir, req);

        if (!capabilities.isReady()) blockers.add(capabilities.getMissingRequirement());
        if (!req.isWrapperComplete() && capabilities.getGradleExecutable() != null) {
            String minimum = req.getMinimumGradleVersion();
            warnings.add("Gradle Wrapper is incomplete; using compatible DevxyzIDE internal Gradle runtime" +
                    (minimum == null ? "." : " (minimum " + minimum + ")."));
        }

        File javaHome = capabilities.getJavaHome();
        if (javaHome != null) env.put("JAVA_HOME", javaHome.getAbsolutePath());

        File gradleHome = new File(appFilesDir, "gradle-home");
        File homeDir = new File(appFilesDir, "home");
        if (homeDir.isDirectory()) gradleHome = new File(homeDir, ".gradle");
        env.put("GRADLE_USER_HOME", gradleHome.getAbsolutePath());

        File androidSdk = capabilities.getAndroidSdk();
        File aapt2 = capabilities.getAapt2();
        if (req.isAndroidProject() && androidSdk != null) {
            env.put("ANDROID_HOME", androidSdk.getAbsolutePath());
            env.put("ANDROID_SDK_ROOT", androidSdk.getAbsolutePath());
            if (req.usesNativeBuild()) {
                if (!inventory.hasNdk()) blockers.add("Native Android project detected but no NDK is installed.");
                if (!inventory.hasCmake()) warnings.add("Native build detected but no CMake installation was found; Gradle may require CMake depending on the module configuration.");
            }
        }

        args.add(normalizedTask);
        args.add("--no-daemon");
        args.add("--console=plain");
        args.add("--stacktrace");
        if (offline) args.add("--offline");
        if (req.isAndroidProject() && aapt2 != null && aapt2.isFile()) {
            args.add("-Pandroid.aapt2FromMavenOverride=" + aapt2.getAbsolutePath());
        }
        if (req.getRepositories().isEmpty()) warnings.add("No repository declaration was detected statically; Gradle may still obtain repositories from plugins or included builds.");

        return new BuildPlan(req.getJavaMajor(), javaHome, androidSdk, aapt2, args, env, blockers, warnings);
    }

    private static String validateTask(String task) {
        if (task == null || task.trim().length() == 0) throw new IllegalArgumentException("Gradle task is empty");
        String value = task.trim();
        if (!value.matches("[A-Za-z0-9_.:-]+")) throw new IllegalArgumentException("Unsafe/invalid Gradle task: " + task);
        return value;
    }
}
