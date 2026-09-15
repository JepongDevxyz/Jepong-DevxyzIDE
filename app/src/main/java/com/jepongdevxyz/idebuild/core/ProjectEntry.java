package com.jepongdevxyz.idebuild.core;

/** Lightweight metadata for one immediate project-directory child. */
public final class ProjectEntry {
    private final String name;
    private final ProjectPath path;
    private final boolean directory;
    private final long size;

    public ProjectEntry(String name, ProjectPath path, boolean directory, long size) {
        if (name == null || name.length() == 0) throw new IllegalArgumentException("name must not be blank");
        if (path == null) throw new IllegalArgumentException("path must not be null");
        this.name = name;
        this.path = path;
        this.directory = directory;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public ProjectPath getPath() {
        return path;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getSize() {
        return size;
    }
}
