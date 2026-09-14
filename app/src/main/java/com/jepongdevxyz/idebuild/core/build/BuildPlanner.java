package com.jepongdevxyz.idebuild.core.build;

import com.jepongdevxyz.idebuild.core.toolchain.ToolchainInventory;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout;

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

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>(req.getWarnings());
        List<String> args = new ArrayList<>();
        LinkedHashMap<String, String> env = new LinkedHashMap<>();
        ToolchainInventory inventory = ToolchainInventory.scan(appFilesDir);

        String minimumInternalGradle = GradleCompatibility.minimumGradleForAgp(req.getAgpVersion());
        File compatibleInternalGradle = RuntimeLayout.findGradleExecutable(appFilesDir, minimumInternalGradle);
        boolean wrapperOrInternalGradleAvailable = req.isWrapperComplete() || compatibleInternalGradle != null;
        if (!wrapperOrInternalGradleAvailable) {
            String suffix = minimumInternalGradle == null ? "" : " (Gradle " + minimumInternalGradle + "+ required by AGP " + req.getAgpVersion() + ")";
            blockers.add("No compatible Gradle launcher was found" + suffix + ". Add a complete Gradle Wrapper or install a compatible Gradle runtime in DevxyzIDE.");
        } else if (!req.isWrapperComplete()) {
            warnings.add("Gradle Wrapper is incomplete; using compatible DevxyzIDE internal Gradle runtime" + (minimumInternalGradle == null ? "." : " (minimum " + minimumInternalGradle + ")."));
        }

        int preferredJava = JvmCompatibility.recommendedJavaMajor(req.getAgpVersion(), req.getGradleVersion());
        int selectedJava = JvmCompatibility.chooseInstalledJavaMajor(appFilesDir, req.getAgpVersion(), req.getGradleVersion());
        RuntimeLayout runtime = RuntimeLayout.resolve(appFilesDir, selectedJava > 0 ? selectedJava : preferredJava);
        File javaHome = selectedJava > 0 ? runtime.getJavaHome() : null;
        if (selectedJava <= 0 || javaHome == null) {
            blockers.add("No compatible JDK is installed in DevxyzIDE toolchains (preferred JDK " + preferredJava + ").");
        } else {
            env.put("JAVA_HOME", javaHome.getAbsolutePath());
            if (selectedJava != preferredJava) warnings.add("Preferred JDK " + preferredJava + " is not installed; using compatible JDK " + selectedJava + ".");
        }

        File gradleHome = runtime.getGradleHome();
        env.put("GRADLE_USER_HOME", gradleHome.getAbsolutePath());

        File androidSdk = runtime.getAndroidSdk();
        File aapt2 = runtime.getAapt2();
        if (req.isAndroidProject()) {
            env.put("ANDROID_HOME", androidSdk.getAbsolutePath());
            env.put("ANDROID_SDK_ROOT", androidSdk.getAbsolutePath());
            if (req.getCompileSdk() > 0) {
                File androidJar = new File(androidSdk, "platforms/android-" + req.getCompileSdk() + "/android.jar");
                if (!androidJar.isFile()) blockers.add("Android SDK platform " + req.getCompileSdk() + " is not installed.");
            } else if (!androidSdk.isDirectory()) blockers.add("Android SDK is not installed and compileSdk could not be detected.");
            if (!aapt2.isFile()) blockers.add("Android-compatible aapt2 is not installed.");
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
        if (req.isAndroidProject() && aapt2.isFile()) args.add("-Pandroid.aapt2FromMavenOverride=" + aapt2.getAbsolutePath());
        if (req.getRepositories().isEmpty()) warnings.add("No repository declaration was detected statically; Gradle may still obtain repositories from plugins or included builds.");

        return new BuildPlan(selectedJava > 0 ? selectedJava : preferredJava, javaHome, androidSdk, aapt2, args, env, blockers, warnings);
    }

    private static String validateTask(String task) {
        if (task == null || task.trim().isEmpty()) throw new IllegalArgumentException("Gradle task is empty");
        String value = task.trim();
        if (!value.matches("[A-Za-z0-9_.:-]+")) throw new IllegalArgumentException("Unsafe/invalid Gradle task: " + task);
        return value;
    }
}
