package com.jepongdevxyz.idebuild.core;

import java.io.File;
import java.io.IOException;

/**
 * Resolves project-relative paths without allowing callers to escape the
 * selected project root, including through filesystem symlinks.
 */
public final class WorkspacePathResolver {
    private final File canonicalRoot;
    private final String canonicalRootPath;
    private final String rootPrefix;
    private final String backendId;

    public WorkspacePathResolver(File projectRoot, String backendId) throws IOException {
        if (projectRoot == null) throw new IllegalArgumentException("projectRoot must not be null");
        if (backendId == null || backendId.trim().length() == 0) {
            throw new IllegalArgumentException("backendId must not be blank");
        }

        canonicalRoot = projectRoot.getCanonicalFile();
        if (!canonicalRoot.isDirectory()) throw new IOException("Project root is not a directory");
        canonicalRootPath = canonicalRoot.getPath();
        rootPrefix = canonicalRootPath.endsWith(File.separator)
                ? canonicalRootPath
                : canonicalRootPath + File.separator;
        this.backendId = backendId.trim();
    }

    public File resolve(ProjectPath path) throws IOException {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        if (!backendId.equals(path.getBackendId())) {
            throw new IOException("Project path belongs to another workspace backend");
        }

        File candidate = path.getRelativePath().length() == 0
                ? canonicalRoot
                : new File(canonicalRoot, path.getRelativePath()).getCanonicalFile();
        String candidatePath = candidate.getPath();
        if (!candidatePath.equals(canonicalRootPath) && !candidatePath.startsWith(rootPrefix)) {
            throw new IOException("Resolved file is outside project root");
        }
        return candidate;
    }
}
