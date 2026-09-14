package com.jepongdevxyz.idebuild.core.toolchain;

import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;
import java.io.File;

/** Resolves a project requirement set against installed app-private toolchains. */
public final class RuntimeCapabilities {
    private final File javaHome;
    private final File gradleExecutable;
    private final File androidSdk;
    private final File aapt2;
    private final boolean ready;
    private final String missingRequirement;

    private RuntimeCapabilities(File javaHome, File gradleExecutable, File androidSdk,
                                File aapt2, boolean ready, String missingRequirement) {
        this.javaHome = javaHome;
        this.gradleExecutable = gradleExecutable;
        this.androidSdk = androidSdk;
        this.aapt2 = aapt2;
        this.ready = ready;
        this.missingRequirement = missingRequirement;
    }

    public static RuntimeCapabilities inspect(File appFilesDir, ProjectRequirements requirements) {
        if (appFilesDir == null) throw new IllegalArgumentException("appFilesDir == null");
        if (requirements == null) throw new IllegalArgumentException("requirements == null");

        int javaMajor = requirements.getJavaMajor();
        File javaHome = RuntimeLayout.findJavaHome(appFilesDir, javaMajor);
        if (javaHome == null) {
            return missing(null, null, RuntimeLayout.findAndroidSdk(appFilesDir), null,
                    "JDK " + javaMajor + " required");
        }

        String minimumGradle = requirements.getMinimumGradleVersion();
        File gradle = RuntimeLayout.findGradleExecutable(appFilesDir, minimumGradle);
        if (!requirements.isWrapperComplete() && gradle == null) {
            String message = minimumGradle == null ? "Compatible Gradle runtime required" : "Gradle " + minimumGradle + "+ required";
            return missing(javaHome, null, RuntimeLayout.findAndroidSdk(appFilesDir), null, message);
        }

        File sdk = RuntimeLayout.findAndroidSdk(appFilesDir);
        File aapt2 = RuntimeLayout.findAapt2(appFilesDir, sdk);
        if (requirements.isAndroidProject()) {
            int compileSdk = requirements.getCompileSdk();
            if (compileSdk > 0) {
                File androidJar = new File(sdk, "platforms/android-" + compileSdk + "/android.jar");
                if (!androidJar.isFile()) {
                    return missing(javaHome, gradle, sdk, aapt2,
                            "Android SDK platform " + compileSdk + " missing");
                }
            } else if (!sdk.isDirectory()) {
                return missing(javaHome, gradle, sdk, aapt2, "Android SDK required");
            }
            if (aapt2 == null || !aapt2.isFile()) {
                return missing(javaHome, gradle, sdk, aapt2, "Android build-tools/aapt2 missing");
            }
        }

        return new RuntimeCapabilities(javaHome, gradle, sdk, aapt2, true, null);
    }

    private static RuntimeCapabilities missing(File javaHome, File gradle, File sdk, File aapt2, String message) {
        return new RuntimeCapabilities(javaHome, gradle, sdk, aapt2, false, message);
    }

    public File getJavaHome() { return javaHome; }
    public File getGradleExecutable() { return gradleExecutable; }
    public File getAndroidSdk() { return androidSdk; }
    public File getAapt2() { return aapt2; }
    public boolean isReady() { return ready; }
    public String getMissingRequirement() { return missingRequirement; }
}
