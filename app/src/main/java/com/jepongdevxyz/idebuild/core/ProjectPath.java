package com.jepongdevxyz.idebuild.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable project-relative path paired with the workspace backend that owns it.
 *
 * <p>This type deliberately rejects parent traversal and absolute paths so callers
 * cannot accidentally escape the selected project root.</p>
 */
public final class ProjectPath {
    private final String backendId;
    private final String relativePath;

    private ProjectPath(String backendId, String relativePath) {
        this.backendId = backendId;
        this.relativePath = relativePath;
    }

    public static ProjectPath of(String backendId, String rawPath) {
        if (backendId == null || backendId.trim().length() == 0) {
            throw new IllegalArgumentException("backendId must not be blank");
        }
        if (rawPath == null) {
            throw new IllegalArgumentException("path must not be null");
        }

        String normalizedInput = rawPath.replace('\\', '/');
        if (normalizedInput.startsWith("/") || isWindowsAbsolute(normalizedInput)) {
            throw new IllegalArgumentException("absolute paths are not allowed");
        }

        String[] parts = normalizedInput.split("/");
        List<String> normalizedParts = new ArrayList<String>();
        for (String part : parts) {
            if (part.length() == 0 || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new IllegalArgumentException("parent traversal is not allowed");
            }
            normalizedParts.add(part);
        }

        StringBuilder path = new StringBuilder();
        for (int i = 0; i < normalizedParts.size(); i++) {
            if (i > 0) {
                path.append('/');
            }
            path.append(normalizedParts.get(i));
        }

        return new ProjectPath(backendId.trim(), path.toString());
    }

    public String getBackendId() {
        return backendId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public ProjectPath child(String name) {
        if (name == null || name.trim().length() == 0) {
            throw new IllegalArgumentException("child name must not be blank");
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException("child name must be a single path segment");
        }

        String childPath = relativePath.length() == 0 ? name : relativePath + "/" + name;
        return of(backendId, childPath);
    }

    private static boolean isWindowsAbsolute(String path) {
        return path.length() >= 3
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':'
                && path.charAt(2) == '/';
    }
}
