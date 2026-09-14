package com.jepongdevxyz.idebuild.core.toolchain;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.io.FileFilter;

/** Resolves both DevxyzIDE pack layout and Termux-style app-private runtime layout. */
public final class RuntimeLayout {
    private final File javaHome;
    private final File androidSdk;
    private final File gradleHome;
    private final File aapt2;

    private RuntimeLayout(File javaHome, File androidSdk, File gradleHome, File aapt2) {
        this.javaHome = javaHome;
        this.androidSdk = androidSdk;
        this.gradleHome = gradleHome;
        this.aapt2 = aapt2;
    }

    public static RuntimeLayout resolve(File appFilesDir, int javaMajor) {
        if (appFilesDir == null) throw new IllegalArgumentException("appFilesDir == null");
        File javaHome = javaMajor > 0 ? findJavaHome(appFilesDir, javaMajor) : null;
        File sdk = findAndroidSdk(appFilesDir);
        File gradle = findGradleHome(appFilesDir);
        File aapt2 = findAapt2(appFilesDir, sdk);
        return new RuntimeLayout(javaHome, sdk, gradle, aapt2);
    }

    public static File findJavaHome(File appFilesDir, int javaMajor) {
        if (appFilesDir == null || javaMajor <= 0) return null;
        File[] candidates = {
                new File(appFilesDir, "toolchains/jdk" + javaMajor),
                new File(appFilesDir, "usr/lib/jvm/java-" + javaMajor + "-openjdk"),
                new File(appFilesDir, "usr/opt/openjdk-" + javaMajor + ".0"),
                new File(appFilesDir, "usr/opt/openjdk-" + javaMajor)
        };
        for (File candidate : candidates) {
            if (new File(candidate, "bin/java").isFile()) return candidate;
        }
        return null;
    }

    public static File findAndroidSdk(File appFilesDir) {
        File homeSdk = new File(appFilesDir, "home/android-sdk");
        if (homeSdk.isDirectory()) return homeSdk;
        return new File(appFilesDir, "toolchains/android-sdk");
    }

    public static File findGradleHome(File appFilesDir) {
        File home = new File(appFilesDir, "home");
        if (home.isDirectory()) return new File(home, ".gradle");
        return new File(appFilesDir, "gradle-home");
    }

    /** Finds an app-private Gradle launcher for projects without a wrapper. */
    public static File findGradleExecutable(File appFilesDir) {
        return findGradleExecutable(appFilesDir, null);
    }

    /** Finds the newest versioned internal Gradle that satisfies the required minimum version. */
    public static File findGradleExecutable(File appFilesDir, String minimumVersion) {
        if (appFilesDir == null) return null;

        File toolchains = new File(appFilesDir, "toolchains");
        File[] children = toolchains.listFiles(new FileFilter() {
            @Override public boolean accept(File file) { return file.isDirectory() && file.getName().startsWith("gradle-"); }
        });
        if (children != null) {
            Arrays.sort(children, new Comparator<File>() {
                @Override public int compare(File a, File b) { return -compareVersionText(a.getName(), b.getName()); }
            });
            for (File child : children) {
                String version = child.getName().substring("gradle-".length());
                if (minimumVersion != null && !com.jepongdevxyz.idebuild.core.build.GradleCompatibility.isAtLeast(version, minimumVersion)) continue;
                File candidate = new File(child, "bin/gradle");
                if (candidate.isFile()) return candidate;
            }
        }

        if (minimumVersion != null) return null;
        File[] direct = {
                new File(appFilesDir, "usr/bin/gradle"),
                new File(appFilesDir, "toolchains/gradle/bin/gradle")
        };
        for (File candidate : direct) if (candidate.isFile()) return candidate;
        return null;
    }

    public static File findAapt2(File appFilesDir, File androidSdk) {
        File explicit = new File(appFilesDir, "toolchains/aapt2/aapt2");
        if (explicit.isFile()) return explicit;
        if (androidSdk == null) return explicit;
        File buildTools = new File(androidSdk, "build-tools");
        File[] versions = buildTools.listFiles(new FileFilter() { @Override public boolean accept(File file) { return file.isDirectory(); } });
        if (versions == null || versions.length == 0) return explicit;
        Arrays.sort(versions, new Comparator<File>() {
            @Override public int compare(File a, File b) { return -compareVersionText(a.getName(), b.getName()); }
        });
        for (File version : versions) {
            File candidate = new File(version, "aapt2");
            if (candidate.isFile()) return candidate;
        }
        return explicit;
    }

    private static int compareVersionText(String a, String b) {
        String[] aa = a.split("[.-]");
        String[] bb = b.split("[.-]");
        int count = Math.max(aa.length, bb.length);
        for (int i = 0; i < count; i++) {
            int av = i < aa.length ? intPrefix(aa[i]) : 0;
            int bv = i < bb.length ? intPrefix(bb[i]) : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        return a.compareTo(b);
    }

    private static int intPrefix(String value) {
        try { return Integer.parseInt(value.replaceFirst("[^0-9].*$", "")); }
        catch (Exception ignored) { return 0; }
    }

    public File getJavaHome() { return javaHome; }
    public File getAndroidSdk() { return androidSdk; }
    public File getGradleHome() { return gradleHome; }
    public File getAapt2() { return aapt2; }
}
