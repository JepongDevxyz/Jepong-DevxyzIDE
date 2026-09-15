package com.jepongdevxyz.idebuild.core.editor;

import com.jepongdevxyz.idebuild.core.ProjectPath;

/** In-memory state for one open editor tab. */
public final class EditorDocument {
    private final ProjectPath path;
    private String text;
    private String savedText;
    private int cursorOffset;
    private int selectionStart;
    private int selectionEnd;
    private int scrollY;

    public EditorDocument(ProjectPath path, String text) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        this.path = path;
        this.text = text == null ? "" : text;
        this.savedText = this.text;
    }

    public ProjectPath getPath() { return path; }
    public String getText() { return text; }

    public void setText(String value) {
        text = value == null ? "" : value;
        cursorOffset = clamp(cursorOffset, 0, text.length());
        selectionStart = clamp(selectionStart, 0, text.length());
        selectionEnd = clamp(selectionEnd, 0, text.length());
    }

    public boolean isDirty() { return !text.equals(savedText); }

    public void markSaved() { savedText = text; }

    public int getCursorOffset() { return cursorOffset; }
    public void setCursorOffset(int value) { cursorOffset = clamp(value, 0, text.length()); }

    public int getSelectionStart() { return selectionStart; }
    public void setSelectionStart(int value) { selectionStart = clamp(value, 0, text.length()); }

    public int getSelectionEnd() { return selectionEnd; }
    public void setSelectionEnd(int value) { selectionEnd = clamp(value, 0, text.length()); }

    public int getScrollY() { return scrollY; }
    public void setScrollY(int value) { scrollY = Math.max(0, value); }

    public String getDisplayName() {
        String relative = path.getRelativePath();
        int slash = relative.lastIndexOf('/');
        return slash < 0 ? relative : relative.substring(slash + 1);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
