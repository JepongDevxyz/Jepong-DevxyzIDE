package com.jepongdevxyz.idebuild.core.capability;

import com.jepongdevxyz.idebuild.BuildRunner;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout;

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
        File gradle = BuildRunner.findGradleExecutable(projectRoot, appFilesDir);
        if (gradle == null || !gradle.isFile()) {
            return new Capability(
                    "gradle.build",
                    "Gradle Build",
                    CapabilityStatus.NEEDS_INSTALL,
                    "No project Gradle Wrapper or compatible internal Gradle runtime is installed.");
        }
        return new Capability(
                "gradle.build",
                "Gradle Build",
                CapabilityStatus.AVAILABLE,
                "Gradle build actions are ready.");
    }
}