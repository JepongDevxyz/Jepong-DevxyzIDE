package com.jepongdevxyz.idebuild.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Imports projects through a disposable staging directory and only exposes the
 * final project after extraction/copy and project-root validation succeed.
 */
public final class ProjectImportService {
    public interface CancellationSignal {
        boolean isCancelled();
    }

    public interface ProgressListener {
        void onProgress(int entries, long expandedBytes);
    }

    /**
     * Optional storage check used by Android callers while a project streams to
     * staging. Throw IOException to stop safely (for example when storage is
     * exhausted). The source is never modified.
     */
    public interface StorageProbe {
        void check(File destinationRoot, long expandedBytes) throws IOException;
    }

    /**
     * Writes one supported project source into the service-owned staging
     * directory. The return value is the project-root path relative to staging,
     * or an empty string when staging itself is the project root.
     */
    public interface StagingWriter {
        String write(File stagingDirectory, CancellationSignal cancellation) throws IOException;
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

    public static final StorageProbe NO_STORAGE_PROBE = new StorageProbe() {
        @Override public void check(File destinationRoot, long expandedBytes) { }
    };

    private ProjectImportService() { }

    /**
     * Legacy bounded overload retained for callers that deliberately request an
     * extraction policy limit. Project import UI should use the streaming
     * overload without maxEntries/maxExpandedBytes below.
     */
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

            ImportResult result = finalizeImport(projectsDirectory, baseName, staging, stagedProjectRoot, signal);
            finalized = true;
            return result;
        } finally {
            if (!finalized && staging.exists()) deleteTreeIterative(staging);
        }
    }

    /**
     * Streaming ZIP import with no application-defined project/archive byte or
     * entry ceiling. Actual limits are available storage, filesystem/provider
     * behavior and runtime resources.
     */
    public static ImportResult importProject(InputStream source,
                                             File projectsDirectory,
                                             String suggestedName,
                                             final CancellationSignal cancellation,
                                             final ProgressListener progress,
                                             final StorageProbe storageProbe) throws IOException {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (projectsDirectory == null || !projectsDirectory.isDirectory()) {
            throw new IllegalArgumentException("projectsDirectory must be an existing directory");
        }

        final CancellationSignal signal = cancellation == null ? NEVER_CANCELLED : cancellation;
        final ProgressListener listener = progress == null ? NO_PROGRESS : progress;
        final StorageProbe probe = storageProbe == null ? NO_STORAGE_PROBE : storageProbe;
        final ProjectRootTracker rootTracker = new ProjectRootTracker();

        String baseName = sanitizeProjectName(suggestedName);
        final File staging = createStagingDirectory(projectsDirectory, baseName);
        boolean finalized = false;
        try {
            checkCancelled(signal);
            probe.check(staging, 0L);
            try {
                SafeZip.extractProject(source, staging,
                        new SafeZip.CancellationSignal() {
                            @Override public boolean isCancelled() { return signal.isCancelled(); }
                        },
                        new SafeZip.ProjectProgressListener() {
                            @Override public void onProgress(String entryName, long entries, long expandedBytes) throws IOException {
                                rootTracker.onEntry(entryName);
                                probe.check(staging, expandedBytes);
                                listener.onProgress(saturatingEntryCount(entries), expandedBytes);
                            }
                        });
            } catch (SafeZip.ExtractionCanceledException canceled) {
                throw new ImportCanceledException();
            }
            checkCancelled(signal);

            String rootRelative = rootTracker.getBestRootRelativePath();
            File stagedProjectRoot = resolveStagedProjectRoot(staging, rootRelative);
            ImportResult result = finalizeImport(projectsDirectory, baseName, staging, stagedProjectRoot, signal);
            finalized = true;
            return result;
        } finally {
            if (!finalized && staging.exists()) deleteTreeIterative(staging);
        }
    }

    /**
     * Atomic import wrapper for non-ZIP sources such as an Android SAF folder.
     * The writer must stream source content into the supplied staging directory
     * and return only the best relative project-root candidate.
     */
    public static ImportResult importPreparedProject(File projectsDirectory,
                                                      String suggestedName,
                                                      CancellationSignal cancellation,
                                                      StagingWriter writer) throws IOException {
        if (projectsDirectory == null || !projectsDirectory.isDirectory()) {
            throw new IllegalArgumentException("projectsDirectory must be an existing directory");
        }
        if (writer == null) throw new IllegalArgumentException("writer must not be null");

        final CancellationSignal signal = cancellation == null ? NEVER_CANCELLED : cancellation;
        String baseName = sanitizeProjectName(suggestedName);
        File staging = createStagingDirectory(projectsDirectory, baseName);
        boolean finalized = false;
        try {
            checkCancelled(signal);
            String rootRelative = writer.write(staging, signal);
            checkCancelled(signal);
            File stagedProjectRoot = resolveStagedProjectRoot(staging, rootRelative);
            ImportResult result = finalizeImport(projectsDirectory, baseName, staging, stagedProjectRoot, signal);
            finalized = true;
            return result;
        } finally {
            if (!finalized && staging.exists()) deleteTreeIterative(staging);
        }
    }

    private static File resolveStagedProjectRoot(File staging, String rootRelative) throws IOException {
        File stagedProjectRoot = rootRelative == null || rootRelative.length() == 0
                ? staging
                : new File(staging, rootRelative.replace('/', File.separatorChar));
        stagedProjectRoot = stagedProjectRoot.getCanonicalFile();
        requireContained(staging.getCanonicalFile(), stagedProjectRoot);
        if (!stagedProjectRoot.isDirectory()) throw new IOException("No importable project root was found");
        return stagedProjectRoot;
    }

    private static ImportResult finalizeImport(File projectsDirectory,
                                               String baseName,
                                               File staging,
                                               File stagedProjectRoot,
                                               CancellationSignal signal) throws IOException {
        String stagedRelative = relativeInside(staging, stagedProjectRoot);
        checkCancelled(signal);
        File finalDirectory = nextAvailableDirectory(projectsDirectory, baseName);
        if (!staging.renameTo(finalDirectory)) {
            throw new IOException("Could not finalize imported project");
        }

        File finalProjectRoot = stagedRelative.length() == 0
                ? finalDirectory
                : new File(finalDirectory, stagedRelative.replace('/', File.separatorChar));
        finalProjectRoot = finalProjectRoot.getCanonicalFile();
        requireContained(finalDirectory.getCanonicalFile(), finalProjectRoot);
        if (!finalProjectRoot.isDirectory()) throw new IOException("Imported project root disappeared during finalization");
        return new ImportResult(finalDirectory.getCanonicalFile(), finalProjectRoot);
    }

    private static int saturatingEntryCount(long entries) {
        if (entries <= 0L) return 0;
        return entries >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) entries;
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

    private static void deleteTreeIterative(File root) {
        if (root == null || !root.exists()) return;
        Deque<File> pending = new ArrayDeque<File>();
        Deque<File> directories = new ArrayDeque<File>();
        pending.push(root);
        while (!pending.isEmpty()) {
            File current = pending.pop();
            if (current.isDirectory()) {
                directories.push(current);
                File[] children = current.listFiles();
                if (children != null) {
                    for (File child : children) pending.push(child);
                }
            } else {
                current.delete();
            }
        }
        while (!directories.isEmpty()) directories.pop().delete();
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
