package com.jepongdevxyz.idebuild.core;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SafeZip {
    private static final int BUFFER_SIZE = 32 * 1024;
    private static final Object PROJECT_EXTRACTION_LOCK = new Object();
    private static ProjectExtractionCancellation activeProjectExtraction;
    private static boolean preparedProjectExtraction;
    private static boolean preparedCancellationRequested;

    public interface CancellationSignal {
        boolean isCancelled();
    }

    public interface ProgressListener {
        void onProgress(int entries, long expandedBytes);
    }

    public static final class ExtractionCanceledException extends IOException {
        public ExtractionCanceledException() { super("ZIP extraction canceled"); }
    }

    public static final CancellationSignal NEVER_CANCELLED = new CancellationSignal() {
        @Override public boolean isCancelled() { return false; }
    };

    public static final ProgressListener NO_PROGRESS = new ProgressListener() {
        @Override public void onProgress(int entries, long expandedBytes) { }
    };

    private SafeZip() {}

    public static void prepareProjectExtraction() {
        synchronized (PROJECT_EXTRACTION_LOCK) {
            preparedProjectExtraction = true;
            preparedCancellationRequested = false;
        }
    }

    public static boolean cancelPreparedOrActiveProjectExtraction() {
        synchronized (PROJECT_EXTRACTION_LOCK) {
            if (activeProjectExtraction != null) {
                activeProjectExtraction.cancelled = true;
                return true;
            }
            if (preparedProjectExtraction) {
                preparedCancellationRequested = true;
                return true;
            }
            return false;
        }
    }

    public static void clearProjectExtractionRequest() {
        synchronized (PROJECT_EXTRACTION_LOCK) {
            preparedProjectExtraction = false;
            preparedCancellationRequested = false;
            if (activeProjectExtraction != null) activeProjectExtraction.cancelled = true;
        }
    }

    public static boolean isProjectExtractionActiveOrPrepared() {
        synchronized (PROJECT_EXTRACTION_LOCK) {
            return preparedProjectExtraction || activeProjectExtraction != null;
        }
    }

    public static void extract(InputStream source, File destination, int maxEntries, long maxBytes) throws IOException {
        extractInternal(source, destination, maxEntries, maxBytes, false, NEVER_CANCELLED, NO_PROGRESS);
    }

    public static void extractProject(InputStream source, File destination, int maxEntries, long maxBytes) throws IOException {
        boolean cleanupOnFailure = isEmptyOrMissingDirectory(destination);
        ProjectExtractionCancellation cancellation = beginPreparedProjectExtraction();
        try {
            extractInternal(source, destination, maxEntries, maxBytes, true, cancellation, NO_PROGRESS);
        } catch (IOException failure) {
            if (cleanupOnFailure) deleteTree(destination);
            throw failure;
        } finally {
            endPreparedProjectExtraction(cancellation);
        }
    }

    public static void extractProject(InputStream source,
                                      File destination,
                                      int maxEntries,
                                      long maxBytes,
                                      CancellationSignal cancellation,
                                      ProgressListener progress) throws IOException {
        extractInternal(source, destination, maxEntries, maxBytes, true,
                cancellation == null ? NEVER_CANCELLED : cancellation,
                progress == null ? NO_PROGRESS : progress);
    }

    private static ProjectExtractionCancellation beginPreparedProjectExtraction() {
        synchronized (PROJECT_EXTRACTION_LOCK) {
            ProjectExtractionCancellation token = new ProjectExtractionCancellation();
            token.cancelled = preparedCancellationRequested;
            preparedProjectExtraction = false;
            preparedCancellationRequested = false;
            activeProjectExtraction = token;
            return token;
        }
    }

    private static void endPreparedProjectExtraction(ProjectExtractionCancellation token) {
        synchronized (PROJECT_EXTRACTION_LOCK) {
            if (activeProjectExtraction == token) activeProjectExtraction = null;
            preparedProjectExtraction = false;
            preparedCancellationRequested = false;
        }
    }

    private static void extractInternal(InputStream source,
                                        File destination,
                                        int maxEntries,
                                        long maxBytes,
                                        boolean projectMode,
                                        CancellationSignal cancellation,
                                        ProgressListener progress) throws IOException {
        if (source == null) throw new IllegalArgumentException("source == null");
        if (destination == null) throw new IllegalArgumentException("destination == null");
        if (maxEntries <= 0 || maxBytes <= 0) throw new IllegalArgumentException("limits must be positive");
        if (!destination.exists() && !destination.mkdirs()) throw new IOException("Cannot create destination: " + destination);

        String canonicalDestination = destination.getCanonicalPath();
        String targetRoot = canonicalDestination + File.separator;
        byte[] buffer = new byte[BUFFER_SIZE];
        int entries = 0;
        long totalBytes = 0;

        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                checkCancelled(cancellation);
                entries++;
                if (entries > maxEntries) throw new IOException("Archive has too many entries");

                File out = new File(destination, entry.getName());
                String outPath = out.getCanonicalPath();
                if (!outPath.equals(canonicalDestination) && !outPath.startsWith(targetRoot)) {
                    throw new IOException("Unsafe ZIP path: " + entry.getName());
                }

                if (projectMode && shouldSkipProjectEntry(entry.getName())) {
                    zip.closeEntry();
                    progress.onProgress(entries, totalBytes);
                    checkCancelled(cancellation);
                    continue;
                }

                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) throw new IOException("Cannot create directory: " + out);
                    progress.onProgress(entries, totalBytes);
                    checkCancelled(cancellation);
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create directory: " + parent);
                    try (OutputStream output = new BufferedOutputStream(new FileOutputStream(out))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            checkCancelled(cancellation);
                            if (!isWithinExpandedLimit(totalBytes, read, maxBytes)) throw new IOException("Archive exceeds extraction size limit");
                            totalBytes += read;
                            output.write(buffer, 0, read);
                            progress.onProgress(entries, totalBytes);
                            checkCancelled(cancellation);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
        checkCancelled(cancellation);
    }

    private static void checkCancelled(CancellationSignal cancellation) throws ExtractionCanceledException {
        if (cancellation != null && cancellation.isCancelled()) throw new ExtractionCanceledException();
    }

    private static boolean isEmptyOrMissingDirectory(File destination) {
        if (destination == null || !destination.exists()) return true;
        if (!destination.isDirectory()) return false;
        File[] children = destination.listFiles();
        return children != null && children.length == 0;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    public static boolean isWithinExpandedLimit(long currentBytes, long nextBytes, long maxBytes) {
        if (currentBytes < 0 || nextBytes < 0 || maxBytes < 0) return false;
        return currentBytes <= maxBytes && nextBytes <= maxBytes - currentBytes;
    }

    static boolean shouldSkipProjectEntry(String rawName) {
        if (rawName == null || rawName.isEmpty()) return false;
        String name = rawName.replace('\\', '/');
        String[] parts = name.split("/");
        for (String part : parts) {
            if (part.equals("build") || part.equals(".gradle") || part.equals(".git") ||
                    part.equals(".idea") || part.equals(".cxx") || part.equals(".externalNativeBuild") ||
                    part.equals(".androidide")) {
                return true;
            }
        }
        return name.equals("local.properties") || name.endsWith("/local.properties");
    }

    private static final class ProjectExtractionCancellation implements CancellationSignal {
        private volatile boolean cancelled;
        @Override public boolean isCancelled() { return cancelled; }
    }
}
