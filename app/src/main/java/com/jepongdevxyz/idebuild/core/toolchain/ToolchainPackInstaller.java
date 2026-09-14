package com.jepongdevxyz.idebuild.core.toolchain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ToolchainPackInstaller {
    private static final String MANIFEST = "devxyz-toolchain.properties";
    private static final int MAX_ENTRIES = 20000;
    private static final long MAX_EXPANDED_BYTES = 2L * 1024L * 1024L * 1024L;

    private ToolchainPackInstaller() {}

    public static InstallResult install(File zipFile, File appFilesDir) throws IOException {
        if (zipFile == null || !zipFile.isFile()) throw new IOException("Toolchain pack ZIP not found");
        if (appFilesDir == null) throw new IOException("App files directory is missing");
        Properties props = new Properties();
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry manifestEntry = zip.getEntry(MANIFEST);
            if (manifestEntry == null || manifestEntry.isDirectory()) throw new IOException("Missing " + MANIFEST);
            try (InputStream in = zip.getInputStream(manifestEntry)) { props.load(in); }

            if (!"1".equals(props.getProperty("format"))) throw new IOException("Unsupported toolchain pack format");
            String target = normalizeTarget(props.getProperty("target"));
            LinkedHashMap<String, String> expected = expectedFiles(props);
            if (expected.isEmpty()) throw new IOException("Toolchain pack manifest has no files");

            File canonicalFiles = appFilesDir.getCanonicalFile();
            File finalDir = new File(canonicalFiles, target).getCanonicalFile();
            if (!inside(canonicalFiles, finalDir)) throw new IOException("Toolchain target escapes app files directory");
            File parent = finalDir.getParentFile();
            if (parent == null) throw new IOException("Invalid toolchain target");
            if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create toolchain parent directory");

            File tempDir = new File(parent, "." + finalDir.getName() + ".installing-" + System.nanoTime());
            if (!tempDir.mkdirs()) throw new IOException("Cannot create temporary install directory");
            boolean success = false;
            try {
                verifyAndExtract(zip, expected, tempDir);
                if (finalDir.exists()) deleteRecursively(finalDir);
                if (!tempDir.renameTo(finalDir)) {
                    copyDirectory(tempDir, finalDir);
                    deleteRecursively(tempDir);
                }
                markCommonExecutables(finalDir);
                success = true;
                return new InstallResult(finalDir, expected.size());
            } finally {
                if (!success && tempDir.exists()) deleteRecursively(tempDir);
            }
        }
    }

    private static String normalizeTarget(String raw) throws IOException {
        if (raw == null) throw new IOException("Toolchain pack target missing");
        String target = raw.replace('\\', '/').replaceAll("^/+", "");
        if (!target.startsWith("toolchains/") || target.contains("../") || target.equals("toolchains/")) {
            throw new IOException("Invalid toolchain pack target: " + raw);
        }
        return target;
    }

    private static LinkedHashMap<String, String> expectedFiles(Properties props) throws IOException {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        for (String key : props.stringPropertyNames()) if (key.startsWith("file.")) keys.add(key);
        Collections.sort(keys);
        for (String key : keys) {
            String path = key.substring("file.".length()).replace('\\', '/');
            if (path.isEmpty() || path.startsWith("/") || path.contains("../") || path.equals("..")) throw new IOException("Unsafe manifest path: " + path);
            String sha = props.getProperty(key, "").trim().toLowerCase(Locale.ROOT);
            if (!sha.matches("[0-9a-f]{64}")) throw new IOException("Invalid SHA-256 for " + path);
            out.put(path, sha);
        }
        return out;
    }

    private static void verifyAndExtract(ZipFile zip, LinkedHashMap<String, String> expected, File tempDir) throws IOException {
        Set<String> seen = new HashSet<>();
        int entries = 0;
        long expanded = 0;
        Enumeration<? extends ZipEntry> enumeration = zip.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            entries++;
            if (entries > MAX_ENTRIES) throw new IOException("Toolchain pack has too many entries");
            String name = entry.getName().replace('\\', '/');
            if (name.equals(MANIFEST)) continue;
            if (entry.isDirectory()) continue;
            String expectedSha = expected.get(name);
            if (expectedSha == null) throw new IOException("Unexpected file not listed in manifest: " + name);
            File out = new File(tempDir, name).getCanonicalFile();
            if (!inside(tempDir.getCanonicalFile(), out)) throw new IOException("Toolchain ZIP path traversal: " + name);
            File parent = out.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create directory for " + name);
            MessageDigest digest = sha256Digest();
            try (InputStream in = zip.getInputStream(entry); OutputStream output = new BufferedOutputStream(new FileOutputStream(out))) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_BYTES) throw new IOException("Toolchain pack expands beyond safety limit");
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            String actual = hex(digest.digest());
            if (!actual.equals(expectedSha)) throw new IOException("SHA-256 mismatch for " + name);
            seen.add(name);
        }
        for (String path : expected.keySet()) if (!seen.contains(path)) throw new IOException("Manifest file missing from ZIP: " + path);
    }

    private static boolean inside(File root, File child) throws IOException {
        String rootPath = root.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        return childPath.equals(rootPath) || childPath.startsWith(rootPath + File.separator);
    }

    private static MessageDigest sha256Digest() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (Exception e) { throw new IOException("SHA-256 unavailable", e); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    private static void markCommonExecutables(File root) {
        Deque<File> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            File file = queue.removeFirst();
            File[] children = file.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) queue.addLast(child);
                else if (isExecutableName(child.getName()) || child.getParentFile().getName().equals("bin")) child.setExecutable(true, false);
            }
        }
    }

    private static boolean isExecutableName(String name) {
        return name.equals("aapt2") || name.equals("aapt") || name.equals("zipalign") || name.equals("apksigner") ||
                name.equals("adb") || name.equals("git") || name.equals("cmake") || name.equals("ninja") || name.equals("java") || name.equals("javac");
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        if (file.exists() && !file.delete()) throw new IOException("Cannot delete " + file);
    }

    private static void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) throw new IOException("Cannot create " + target);
            File[] children = source.listFiles();
            if (children != null) for (File child : children) copyDirectory(child, new File(target, child.getName()));
        } else {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
            InputStream in = new BufferedInputStream(new FileInputStream(source));
            try {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(target));
                try {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
        }
    }

    public static final class InstallResult {
        private final File installedDirectory;
        private final int verifiedFiles;
        InstallResult(File installedDirectory, int verifiedFiles) {
            this.installedDirectory = installedDirectory;
            this.verifiedFiles = verifiedFiles;
        }
        public File getInstalledDirectory() { return installedDirectory; }
        public int getVerifiedFiles() { return verifiedFiles; }
    }
}
