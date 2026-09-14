package com.jepongdevxyz.idebuild.core.toolchain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs a DevxyzIDE-prefixed Termux-style bootstrap into files/usr. */
public final class TerminalBootstrapInstaller {
    private static final String META = "devxyz-bootstrap.properties";
    private static final String SYMLINKS = "SYMLINKS.txt";
    private static final int MAX_ENTRIES = 50000;
    private static final long MAX_EXPANDED_BYTES = 3L * 1024L * 1024L * 1024L;

    private TerminalBootstrapInstaller() {}

    public static InstallResult install(InputStream zipInput, File appFilesDir, String expectedApplicationId) throws IOException {
        if (zipInput == null) throw new IOException("Bootstrap input is missing");
        if (appFilesDir == null) throw new IOException("App files directory is missing");
        if (expectedApplicationId == null || !expectedApplicationId.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) {
            throw new IOException("Invalid expected applicationId");
        }
        File filesRoot = appFilesDir.getCanonicalFile();
        if (!filesRoot.exists() && !filesRoot.mkdirs()) throw new IOException("Cannot create app files directory");
        File finalPrefix = new File(filesRoot, "usr").getCanonicalFile();
        File staging = new File(filesRoot, ".usr.installing-" + System.nanoTime()).getCanonicalFile();
        if (!inside(filesRoot, staging) || !staging.mkdirs()) throw new IOException("Cannot create bootstrap staging directory");

        Properties metadata = null;
        List<Symlink> symlinks = new ArrayList<>();
        int files = 0;
        long expanded = 0;
        boolean success = false;
        try {
            try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(zipInput))) {
                ZipEntry entry;
                int entries = 0;
                byte[] buffer = new byte[64 * 1024];
                while ((entry = zin.getNextEntry()) != null) {
                    if (++entries > MAX_ENTRIES) throw new IOException("Bootstrap has too many entries");
                    String name = normalizeEntryName(entry.getName());
                    if (name.isEmpty()) continue;
                    if (META.equals(name)) {
                        byte[] bytes = readEntry(zin, 1024 * 1024);
                        expanded += bytes.length;
                        metadata = new Properties();
                        metadata.load(new ByteArrayInputStream(bytes));
                        continue;
                    }
                    if (SYMLINKS.equals(name)) {
                        byte[] bytes = readEntry(zin, 16L * 1024L * 1024L);
                        expanded += bytes.length;
                        parseSymlinks(new String(bytes, StandardCharsets.UTF_8), expectedApplicationId, symlinks);
                        continue;
                    }
                    File out = new File(staging, name).getCanonicalFile();
                    if (!inside(staging, out)) throw new IOException("Bootstrap ZIP path traversal: " + name);
                    if (entry.isDirectory()) {
                        if (!out.exists() && !out.mkdirs()) throw new IOException("Cannot create bootstrap directory: " + name);
                        continue;
                    }
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create directory for " + name);
                    try (OutputStream output = new BufferedOutputStream(new FileOutputStream(out))) {
                        int read;
                        while ((read = zin.read(buffer)) != -1) {
                            expanded += read;
                            if (expanded > MAX_EXPANDED_BYTES) throw new IOException("Bootstrap expands beyond safety limit");
                            output.write(buffer, 0, read);
                        }
                    }
                    files++;
                    if (shouldExecute(name)) out.setExecutable(true, false);
                }
            }
            validateMetadata(metadata, expectedApplicationId);
            if (symlinks.isEmpty()) throw new IOException("Bootstrap does not contain any symlinks");
            for (Symlink link : symlinks) {
                File destination = new File(staging, link.destination).getCanonicalFile();
                if (!inside(staging, destination)) throw new IOException("Symlink destination escapes prefix: " + link.destination);
                File parent = destination.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create symlink directory");
                Path path = destination.toPath();
                Files.deleteIfExists(path);
                Files.createSymbolicLink(path, java.nio.file.Paths.get(link.target));
            }
            replaceDirectory(staging, finalPrefix);
            success = true;
            return new InstallResult(finalPrefix, metadata.getProperty("arch", "unknown"), files, symlinks.size());
        } finally {
            if (!success && staging.exists()) deleteRecursively(staging);
        }
    }

    private static void validateMetadata(Properties metadata, String expectedApplicationId) throws IOException {
        if (metadata == null) throw new IOException("Missing " + META);
        if (!"1".equals(metadata.getProperty("format"))) throw new IOException("Unsupported bootstrap format");
        String actual = metadata.getProperty("applicationId", "").trim();
        if (!expectedApplicationId.equals(actual)) throw new IOException("Bootstrap applicationId mismatch: " + actual);
        String arch = metadata.getProperty("arch", "").trim();
        if (!(arch.equals("aarch64") || arch.equals("arm") || arch.equals("arm64-v8a") || arch.equals("armeabi-v7a"))) {
            throw new IOException("Unsupported bootstrap arch: " + arch);
        }
    }

    private static void parseSymlinks(String text, String applicationId, List<Symlink> out) throws IOException {
        String canonicalDataPrefix = "/data/data/" + applicationId + "/files/usr/";
        String userDataPrefix = "/data/user/0/" + applicationId + "/files/usr/";
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("←", -1);
            if (parts.length != 2) throw new IOException("Malformed bootstrap symlink line");
            String target = parts[0].trim();
            String destination = normalizeEntryName(parts[1].trim());
            if (destination.isEmpty()) throw new IOException("Empty bootstrap symlink destination");
            if (target.startsWith("/")) {
                if (!(target.startsWith(canonicalDataPrefix) || target.startsWith(userDataPrefix))) {
                    throw new IOException("Absolute symlink target is outside DevxyzIDE prefix: " + target);
                }
            } else if (target.equals("..") || target.startsWith("../") || target.contains("/../")) {
                throw new IOException("Unsafe relative symlink target: " + target);
            }
            out.add(new Symlink(target, destination));
        }
    }

    private static String normalizeEntryName(String raw) throws IOException {
        if (raw == null) return "";
        String name = raw.replace('\\', '/');
        while (name.startsWith("./")) name = name.substring(2);
        if (name.startsWith("/") || name.equals("..") || name.startsWith("../") || name.contains("/../")) {
            throw new IOException("Unsafe bootstrap path: " + raw);
        }
        return name;
    }

    private static byte[] readEntry(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("Bootstrap metadata exceeds size limit");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static boolean shouldExecute(String name) {
        return name.startsWith("bin/") || name.startsWith("libexec/") ||
                name.startsWith("lib/apt/apt-helper") || name.startsWith("lib/apt/methods/");
    }

    private static boolean inside(File root, File child) throws IOException {
        String rp = root.getCanonicalPath();
        String cp = child.getCanonicalPath();
        return cp.equals(rp) || cp.startsWith(rp + File.separator);
    }

    private static void replaceDirectory(File staging, File destination) throws IOException {
        File backup = new File(destination.getParentFile(), ".usr.backup-" + System.nanoTime());
        boolean hadOld = destination.exists();
        if (hadOld && !destination.renameTo(backup)) throw new IOException("Cannot move old runtime out of the way");
        boolean committed = false;
        try {
            if (!staging.renameTo(destination)) throw new IOException("Cannot activate new runtime prefix");
            committed = true;
        } finally {
            if (!committed && hadOld && backup.exists()) backup.renameTo(destination);
        }
        if (backup.exists()) deleteRecursively(backup);
    }

    private static void deleteRecursively(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath())) {
            if (!file.delete()) throw new IOException("Cannot delete symlink " + file);
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        if (file.exists() && !file.delete()) throw new IOException("Cannot delete " + file);
    }

    private static final class Symlink {
        final String target;
        final String destination;
        Symlink(String target, String destination) { this.target = target; this.destination = destination; }
    }

    public static final class InstallResult {
        private final File prefix;
        private final String arch;
        private final int files;
        private final int symlinks;
        InstallResult(File prefix, String arch, int files, int symlinks) {
            this.prefix = prefix;
            this.arch = arch;
            this.files = files;
            this.symlinks = symlinks;
        }
        public File getPrefix() { return prefix; }
        public String getArch() { return arch; }
        public int getFiles() { return files; }
        public int getSymlinks() { return symlinks; }
    }
}
