package com.jepongdevxyz.idebuild.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result for one lazy project-directory listing operation. */
public final class ProjectDirectoryListing {
    private final List<ProjectEntry> entries;
    private final boolean truncated;

    public ProjectDirectoryListing(List<ProjectEntry> entries, boolean truncated) {
        if (entries == null) throw new IllegalArgumentException("entries must not be null");
        this.entries = Collections.unmodifiableList(new ArrayList<ProjectEntry>(entries));
        this.truncated = truncated;
    }

    public List<ProjectEntry> getEntries() {
        return entries;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
