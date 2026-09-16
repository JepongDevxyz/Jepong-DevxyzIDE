package com.jepongdevxyz.idebuild.core.resources;

import java.io.File;

/** Immutable Android resource entry discovered under a project's res directory. */
public final class ResourceItem {
    private final String type;
    private final String qualifier;
    private final String name;
    private final String reference;
    private final File file;

    public ResourceItem(String type, String qualifier, String name, String reference, File file) {
        this.type = type == null ? "" : type;
        this.qualifier = qualifier == null ? "" : qualifier;
        this.name = name == null ? "" : name;
        this.reference = reference == null ? "" : reference;
        this.file = file;
    }

    public String getType() { return type; }
    public String getQualifier() { return qualifier; }
    public String getName() { return name; }
    public String getReference() { return reference; }
    public File getFile() { return file; }

    @Override public String toString() {
        return reference + (qualifier.length() == 0 ? "" : " [" + qualifier + "]");
    }
}
