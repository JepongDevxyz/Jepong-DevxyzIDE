package com.jepongdevxyz.idebuild.core.storage;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Measures and clears only explicitly allowlisted cache directories beneath a
 * trusted app-owned root. Canonical containment checks prevent traversal and
 * symlink escapes from reaching project source or unrelated storage.
 */
public final class CacheMaintenanceService {
    private final File allowedRoot;
    private final Set<String> allowedNames = new HashSet<String>();

    public CacheMaintenanceService(File allowedRoot, String[] allowedNames) throws IOException {
        if (allowedRoot == null || !allowedRoot.isDirectory()) {
            throw new IllegalArgumentException("allowedRoot must be an existing directory");
        }
        if (allowedNames == null || allowedNames.length == 0) {
            throw new IllegalArgumentException("allowedNames must not be empty");
        }
        this.allowedRoot = allowedRoot.getCanonicalFile();
        for (String name : allowedNames) {
            String clean = validateSimpleName(name);
            this.allowedNames.add(clean);
        }
    }

    public CacheStats measure(String cacheName, int maxEntries) throws IOException {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        File target = resolveAllowed(cacheName);
        if (!target.exists()) return new CacheStats(0L, 0, 0);
        Counter counter = new Counter(maxEntries);
        scan(target, counter);
        return new CacheStats(counter.bytes, counter.files, counter.entries);
    }

    public CacheStats clear(String cacheName, int maxEntries) throws IOException {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        File target = resolveAllowed(cacheName);
        if (!target.exists()) return new CacheStats(0L, 0, 0);

        Counter counter = new Counter(maxEntries);
        scan(target, counter);
        deleteTree(target, new Counter(maxEntries));
        return new CacheStats(counter.bytes, counter.files, counter.entries);
    }

    private File resolveAllowed(String cacheName) throws IOException {
        String clean = validateSimpleName(cacheName);
        if (!allowedNames.contains(clean)) {
            throw new IllegalArgumentException("Cache name is not allowlisted: " + clean);
        }

        File lexical = new File(allowedRoot, clean).getAbsoluteFile();
        File canonical = lexical.getCanonicalFile();
        requireContained(canonical);

        // Existing aliases/symlinks are never maintenance targets. Even a link
        // that currently resolves inside the root could later be retargeted.
        if (lexical.exists() && !lexical.getPath().equals(canonical.getPath())) {
            throw new IOException("Cache path resolves through an alias or symlink: " + clean);
        }
        return canonical;
    }

    private void scan(File entry, Counter counter) throws IOException {
        File canonical = entry.getCanonicalFile();
        requireContained(canonical);
        counter.visit();

        if (canonical.isDirectory()) {
            File[] children = canonical.listFiles();
            if (children == null) throw new IOException("Could not list cache directory: " + canonical.getAbsolutePath());
            for (File child : children) {
                File childCanonical = child.getCanonicalFile();
                requireContained(childCanonical);
                if (!child.getAbsoluteFile().getPath().equals(childCanonical.getPath())) {
                    throw new IOException("Cache contains an alias or symlink: " + child.getName());
                }
                scan(childCanonical, counter);
            }
        } else if (canonical.isFile()) {
            counter.files++;
            long length = canonical.length();
            if (length > 0L && counter.bytes > Long.MAX_VALUE - length) {
                throw new IOException("Cache size overflow");
            }
            counter.bytes += length;
        } else {
            throw new IOException("Unsupported cache entry: " + canonical.getAbsolutePath());
        }
    }

    private void deleteTree(File entry, Counter counter) throws IOException {
        File canonical = entry.getCanonicalFile();
        requireContained(canonical);
        counter.visit();

        if (canonical.isDirectory()) {
            File[] children = canonical.listFiles();
            if (children == null) throw new IOException("Could not list cache directory: " + canonical.getAbsolutePath());
            for (File child : children) {
                File childCanonical = child.getCanonicalFile();
                requireContained(childCanonical);
                if (!child.getAbsoluteFile().getPath().equals(childCanonical.getPath())) {
                    throw new IOException("Cache contains an alias or symlink: " + child.getName());
                }
                deleteTree(childCanonical, counter);
            }
        }
        if (!canonical.delete()) throw new IOException("Could not delete cache entry: " + canonical.getAbsolutePath());
    }

    private void requireContained(File candidate) throws IOException {
        String rootPath = allowedRoot.getCanonicalPath();
        String candidatePath = candidate.getCanonicalPath();
        String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
        if (!candidatePath.startsWith(prefix)) {
            throw new IOException("Cache path escapes the allowed root");
        }
    }

    private static String validateSimpleName(String value) {
        if (value == null) throw new IllegalArgumentException("Cache name must not be null");
        String name = value.trim();
        if (name.length() == 0
                || name.equals(".")
                || name.equals("..")
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.indexOf('\u0000') >= 0
                || name.contains("..")) {
            throw new IllegalArgumentException("Unsafe cache name");
        }
        return name;
    }

    public static final class CacheStats {
        private final long bytes;
        private final int files;
        private final int entries;

        private CacheStats(long bytes, int files, int entries) {
            this.bytes = bytes;
            this.files = files;
            this.entries = entries;
        }

        public long getBytes() { return bytes; }
        public int getFiles() { return files; }
        public int getEntries() { return entries; }
    }

    private static final class Counter {
        private final int maxEntries;
        private int entries;
        private int files;
        private long bytes;

        private Counter(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        private void visit() throws IOException {
            if (entries >= maxEntries) throw new IOException("Cache exceeds maintenance entry limit");
            entries++;
        }
    }
}
