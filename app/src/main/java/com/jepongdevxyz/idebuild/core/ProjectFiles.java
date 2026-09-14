package com.jepongdevxyz.idebuild.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProjectFiles {
    private ProjectFiles() {}

    public static List<String> listRelativeFiles(File root, int maxFiles) throws IOException {
        if (root == null || !root.isDirectory()) throw new IOException("Project root is not a directory");
        if (maxFiles <= 0) throw new IllegalArgumentException("maxFiles must be positive");
        ArrayList<String> out = new ArrayList<>();
        String canonicalRoot = root.getCanonicalPath();
        walk(canonicalRoot, root, out, maxFiles);
        Collections.sort(out);
        return out;
    }

    public static String relativePath(File root, File child) throws IOException {
        String rootPath = root.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
        if (!childPath.startsWith(prefix)) throw new IOException("File is outside project root");
        return childPath.substring(prefix.length()).replace(File.separatorChar, '/');
    }

    private static void walk(String canonicalRoot, File current, List<String> out, int maxFiles) throws IOException {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                String name = child.getName();
                if (name.equals("build") || name.equals(".gradle") || name.equals(".git") || name.equals(".idea")) continue;
                walk(canonicalRoot, child, out, maxFiles);
            } else {
                String childPath = child.getCanonicalPath();
                String prefix = canonicalRoot.endsWith(File.separator) ? canonicalRoot : canonicalRoot + File.separator;
                if (!childPath.startsWith(prefix)) continue;
                out.add(childPath.substring(prefix.length()).replace(File.separatorChar, '/'));
                if (out.size() > maxFiles) throw new IOException("Project has too many files");
            }
        }
    }
}
