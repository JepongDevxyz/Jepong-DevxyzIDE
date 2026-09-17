package com.jepongdevxyz.idebuild.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a source-focused backup ZIP without generated build/cache directories. */
public final class ProjectArchiveService {
    private static final int BUFFER_BYTES = 64 * 1024;

    private ProjectArchiveService() { }

    public static ArchiveResult writeSourceArchive(File projectRoot,
                                                   OutputStream output,
                                                   int maxFileEntries,
                                                   long maxUncompressedBytes) throws IOException {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            throw new IllegalArgumentException("projectRoot must be an existing directory");
        }
        if (output == null) throw new IllegalArgumentException("output must not be null");
        if (maxFileEntries < 1) throw new IllegalArgumentException("maxFileEntries must be positive");
        if (maxUncompressedBytes < 0) throw new IllegalArgumentException("maxUncompressedBytes must not be negative");

        File canonicalRoot = projectRoot.getCanonicalFile();
        Counter counter = new Counter(maxFileEntries, maxUncompressedBytes);
        ZipOutputStream zip = new ZipOutputStream(output);
        archiveChildren(canonicalRoot, canonicalRoot, zip, counter);
        zip.finish();
        zip.flush();
        return new ArchiveResult(counter.fileEntries, counter.uncompressedBytes);
    }

    private static void archiveChildren(File root,
                                        File directory,
                                        ZipOutputStream zip,
                                        Counter counter) throws IOException {
        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Could not list directory: " + directory.getAbsolutePath());
        Arrays.sort(children, new Comparator<File>() {
            @Override public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });

        for (File child : children) {
            if (shouldSkip(child)) continue;
            File canonical = child.getCanonicalFile();
            requireContained(root, canonical);
            if (isSymbolicAlias(child, canonical)) continue;

            if (canonical.isDirectory()) {
                archiveChildren(root, canonical, zip, counter);
            } else if (canonical.isFile()) {
                addFile(root, canonical, zip, counter);
            }
        }
    }

    private static void addFile(File root,
                                File file,
                                ZipOutputStream zip,
                                Counter counter) throws IOException {
        counter.beforeFile();
        String name = relativeName(root, file);
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);

        InputStream input = null;
        try {
            input = new BufferedInputStream(new FileInputStream(file), BUFFER_BYTES);
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) != -1) {
                counter.addBytes(read);
                zip.write(buffer, 0, read);
            }
        } finally {
            if (input != null) try { input.close(); } catch (IOException ignored) { }
            zip.closeEntry();
        }
        counter.fileEntries++;
    }

    private static String relativeName(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
        if (!filePath.startsWith(prefix)) throw new IOException("Archive entry escaped project root");
        String relative = filePath.substring(prefix.length()).replace(File.separatorChar, '/');
        if (relative.length() == 0 || relative.startsWith("/") || relative.contains("../") || relative.equals("..")) {
            throw new IOException("Unsafe archive entry: " + relative);
        }
        return relative;
    }

    private static void requireContained(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
        if (!filePath.startsWith(prefix)) throw new IOException("Project entry escapes project root: " + file.getName());
    }

    private static boolean isSymbolicAlias(File original, File canonical) throws IOException {
        return !original.getAbsoluteFile().getPath().equals(canonical.getPath());
    }

    private static boolean shouldSkip(File file) {
        if (!file.isDirectory()) return false;
        String name = file.getName();
        return "build".equals(name)
                || ".gradle".equals(name)
                || ".git".equals(name)
                || ".idea".equals(name)
                || ".cxx".equals(name)
                || ".kotlin".equals(name)
                || "captures".equals(name)
                || "out".equals(name);
    }

    public static final class ArchiveResult {
        private final int fileEntries;
        private final long uncompressedBytes;

        private ArchiveResult(int fileEntries, long uncompressedBytes) {
            this.fileEntries = fileEntries;
            this.uncompressedBytes = uncompressedBytes;
        }

        public int getFileEntries() { return fileEntries; }
        public long getUncompressedBytes() { return uncompressedBytes; }
    }

    private static final class Counter {
        private final int maxFileEntries;
        private final long maxUncompressedBytes;
        private int fileEntries;
        private long uncompressedBytes;

        private Counter(int maxFileEntries, long maxUncompressedBytes) {
            this.maxFileEntries = maxFileEntries;
            this.maxUncompressedBytes = maxUncompressedBytes;
        }

        private void beforeFile() throws IOException {
            if (fileEntries >= maxFileEntries) throw new IOException("Project archive exceeds file-entry limit");
        }

        private void addBytes(int count) throws IOException {
            if (count < 0) throw new IllegalArgumentException("count must not be negative");
            if (uncompressedBytes > maxUncompressedBytes - count) {
                throw new IOException("Project archive exceeds uncompressed-size limit");
            }
            uncompressedBytes += count;
        }
    }
}
