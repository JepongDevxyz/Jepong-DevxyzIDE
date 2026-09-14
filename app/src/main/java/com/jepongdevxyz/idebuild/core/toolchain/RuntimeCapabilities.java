package com.jepongdevxyz.idebuild.core.toolchain;

import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

        List<String> missing = new ArrayList<String>();
        int javaMajor = requirements.getJavaMajor();
        File javaHome = RuntimeLayout.findJavaHome(appFilesDir, javaMajor);
        if (javaHome == null) missing.add("JDK " + javaMajor + " required");

        String minimumGradle = requirements.getMinimumGradleVersion();
        File gradle = RuntimeLayout.findGradleExecutable(appFilesDir, minimumGradle);
        if (!requirements.isWrapperComplete() && gradle == null) {
            missing.add(minimumGradle == null ? "Compatible Gradle runtime required" : "Gradle " + minimumGradle + "+ required");
        }

        File sdk = RuntimeLayout.findAndroidSdk(appFilesDir);
        File aapt2 = RuntimeLayout.findAapt2(appFilesDir, sdk);
        if (requirements.isAndroidProject()) {
            int compileSdk = requirements.getCompileSdk();
            if (compileSdk > 0) {
                File androidJar = new File(sdk, "platforms/android-" + compileSdk + "/android.jar");
                if (!androidJar.isFile()) missing.add("Android SDK platform " + compileSdk + " missing");
            } else if (!sdk.isDirectory()) {
                missing.add("Android SDK required");
            }
            if (aapt2 == null || !aapt2.isFile()) missing.add("Android build-tools/aapt2 missing");
        }

        String message = join(missing);
        return new RuntimeCapabilities(javaHome, gradle, sdk, aapt2, missing.isEmpty(), message);
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append("; ");
            out.append(values.get(i));
        }
        return out.toString();
    }

    public File getJavaHome() { return javaHome; }
    public File getGradleExecutable() { return gradleExecutable; }
    public File getAndroidSdk() { return androidSdk; }
    public File getAapt2() { return aapt2; }
    public boolean isReady() { return ready; }
    public String getMissingRequirement() { return missingRequirement; }
}
