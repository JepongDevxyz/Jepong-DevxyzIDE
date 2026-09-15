package com.jepongdevxyz.idebuild.core.editor;

import com.jepongdevxyz.idebuild.core.ProjectPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Owns open editor tabs and the active tab without depending on Android UI classes. */
public final class EditorSession {
    private final List<EditorDocument> documents = new ArrayList<EditorDocument>();
    private int activeIndex = -1;

    public EditorDocument open(ProjectPath path, String text) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        int existing = indexOf(path);
        if (existing >= 0) {
            activeIndex = existing;
            return documents.get(existing);
        }
        EditorDocument document = new EditorDocument(path, text);
        documents.add(document);
        activeIndex = documents.size() - 1;
        return document;
    }

    public boolean switchTo(ProjectPath path) {
        int index = indexOf(path);
        if (index < 0) return false;
        activeIndex = index;
        return true;
    }

    public EditorDocument getActive() {
        return activeIndex >= 0 && activeIndex < documents.size() ? documents.get(activeIndex) : null;
    }

    public EditorDocument find(ProjectPath path) {
        int index = indexOf(path);
        return index < 0 ? null : documents.get(index);
    }

    public boolean close(ProjectPath path, boolean discardDirty) {
        int index = indexOf(path);
        if (index < 0) return true;
        EditorDocument document = documents.get(index);
        if (document.isDirty() && !discardDirty) return false;

        documents.remove(index);
        if (documents.isEmpty()) {
            activeIndex = -1;
        } else if (index < activeIndex) {
            activeIndex--;
        } else if (index == activeIndex) {
            activeIndex = Math.min(index, documents.size() - 1);
        }
        return true;
    }

    public boolean closeOthers(ProjectPath keep, boolean discardDirty) {
        int keepIndex = indexOf(keep);
        if (keepIndex < 0) return false;
        if (!discardDirty) {
            for (int i = 0; i < documents.size(); i++) {
                if (i != keepIndex && documents.get(i).isDirty()) return false;
            }
        }
        EditorDocument kept = documents.get(keepIndex);
        documents.clear();
        documents.add(kept);
        activeIndex = 0;
        return true;
    }

    public boolean closeAll(boolean discardDirty) {
        if (!discardDirty && hasDirtyDocuments()) return false;
        documents.clear();
        activeIndex = -1;
        return true;
    }

    public boolean hasDirtyDocuments() {
        for (EditorDocument document : documents) {
            if (document.isDirty()) return true;
        }
        return false;
    }

    public int size() { return documents.size(); }

    public List<EditorDocument> getDocuments() {
        return Collections.unmodifiableList(documents);
    }

    private int indexOf(ProjectPath path) {
        if (path == null) return -1;
        for (int i = 0; i < documents.size(); i++) {
            if (samePath(documents.get(i).getPath(), path)) return i;
        }
        return -1;
    }

    private static boolean samePath(ProjectPath left, ProjectPath right) {
        return left.getBackendId().equals(right.getBackendId())
                && left.getRelativePath().equals(right.getRelativePath());
    }
}
