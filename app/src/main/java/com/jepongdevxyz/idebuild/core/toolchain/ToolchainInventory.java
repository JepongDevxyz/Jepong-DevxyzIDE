package com.jepongdevxyz.idebuild.core.toolchain;

import java.io.File;
import java.util.*;

public final class ToolchainInventory {
    private final List<Integer> installedJdks;
    private final List<Integer> androidPlatforms;
    private final List<String> buildTools;
    private final boolean aapt2;
    private final boolean ndk;
    private final boolean cmake;

    private ToolchainInventory(List<Integer> installedJdks,
                               List<Integer> androidPlatforms,
                               List<String> buildTools,
                               boolean aapt2,
                               boolean ndk,
                               boolean cmake) {
        this.installedJdks = Collections.unmodifiableList(installedJdks);
        this.androidPlatforms = Collections.unmodifiableList(androidPlatforms);
        this.buildTools = Collections.unmodifiableList(buildTools);
        this.aapt2 = aapt2;
        this.ndk = ndk;
        this.cmake = cmake;
    }

    public static ToolchainInventory scan(File appFilesDir) {
        if (appFilesDir == null) throw new IllegalArgumentException("appFilesDir == null");
        List<Integer> jdks = new ArrayList<>();
        for (int major : new int[]{11, 17, 21, 25, 26}) {
            if (RuntimeLayout.findJavaHome(appFilesDir, major) != null) jdks.add(major);
        }
        Collections.sort(jdks);

        File sdk = RuntimeLayout.findAndroidSdk(appFilesDir);
        List<Integer> platforms = new ArrayList<>();
        File platformsDir = new File(sdk, "platforms");
        File[] platformDirs = platformsDir.listFiles();
        if (platformDirs != null) {
            for (File platform : platformDirs) {
                if (!platform.isDirectory() || !new File(platform, "android.jar").isFile()) continue;
                String n = platform.getName();
                if (!n.startsWith("android-")) continue;
                try { platforms.add(Integer.parseInt(n.substring("android-".length()))); }
                catch (NumberFormatException ignored) {}
            }
        }
        Collections.sort(platforms);

        List<String> buildTools = new ArrayList<>();
        File[] buildToolDirs = new File(sdk, "build-tools").listFiles();
        if (buildToolDirs != null) {
            for (File dir : buildToolDirs) if (dir.isDirectory()) buildTools.add(dir.getName());
        }
        Collections.sort(buildTools, new Comparator<String>() {
            @Override public int compare(String a, String b) { return compareVersionText(a, b); }
        });

        boolean hasAapt2 = RuntimeLayout.findAapt2(appFilesDir, sdk).isFile();
        boolean hasNdk = hasNonEmptyDirectory(new File(sdk, "ndk")) || new File(sdk, "ndk-bundle").isDirectory();
        boolean hasCmake = hasNonEmptyDirectory(new File(sdk, "cmake")) || new File(appFilesDir, "usr/bin/cmake").isFile();
        return new ToolchainInventory(jdks, platforms, buildTools, hasAapt2, hasNdk, hasCmake);
    }

    private static boolean hasNonEmptyDirectory(File dir) {
        File[] children = dir.listFiles(new java.io.FileFilter() {
            @Override public boolean accept(File file) { return file.isDirectory(); }
        });
        return children != null && children.length > 0;
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

    public List<Integer> getInstalledJdks() { return installedJdks; }
    public List<Integer> getAndroidPlatforms() { return androidPlatforms; }
    public List<String> getBuildTools() { return buildTools; }
    public boolean hasAndroidPlatform(int api) { return androidPlatforms.contains(api); }
    public boolean hasJdk(int major) { return installedJdks.contains(major); }
    public boolean hasBuildTools(String version) { return version != null && buildTools.contains(version); }
    public boolean hasAapt2() { return aapt2; }
    public boolean hasNdk() { return ndk; }
    public boolean hasCmake() { return cmake; }

    public String summary() {
        return "JDKs=" + installedJdks +
                ", AndroidPlatforms=" + androidPlatforms +
                ", BuildTools=" + buildTools +
                ", aapt2=" + aapt2 +
                ", NDK=" + ndk +
                ", CMake=" + cmake;
    }
}
