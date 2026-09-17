package com.jepongdevxyz.idebuild.core;

/**
 * Tracks the best likely Gradle project root while import entries stream past.
 * Only the best settings/build candidates are retained, so memory usage does
 * not grow with the number of project files.
 */
public final class ProjectRootTracker {
    private String settingsRoot;
    private String buildRoot;

    public void onEntry(String relativeName) {
        String name = normalize(relativeName);
        if (name == null || name.length() == 0 || name.endsWith("/")) return;

        int slash = name.lastIndexOf('/');
        String fileName = slash >= 0 ? name.substring(slash + 1) : name;
        String parent = slash >= 0 ? name.substring(0, slash) : "";

        if (fileName.equals("settings.gradle") || fileName.equals("settings.gradle.kts")) {
            settingsRoot = chooseBetter(settingsRoot, parent);
        } else if (fileName.equals("build.gradle") || fileName.equals("build.gradle.kts")) {
            buildRoot = chooseBetter(buildRoot, parent);
        }
    }

    public String getBestRootRelativePath() {
        return settingsRoot != null ? settingsRoot : buildRoot;
    }

    private static String normalize(String raw) {
        if (raw == null) return null;
        String value = raw.replace('\\', '/');
        while (value.startsWith("./")) value = value.substring(2);
        while (value.contains("//")) value = value.replace("//", "/");
        if (value.equals(".")) return "";
        return value;
    }

    private static String chooseBetter(String current, String candidate) {
        if (current == null) return candidate;
        int currentDepth = depth(current);
        int candidateDepth = depth(candidate);
        if (candidateDepth < currentDepth) return candidate;
        if (candidateDepth > currentDepth) return current;
        return candidate.compareToIgnoreCase(current) < 0 ? candidate : current;
    }

    private static int depth(String path) {
        if (path == null || path.length() == 0) return 0;
        int depth = 1;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') depth++;
        }
        return depth;
    }
}
