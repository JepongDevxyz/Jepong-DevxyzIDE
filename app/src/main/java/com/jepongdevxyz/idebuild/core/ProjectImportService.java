package com.jepongdevxyz.idebuild.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Imports a project ZIP through a disposable staging directory and only exposes
 * the final project after extraction and project-root detection succeed.
 */
public final class ProjectImportService {
    public interface CancellationSignal {
        boolean isCancelled();
    }

    public interface ProgressListener {
        void onProgress(int entries, long expandedBytes);
    }

    public static final class ImportCanceledException extends IOException {
        public ImportCanceledException() { super("Project import canceled"); }
    }

    public static final CancellationSignal NEVER_CANCELLED = new CancellationSignal() {
        @Override public boolean isCancelled() { return false; }
    };

    public static final ProgressListener NO_PROGRESS = new ProgressListener() {
        @Override public void onProgress(int entries, long expandedBytes) { }
    };

    private ProjectImportService() { }

    public static ImportResult importProject(InputStream source,
                                             File projectsDirectory,
                                             String suggestedName,
                                             int maxEntries,
                                             long maxExpandedBytes,
                                             final CancellationSignal cancellation,
                                             final ProgressListener progress) throws IOException {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (projectsDirectory == null || !projectsDirectory.isDirectory()) {
            throw new IllegalArgumentException("projectsDirectory must be an existing directory");
        }
        if (maxEntries <= 0 || maxExpandedBytes <= 0) {
            throw new IllegalArgumentException("import limits must be positive");
        }

        final CancellationSignal signal = cancellation == null ? NEVER_CANCELLED : cancellation;
        final ProgressListener listener = progress == null ? NO_PROGRESS : progress;
        String baseName = sanitizeProjectName(suggestedName);
        File staging = createStagingDirectory(projectsDirectory, baseName);
        boolean finalized = false;
        try {
            checkCancelled(signal);
            try {
                SafeZip.extractProject(source, staging, maxEntries, maxExpandedBytes,
                        new SafeZip.CancellationSignal() {
                            @Override public boolean isCancelled() { return signal.isCancelled(); }
                        },
                        new SafeZip.ProgressListener() {
                            @Override public void onProgress(int entries, long expandedBytes) {
                                listener.onProgress(entries, expandedBytes);
                            }
                        });
            } catch (SafeZip.ExtractionCanceledException canceled) {
                throw new ImportCanceledException();
            }
            checkCancelled(signal);

            File stagedCandidate = collapseSingleRootFolder(staging);
            File stagedProjectRoot = ProjectRootDetector.findBestGradleRoot(stagedCandidate, 6, 20000);
            if (stagedProjectRoot == null || !stagedProjectRoot.isDirectory()) {
                throw new IOException("No importable project root was found");
            }

            String stagedRelative = relativeInside(staging, stagedProjectRoot);
            checkCancelled(signal);
            File finalDirectory = nextAvailableDirectory(projectsDirectory, baseName);
            if (!staging.renameTo(finalDirectory)) {
                throw new IOException("Could not finalize imported project");
            }
            finalized = true;

            File finalProjectRoot = stagedRelative.length() == 0
                    ? finalDirectory
                    : new File(finalDirectory, stagedRelative.replace('/', File.separatorChar));
            finalProjectRoot = finalProjectRoot.getCanonicalFile();
            requireContained(finalDirectory.getCanonicalFile(), finalProjectRoot);
            if (!finalProjectRoot.isDirectory()) throw new IOException("Imported project root disappeared during finalization");
            return new ImportResult(finalDirectory.getCanonicalFile(), finalProjectRoot);
        } finally {
            if (!finalized && staging.exists()) deleteTree(staging);
        }
    }

    private static void checkCancelled(CancellationSignal cancellation) throws ImportCanceledException {
        if (cancellation != null && cancellation.isCancelled()) throw new ImportCanceledException();
    }

    private static File createStagingDirectory(File parent, String baseName) throws IOException {
        for (int i = 0; i < 1000; i++) {
            File candidate = new File(parent, ".import-" + baseName + "-" + System.nanoTime() + (i == 0 ? "" : "-" + i));
            if (candidate.mkdir()) return candidate.getCanonicalFile();
        }
        throw new IOException("Could not create project import staging directory");
    }

    private static File nextAvailableDirectory(File parent, String baseName) throws IOException {
        File candidate = new File(parent, baseName);
        int suffix = 2;
        while (candidate.exists()) candidate = new File(parent, baseName + "-" + suffix++);
        requireContained(parent.getCanonicalFile(), candidate.getCanonicalFile());
        return candidate;
    }

    private static String sanitizeProjectName(String suggestedName) {
        String value = suggestedName == null ? "project" : suggestedName.trim();
        value = value.replaceFirst("(?i)\\.zip$", "");
        value = value.replaceAll("[^A-Za-z0-9._-]+", "-");
        value = value.replaceAll("^-+|-+$", "");
        while (value.contains("..")) value = value.replace("..", ".");
        if (value.length() == 0 || value.equals(".") || value.equals("..")) value = "project";
        if (value.length() > 80) value = value.substring(0, 80);
        return value;
    }

    private static File collapseSingleRootFolder(File target) {
        File[] children = target.listFiles();
        if (children != null && children.length == 1 && children[0].isDirectory()) return children[0];
        return target;
    }

    private static String relativeInside(File root, File child) throws IOException {
        File canonicalRoot = root.getCanonicalFile();
        File canonicalChild = child.getCanonicalFile();
        requireContained(canonicalRoot, canonicalChild);
        if (canonicalRoot.equals(canonicalChild)) return "";
        String prefix = canonicalRoot.getPath() + File.separator;
        return canonicalChild.getPath().substring(prefix.length()).replace(File.separatorChar, '/');
    }

    private static void requireContained(File root, File child) throws IOException {
        String rootPath = root.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        if (!childPath.equals(rootPath) && !childPath.startsWith(rootPath + File.separator)) {
            throw new IOException("Imported project path escaped the staging root");
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    public static final class ImportResult {
        private final File importedDirectory;
        private final File projectRoot;

        private ImportResult(File importedDirectory, File projectRoot) {
            this.importedDirectory = importedDirectory;
            this.projectRoot = projectRoot;
        }

        public File getImportedDirectory() { return importedDirectory; }
        public File getProjectRoot() { return projectRoot; }
    }
}
