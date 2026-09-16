package com.jepongdevxyz.idebuild.core.capability;

import com.jepongdevxyz.idebuild.BuildRunner;
import com.jepongdevxyz.idebuild.core.build.ProjectAnalyzer;
import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;
import com.jepongdevxyz.idebuild.core.git.GitService;
import com.jepongdevxyz.idebuild.core.terminal.TerminalCommandPlanner;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainProvisioningPlan;

import java.io.File;

public final class CapabilityRegistry {
    private CapabilityRegistry() {}

    public static Capability apkSigning(File appFilesDir, File inputApk, boolean keystoreSelected) {
        File signer = appFilesDir == null ? null : RuntimeLayout.findApksigner(appFilesDir);
        if (inputApk == null || !inputApk.isFile()) {
            return new Capability(
                    "apk.signing",
                    "APK Signing",
                    CapabilityStatus.UNAVAILABLE,
                    "Build a readable APK before signing.");
        }
        if (signer == null || !signer.isFile()) {
            return new Capability(
                    "apk.signing",
                    "APK Signing",
                    CapabilityStatus.NEEDS_INSTALL,
                    "APK signer component is not installed.");
        }
        if (!keystoreSelected) {
            return new Capability(
                    "apk.signing",
                    "APK Signing",
                    CapabilityStatus.UNAVAILABLE,
                    "Select a JKS or compatible keystore.");
        }
        return new Capability(
                "apk.signing",
                "APK Signing",
                CapabilityStatus.AVAILABLE,
                "APK signing is ready.");
    }

    public static Capability gradleBuild(File projectRoot, File appFilesDir) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return new Capability(
                    "gradle.build",
                    "Gradle Build",
                    CapabilityStatus.UNAVAILABLE,
                    "Open a Gradle project before running build actions.");
        }
        boolean hasBuildFile = new File(projectRoot, "build.gradle").isFile()
                || new File(projectRoot, "build.gradle.kts").isFile()
                || new File(projectRoot, "settings.gradle").isFile()
                || new File(projectRoot, "settings.gradle.kts").isFile();
        if (!hasBuildFile) {
            return new Capability(
                    "gradle.build",
                    "Gradle Build",
                    CapabilityStatus.UNSUPPORTED,
                    "This folder does not look like a Gradle project.");
        }
        if (appFilesDir == null || !appFilesDir.isDirectory()) {
            return new Capability(
                    "gradle.build",
                    "Gradle Build",
                    CapabilityStatus.NEEDS_INSTALL,
                    "DevxyzIDE runtime directory is unavailable. Install the build runtime first.");
        }

        File gradle = BuildRunner.findGradleExecutable(projectRoot, appFilesDir);
        if (gradle == null || !gradle.isFile()) {
            return new Capability(
                    "gradle.build",
                    "Gradle Build",
                    CapabilityStatus.NEEDS_INSTALL,
                    "No project Gradle Wrapper or compatible internal Gradle runtime is installed.");
        }

        try {
            ProjectRequirements requirements = ProjectAnalyzer.analyze(projectRoot);
            ToolchainProvisioningPlan provisioning = ToolchainProvisioningPlan.create(requirements, appFilesDir);
            if (!provisioning.isReady()) {
                return new Capability(
                        "gradle.build",
                        "Gradle Build",
                        CapabilityStatus.NEEDS_INSTALL,
                        "Build toolchain needs installation. Missing components: "
                                + provisioning.getRequiredComponents()
                                + ". Install the required JDK/Android SDK components.");
            }
        } catch (Exception error) {
            return new Capability(
                    "gradle.build",
                    "Gradle Build",
                    CapabilityStatus.UNAVAILABLE,
                    "Project toolchain inspection failed: " + safeMessage(error));
        }

        return new Capability(
                "gradle.build",
                "Gradle Build",
                CapabilityStatus.AVAILABLE,
                "Gradle build actions are ready.");
    }

    public static Capability terminal(File appFilesDir) {
        try {
            String shell = TerminalCommandPlanner.describeShell(appFilesDir);
            if (shell == null || shell.length() == 0) {
                return new Capability(
                        "terminal",
                        "Terminal",
                        CapabilityStatus.UNAVAILABLE,
                        "No shell runtime was detected.");
            }
            if ("/system/bin/sh".equals(shell)) {
                return new Capability(
                        "terminal",
                        "Terminal",
                        CapabilityStatus.EXPERIMENTAL,
                        "Using Android system shell. Install the Devxyz terminal runtime for the full toolchain shell.");
            }
            return new Capability(
                    "terminal",
                    "Terminal",
                    CapabilityStatus.AVAILABLE,
                    "Terminal runtime shell is ready.");
        } catch (Exception error) {
            return new Capability(
                    "terminal",
                    "Terminal",
                    CapabilityStatus.UNAVAILABLE,
                    "Shell detection failed: " + safeMessage(error));
        }
    }

    public static Capability git(File workingDirectory) {
        if (workingDirectory == null || !workingDirectory.isDirectory()) {
            return new Capability(
                    "git",
                    "Git",
                    CapabilityStatus.UNAVAILABLE,
                    "Open or choose a working directory before using Git.");
        }
        if (!GitService.isGitAvailable(workingDirectory)) {
            return new Capability(
                    "git",
                    "Git",
                    CapabilityStatus.NEEDS_INSTALL,
                    "Git executable is unavailable on this runtime.");
        }
        return new Capability(
                "git",
                "Git",
                CapabilityStatus.AVAILABLE,
                "Git executable is ready.");
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().length() == 0 ? error.getClass().getSimpleName() : message;
    }
}
