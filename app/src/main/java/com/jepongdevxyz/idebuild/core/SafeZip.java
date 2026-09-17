package com.jepongdevxyz.idebuild.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    /**
     * Progress callback for project extraction without an application-defined
     * entry/byte ceiling. The entry count is long so extraction itself does not
     * impose an int-sized project limit. Implementations should retain only
     * bounded state.
     */
    public interface ProjectProgressListener {
        void onProgress(String entryName, long entries, long expandedBytes) throws IOException;
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

    public static final ProjectProgressListener NO_PROJECT_PROGRESS = new ProjectProgressListener() {
        @Override public void onProgress(String entryName, long entries, long expandedBytes) { }
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

    /**
     * Project ZIP extraction with no application-defined byte or entry ceiling.
     * Physical limits still come from storage, the filesystem/provider and the
     * Java/Android runtime. Data is copied with one fixed-size reusable buffer.
     */
    public static void extractProject(InputStream source,
                                      File destination,
                                      CancellationSignal cancellation,
                                      ProjectProgressListener progress) throws IOException {
        extractProjectUnlimitedInternal(source, destination,
                cancellation == null ? NEVER_CANCELLED : cancellation,
                progress == null ? NO_PROJECT_PROGRESS : progress);
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

        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                checkCancelled(cancellation);
                entries++;
                if (entries > maxEntries) throw new IOException("Archive has too many entries");

                String normalizedName = normalizeAndValidateEntryName(entry.getName());
                File out = resolveContained(destination, canonicalDestination, targetRoot, normalizedName);

                if (projectMode && shouldSkipProjectEntry(normalizedName)) {
                    zip.closeEntry();
                    progress.onProgress(entries, totalBytes);
                    checkCancelled(cancellation);
                    continue;
                }

                if (entry.isDirectory() || normalizedName.endsWith("/")) {
                    ensureDirectory(out);
                    progress.onProgress(entries, totalBytes);
                    checkCancelled(cancellation);
                } else {
                    prepareOutputFile(out, normalizedName);
                    OutputStream output = new BufferedOutputStream(new FileOutputStream(out));
                    try {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            checkCancelled(cancellation);
                            if (!isWithinExpandedLimit(totalBytes, read, maxBytes)) throw new IOException("Archive exceeds extraction size limit");
                            totalBytes += read;
                            output.write(buffer, 0, read);
                            progress.onProgress(entries, totalBytes);
                            checkCancelled(cancellation);
                        }
                    } finally {
                        output.close();
                    }
                }
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        checkCancelled(cancellation);
    }

    private static void extractProjectUnlimitedInternal(InputStream source,
                                                        File destination,
                                                        CancellationSignal cancellation,
                                                        ProjectProgressListener progress) throws IOException {
        if (source == null) throw new IllegalArgumentException("source == null");
        if (destination == null) throw new IllegalArgumentException("destination == null");
        if (!destination.exists() && !destination.mkdirs()) throw new IOException("Cannot create destination: " + destination);

        String canonicalDestination = destination.getCanonicalPath();
        String targetRoot = canonicalDestination + File.separator;
        byte[] buffer = new byte[BUFFER_SIZE];
        long entries = 0L;
        long totalBytes = 0L;

        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                checkCancelled(cancellation);
                if (entries == Long.MAX_VALUE) throw new IOException("Archive entry counter overflow");
                entries++;

                String normalizedName = normalizeAndValidateEntryName(entry.getName());
                File out = resolveContained(destination, canonicalDestination, targetRoot, normalizedName);

                if (shouldSkipProjectEntry(normalizedName)) {
                    zip.closeEntry();
                    progress.onProgress(normalizedName, entries, totalBytes);
                    checkCancelled(cancellation);
                    continue;
                }

                if (entry.isDirectory() || normalizedName.endsWith("/")) {
                    ensureDirectory(out);
                    progress.onProgress(normalizedName, entries, totalBytes);
                    checkCancelled(cancellation);
                } else {
                    prepareOutputFile(out, normalizedName);
                    OutputStream output = new BufferedOutputStream(new FileOutputStream(out));
                    try {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            checkCancelled(cancellation);
                            if (read > 0 && totalBytes > Long.MAX_VALUE - read) {
                                throw new IOException("Archive expanded byte counter overflow");
                            }
                            output.write(buffer, 0, read);
                            totalBytes += read;
                            progress.onProgress(normalizedName, entries, totalBytes);
                            checkCancelled(cancellation);
                        }
                    } finally {
                        output.close();
                    }
                }
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        checkCancelled(cancellation);
    }

    private static String normalizeAndValidateEntryName(String rawName) throws IOException {
        if (rawName == null || rawName.length() == 0) throw new IOException("Invalid empty ZIP entry");
        if (rawName.indexOf('\u0000') >= 0) throw new IOException("Invalid ZIP entry name");

        String name = rawName.replace('\\', '/');
        if (name.startsWith("/") || name.startsWith("//")) throw new IOException("Unsafe absolute ZIP path: " + rawName);
        if (name.length() >= 2 && Character.isLetter(name.charAt(0)) && name.charAt(1) == ':') {
            throw new IOException("Unsafe absolute ZIP path: " + rawName);
        }

        String[] parts = name.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.equals("..") || part.equals(".")) throw new IOException("Unsafe ZIP path: " + rawName);
            if (part.length() == 0 && i != parts.length - 1) throw new IOException("Invalid ZIP path: " + rawName);
        }
        return name;
    }

    private static File resolveContained(File destination,
                                         String canonicalDestination,
                                         String targetRoot,
                                         String normalizedName) throws IOException {
        File out = new File(destination, normalizedName.replace('/', File.separatorChar));
        String outPath = out.getCanonicalPath();
        if (!outPath.equals(canonicalDestination) && !outPath.startsWith(targetRoot)) {
            throw new IOException("Unsafe ZIP path: " + normalizedName);
        }
        return out;
    }

    private static void ensureDirectory(File out) throws IOException {
        if (out.exists()) {
            if (!out.isDirectory()) throw new IOException("Conflicting ZIP entry: " + out);
            return;
        }
        if (!out.mkdirs()) throw new IOException("Cannot create directory: " + out);
    }

    private static void prepareOutputFile(File out, String entryName) throws IOException {
        if (out.exists()) throw new IOException("Conflicting or duplicate ZIP entry: " + entryName);
        File parent = out.getParentFile();
        if (parent != null) {
            if (parent.exists() && !parent.isDirectory()) throw new IOException("Conflicting ZIP path parent: " + entryName);
            if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create directory: " + parent);
        }
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
        if (rawName == null || rawName.length() == 0) return false;
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
