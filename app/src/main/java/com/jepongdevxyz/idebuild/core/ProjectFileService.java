package com.jepongdevxyz.idebuild.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Performs project file mutations through ProjectPath + WorkspacePathResolver.
 * All paths are project-relative and every operation is re-resolved against the
 * trusted workspace root before touching disk.
 */
public final class ProjectFileService {
    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    private final WorkspacePathResolver resolver;
    private final String backendId;

    public ProjectFileService(WorkspacePathResolver resolver, String backendId) {
        if (resolver == null) throw new IllegalArgumentException("resolver must not be null");
        if (backendId == null || backendId.trim().length() == 0) {
            throw new IllegalArgumentException("backendId must not be blank");
        }
        this.resolver = resolver;
        this.backendId = backendId.trim();
    }

    public ProjectPath createFile(ProjectPath parent, String name) throws IOException {
        File parentFile = requireDirectory(parent);
        ProjectPath target = child(parent, name);
        File targetFile = resolver.resolve(target);
        if (targetFile.exists()) throw new IOException("A file or directory with that name already exists");
        if (!parentFile.isDirectory()) throw new IOException("Parent directory no longer exists");
        if (!targetFile.createNewFile()) throw new IOException("Could not create file: " + target.getRelativePath());
        return target;
    }

    public ProjectPath createDirectory(ProjectPath parent, String name) throws IOException {
        requireDirectory(parent);
        ProjectPath target = child(parent, name);
        File targetFile = resolver.resolve(target);
        if (targetFile.exists()) throw new IOException("A file or directory with that name already exists");
        if (!targetFile.mkdir()) throw new IOException("Could not create directory: " + target.getRelativePath());
        return target;
    }

    public ProjectPath rename(ProjectPath source, String newName) throws IOException {
        requireMutablePath(source);
        ProjectPath parent = source.parent();
        ProjectPath target = child(parent, newName);
        File sourceFile = resolver.resolve(source);
        File targetFile = resolver.resolve(target);
        if (!sourceFile.exists()) throw new IOException("Source does not exist: " + source.getRelativePath());
        if (targetFile.exists()) throw new IOException("A file or directory with that name already exists");
        if (!sourceFile.renameTo(targetFile)) {
            throw new IOException("Could not rename: " + source.getRelativePath());
        }
        return target;
    }

    public ProjectPath duplicate(ProjectPath source) throws IOException {
        requireMutablePath(source);
        File sourceFile = resolver.resolve(source);
        if (!sourceFile.exists()) throw new IOException("Source does not exist: " + source.getRelativePath());

        ProjectPath parent = source.parent();
        String originalName = lastName(source);
        String baseName = originalName;
        String extension = "";
        if (sourceFile.isFile()) {
            int dot = originalName.lastIndexOf('.');
            if (dot > 0) {
                baseName = originalName.substring(0, dot);
                extension = originalName.substring(dot);
            }
        }

        ProjectPath target = null;
        for (int copyNumber = 1; copyNumber < 10000; copyNumber++) {
            String suffix = copyNumber == 1 ? " copy" : " copy " + copyNumber;
            ProjectPath candidate = child(parent, baseName + suffix + extension);
            if (!resolver.resolve(candidate).exists()) {
                target = candidate;
                break;
            }
        }
        if (target == null) throw new IOException("Could not choose an available duplicate name");

        try {
            copyRecursive(source, target);
            return target;
        } catch (IOException failure) {
            try {
                if (resolver.resolve(target).exists()) deleteRecursive(target);
            } catch (IOException ignored) {
                // Preserve the original copy failure; partial cleanup is best effort.
            }
            throw failure;
        }
    }

    public void delete(ProjectPath path) throws IOException {
        requireMutablePath(path);
        File file = resolver.resolve(path);
        if (!file.exists()) throw new IOException("Path does not exist: " + path.getRelativePath());
        deleteRecursive(path);
    }

    private void copyRecursive(ProjectPath source, ProjectPath target) throws IOException {
        File sourceFile = resolver.resolve(source);
        File targetFile = resolver.resolve(target);
        if (targetFile.exists()) throw new IOException("Duplicate target already exists");

        if (sourceFile.isDirectory()) {
            if (!targetFile.mkdir()) throw new IOException("Could not create duplicate directory");
            File[] children = sourceFile.listFiles();
            if (children == null) throw new IOException("Could not list directory: " + source.getRelativePath());
            for (File child : children) {
                copyRecursive(source.child(child.getName()), target.child(child.getName()));
            }
            return;
        }

        if (!sourceFile.isFile()) throw new IOException("Unsupported project entry: " + source.getRelativePath());
        copyFile(sourceFile, targetFile);
    }

    private void copyFile(File source, File target) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new BufferedInputStream(new FileInputStream(source), COPY_BUFFER_BYTES);
            output = new BufferedOutputStream(new FileOutputStream(target), COPY_BUFFER_BYTES);
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
        } finally {
            if (output != null) try { output.close(); } catch (IOException ignored) { }
            if (input != null) try { input.close(); } catch (IOException ignored) { }
        }
    }

    private void deleteRecursive(ProjectPath path) throws IOException {
        File file = resolver.resolve(path);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Could not list directory: " + path.getRelativePath());
            for (File child : children) deleteRecursive(path.child(child.getName()));
        }
        if (!file.delete()) throw new IOException("Could not delete: " + path.getRelativePath());
    }

    private File requireDirectory(ProjectPath path) throws IOException {
        requireBackend(path);
        File directory = resolver.resolve(path);
        if (!directory.isDirectory()) throw new IOException("Parent is not a directory: " + path.getRelativePath());
        return directory;
    }

    private void requireMutablePath(ProjectPath path) throws IOException {
        requireBackend(path);
        if (path.getRelativePath().length() == 0) throw new IOException("Project root cannot be renamed, duplicated, or deleted");
    }

    private void requireBackend(ProjectPath path) throws IOException {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        if (!backendId.equals(path.getBackendId())) throw new IOException("Project path belongs to another workspace backend");
    }

    private ProjectPath child(ProjectPath parent, String name) throws IOException {
        requireBackend(parent);
        return parent.child(name);
    }

    private static String lastName(ProjectPath path) {
        String relative = path.getRelativePath();
        int slash = relative.lastIndexOf('/');
        return slash < 0 ? relative : relative.substring(slash + 1);
    }
}
