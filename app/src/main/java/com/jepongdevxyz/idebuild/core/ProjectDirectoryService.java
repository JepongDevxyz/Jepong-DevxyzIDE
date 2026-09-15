package com.jepongdevxyz.idebuild.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Lists one project directory at a time. It never recursively scans the tree,
 * and every returned child is revalidated through the workspace resolver.
 */
public final class ProjectDirectoryService {
    private final WorkspacePathResolver resolver;

    public ProjectDirectoryService(WorkspacePathResolver resolver) {
        if (resolver == null) throw new IllegalArgumentException("resolver must not be null");
        this.resolver = resolver;
    }

    public ProjectDirectoryListing listChildren(ProjectPath directory, int maxChildren) throws IOException {
        if (directory == null) throw new IllegalArgumentException("directory must not be null");
        if (maxChildren < 1) throw new IllegalArgumentException("maxChildren must be at least 1");

        File resolvedDirectory = resolver.resolve(directory);
        if (!resolvedDirectory.isDirectory()) throw new IOException("Project path is not a directory");

        File[] children = resolvedDirectory.listFiles();
        if (children == null) throw new IOException("Cannot list project directory");

        List<ProjectEntry> visible = new ArrayList<ProjectEntry>();
        for (File child : children) {
            if (child == null || isIgnoredName(child.getName())) continue;

            ProjectPath childPath;
            File safeChild;
            try {
                childPath = directory.child(child.getName());
                safeChild = resolver.resolve(childPath);
            } catch (IllegalArgumentException invalidPath) {
                continue;
            } catch (IOException escapedOrUnreadable) {
                continue;
            }

            visible.add(new ProjectEntry(
                    child.getName(),
                    childPath,
                    safeChild.isDirectory(),
                    safeChild.isFile() ? safeChild.length() : 0L));
        }

        Collections.sort(visible, new Comparator<ProjectEntry>() {
            @Override public int compare(ProjectEntry left, ProjectEntry right) {
                if (left.isDirectory() != right.isDirectory()) return left.isDirectory() ? -1 : 1;
                int insensitive = left.getName().compareToIgnoreCase(right.getName());
                return insensitive != 0 ? insensitive : left.getName().compareTo(right.getName());
            }
        });

        boolean truncated = visible.size() > maxChildren;
        if (truncated) {
            visible = new ArrayList<ProjectEntry>(visible.subList(0, maxChildren));
        }
        return new ProjectDirectoryListing(visible, truncated);
    }

    private static boolean isIgnoredName(String name) {
        return "build".equals(name)
                || ".gradle".equals(name)
                || ".git".equals(name)
                || ".idea".equals(name);
    }
}
