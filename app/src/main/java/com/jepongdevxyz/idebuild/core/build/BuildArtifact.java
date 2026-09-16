package com.jepongdevxyz.idebuild.core.build;

import java.io.File;

/** Detected Gradle output artifact from a real project build directory. */
public final class BuildArtifact {
    private final String type;
    private final File file;
    private final String relativePath;
    private final long sizeBytes;
    private final long modifiedTimeMillis;

    public BuildArtifact(String type, File file, String relativePath, long sizeBytes, long modifiedTimeMillis) {
        if (type == null || type.length() == 0) throw new IllegalArgumentException("type must not be empty");
        if (file == null) throw new IllegalArgumentException("file must not be null");
        if (relativePath == null || relativePath.length() == 0) throw new IllegalArgumentException("relativePath must not be empty");
        this.type = type;
        this.file = file;
        this.relativePath = relativePath;
        this.sizeBytes = Math.max(0L, sizeBytes);
        this.modifiedTimeMillis = Math.max(0L, modifiedTimeMillis);
    }

    public String getType() { return type; }
    public File getFile() { return file; }
    public String getRelativePath() { return relativePath; }
    public long getSizeBytes() { return sizeBytes; }
    public long getModifiedTimeMillis() { return modifiedTimeMillis; }
}
