package com.jepongdevxyz.idebuild.core.build;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Detected Gradle output artifact from a real project build directory. */
public final class BuildArtifact {
    private final String type;
    private final String variant;
    private final File file;
    private final String relativePath;
    private final long sizeBytes;
    private final long modifiedTimeMillis;

    public BuildArtifact(String type,
                         String variant,
                         File file,
                         String relativePath,
                         long sizeBytes,
                         long modifiedTimeMillis) {
        if (type == null || type.length() == 0) throw new IllegalArgumentException("type must not be empty");
        if (variant == null || variant.length() == 0) throw new IllegalArgumentException("variant must not be empty");
        if (file == null) throw new IllegalArgumentException("file must not be null");
        if (relativePath == null || relativePath.length() == 0) throw new IllegalArgumentException("relativePath must not be empty");
        this.type = type;
        this.variant = variant;
        this.file = file;
        this.relativePath = relativePath;
        this.sizeBytes = Math.max(0L, sizeBytes);
        this.modifiedTimeMillis = Math.max(0L, modifiedTimeMillis);
    }

    /** Kept for source compatibility with callers that do not yet provide variant metadata. */
    public BuildArtifact(String type, File file, String relativePath, long sizeBytes, long modifiedTimeMillis) {
        this(type, "unknown", file, relativePath, sizeBytes, modifiedTimeMillis);
    }

    public String getType() { return type; }
    public String getVariant() { return variant; }
    public File getFile() { return file; }
    public String getRelativePath() { return relativePath; }
    public long getSizeBytes() { return sizeBytes; }
    public long getModifiedTimeMillis() { return modifiedTimeMillis; }

    public String describe() {
        String modified = modifiedTimeMillis <= 0L
                ? "unknown"
                : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(modifiedTimeMillis));
        return type
                + " variant=" + variant
                + " · " + formatSize(sizeBytes)
                + " · modified=" + modified
                + " · " + relativePath;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kib = bytes / 1024.0d;
        if (kib < 1024.0d) return String.format(Locale.US, "%.1f KiB", kib);
        double mib = kib / 1024.0d;
        if (mib < 1024.0d) return String.format(Locale.US, "%.1f MiB", mib);
        return String.format(Locale.US, "%.2f GiB", mib / 1024.0d);
    }
}
