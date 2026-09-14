package com.jepongdevxyz.idebuild.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;

/** Finds the nearest likely Gradle project root after ZIP extraction. */
public final class ProjectRootDetector {
    private ProjectRootDetector() {}

    public static File findBestGradleRoot(File extractedRoot, int maxDepth, int maxDirectories) throws IOException {
        if (extractedRoot == null || !extractedRoot.isDirectory()) throw new IOException("Imported project root is not a directory");
        if (maxDepth < 0 || maxDirectories <= 0) throw new IllegalArgumentException("Invalid scan limits");
        File canonical = extractedRoot.getCanonicalFile();
        Deque<Node> queue = new ArrayDeque<Node>();
        queue.addLast(new Node(canonical, 0));
        int visited = 0;
        File buildOnlyCandidate = null;
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            if (++visited > maxDirectories) throw new IOException("Imported archive contains too many directories to detect a project root");
            File dir = node.file;
            if (hasSettings(dir)) return dir;
            if (buildOnlyCandidate == null && hasBuildFile(dir)) buildOnlyCandidate = dir;
            if (node.depth >= maxDepth) continue;
            File[] children = dir.listFiles();
            if (children == null) continue;
            Arrays.sort(children, new Comparator<File>() {
                @Override public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
            });
            for (File child : children) {
                if (!child.isDirectory() || shouldSkip(child.getName())) continue;
                queue.addLast(new Node(child.getCanonicalFile(), node.depth + 1));
            }
        }
        return buildOnlyCandidate != null ? buildOnlyCandidate : canonical;
    }

    private static boolean hasSettings(File dir) {
        return new File(dir, "settings.gradle").isFile() || new File(dir, "settings.gradle.kts").isFile();
    }

    private static boolean hasBuildFile(File dir) {
        return new File(dir, "build.gradle").isFile() || new File(dir, "build.gradle.kts").isFile();
    }

    private static boolean shouldSkip(String name) {
        return name.equals(".git") || name.equals(".gradle") || name.equals(".idea") || name.equals("build") || name.equals("node_modules");
    }

    private static final class Node {
        final File file;
        final int depth;
        Node(File file, int depth) { this.file = file; this.depth = depth; }
    }
}
