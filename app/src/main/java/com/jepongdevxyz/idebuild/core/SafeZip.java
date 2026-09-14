package com.jepongdevxyz.idebuild.core;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SafeZip {
    private static final int BUFFER_SIZE = 32 * 1024;

    private SafeZip() {}

    public static void extract(InputStream source, File destination, int maxEntries, long maxBytes) throws IOException {
        extractInternal(source, destination, maxEntries, maxBytes, false);
    }

    public static void extractProject(InputStream source, File destination, int maxEntries, long maxBytes) throws IOException {
        extractInternal(source, destination, maxEntries, maxBytes, true);
    }

    private static void extractInternal(InputStream source, File destination, int maxEntries, long maxBytes, boolean projectMode) throws IOException {
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
                entries++;
                if (entries > maxEntries) throw new IOException("Archive has too many entries");

                File out = new File(destination, entry.getName());
                String outPath = out.getCanonicalPath();
                if (!outPath.equals(canonicalDestination) && !outPath.startsWith(targetRoot)) {
                    throw new IOException("Unsafe ZIP path: " + entry.getName());
                }

                if (projectMode && shouldSkipProjectEntry(entry.getName())) {
                    zip.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) throw new IOException("Cannot create directory: " + out);
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create directory: " + parent);
                    try (OutputStream output = new BufferedOutputStream(new FileOutputStream(out))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            if (!isWithinExpandedLimit(totalBytes, read, maxBytes)) throw new IOException("Archive exceeds extraction size limit");
                            totalBytes += read;
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
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
}
