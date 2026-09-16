package com.jepongdevxyz.idebuild.core.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Finds real Gradle output artifacts without reporting intermediate/cache files. */
public final class BuildOutputScanner {
    private BuildOutputScanner() {}

    public static List<BuildArtifact> scan(File projectRoot, int maxFiles) throws IOException {
        if (projectRoot == null || !projectRoot.isDirectory()) throw new IllegalArgumentException("projectRoot must be a directory");
        File canonicalRoot = projectRoot.getCanonicalFile();
        ArrayList<BuildArtifact> artifacts = new ArrayList<BuildArtifact>();
        int limit = maxFiles <= 0 ? 1 : maxFiles;
        scanDirectory(canonicalRoot, canonicalRoot, limit, new int[]{0}, artifacts);
        Collections.sort(artifacts, new Comparator<BuildArtifact>() {
            @Override public int compare(BuildArtifact left, BuildArtifact right) {
                int type = rank(left.getType()) - rank(right.getType());
                if (type != 0) return type;
                int variant = left.getVariant().compareTo(right.getVariant());
                if (variant != 0) return variant;
                return left.getRelativePath().compareTo(right.getRelativePath());
            }
        });
        return artifacts;
    }

    private static void scanDirectory(File root,
                                      File directory,
                                      int maxFiles,
                                      int[] visited,
                                      List<BuildArtifact> artifacts) throws IOException {
        if (visited[0] >= maxFiles) return;
        File canonical = directory.getCanonicalFile();
        if (!isContained(root, canonical)) return;
        File[] children = canonical.listFiles();
        if (children == null) return;
        for (int i = 0; i < children.length && visited[0] < maxFiles; i++) {
            File child = children[i];
            File childCanonical = child.getCanonicalFile();
            if (!isContained(root, childCanonical)) continue;
            visited[0]++;
            if (childCanonical.isDirectory()) {
                if (shouldSkipDirectory(root, childCanonical)) continue;
                scanDirectory(root, childCanonical, maxFiles, visited, artifacts);
            } else if (childCanonical.isFile()) {
                BuildArtifact artifact = artifactFor(root, childCanonical);
                if (artifact != null) artifacts.add(artifact);
            }
        }
    }

    private static BuildArtifact artifactFor(File root, File file) throws IOException {
        String relative = relativePath(root, file);
        String normalized = "/" + relative.replace(File.separatorChar, '/') + "/";
        if (normalized.indexOf("/build/outputs/") < 0) return null;
        String lower = relative.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".apk")) {
            return new BuildArtifact("APK", file, relative, variantFor(relative, "APK"), file.length(), file.lastModified());
        }
        if (lower.endsWith(".aab")) {
            return new BuildArtifact("AAB", file, relative, variantFor(relative, "AAB"), file.length(), file.lastModified());
        }
        return null;
    }

    private static String variantFor(String relative, String type) {
        String path = "/" + relative.replace(File.separatorChar, '/');
        String marker = "APK".equals(type) ? "/build/outputs/apk/" : "/build/outputs/bundle/";
        int markerIndex = path.indexOf(marker);
        if (markerIndex < 0) return "unknown";
        int start = markerIndex + marker.length();
        int end = path.indexOf('/', start);
        if (end < 0) end = path.length();
        if (end <= start) return "unknown";
        return path.substring(start, end);
    }

    private static boolean shouldSkipDirectory(File root, File directory) throws IOException {
        String relative = relativePath(root, directory).replace(File.separatorChar, '/');
        return ".gradle".equals(relative) || relative.startsWith(".gradle/")
                || "build/intermediates".equals(relative) || relative.indexOf("/build/intermediates") >= 0;
    }

    private static String relativePath(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        if (filePath.equals(rootPath)) return "";
        int start = rootPath.endsWith(File.separator) ? rootPath.length() : rootPath.length() + 1;
        return filePath.substring(start).replace(File.separatorChar, '/');
    }

    private static boolean isContained(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String path = file.getCanonicalPath();
        return path.equals(rootPath) || path.startsWith(rootPath + File.separator);
    }

    private static int rank(String type) {
        return "APK".equals(type) ? 0 : "AAB".equals(type) ? 1 : 2;
    }
}
