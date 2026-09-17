package com.jepongdevxyz.idebuild.core.editor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bounded text snapshot history for the lightweight mobile editor.
 * Large snapshots are deliberately ignored to prevent unbounded memory growth.
 */
public final class EditorUndoHistory {
    private final int maxEntries;
    private final int maxSnapshotChars;
    private final Deque<String> undo = new ArrayDeque<String>();
    private final Deque<String> redo = new ArrayDeque<String>();
    private String lastRecorded;

    public EditorUndoHistory(int maxEntries, int maxSnapshotChars) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        if (maxSnapshotChars < 1) throw new IllegalArgumentException("maxSnapshotChars must be positive");
        this.maxEntries = maxEntries;
        this.maxSnapshotChars = maxSnapshotChars;
    }

    public void reset(String text) {
        undo.clear();
        redo.clear();
        lastRecorded = safe(text);
    }

    public void record(String newText) {
        String next = safe(newText);
        if (lastRecorded == null) {
            lastRecorded = next;
            return;
        }
        if (next.equals(lastRecorded)) return;
        if (lastRecorded.length() > maxSnapshotChars || next.length() > maxSnapshotChars) {
            undo.clear();
            redo.clear();
            lastRecorded = next;
            return;
        }
        undo.addLast(lastRecorded);
        trim(undo);
        redo.clear();
        lastRecorded = next;
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    public String undo(String currentText) {
        String current = safe(currentText);
        if (undo.isEmpty()) return current;
        if (current.length() <= maxSnapshotChars) {
            redo.addLast(current);
            trim(redo);
        }
        String previous = undo.removeLast();
        lastRecorded = previous;
        return previous;
    }

    public String redo(String currentText) {
        String current = safe(currentText);
        if (redo.isEmpty()) return current;
        if (current.length() <= maxSnapshotChars) {
            undo.addLast(current);
            trim(undo);
        }
        String next = redo.removeLast();
        lastRecorded = next;
        return next;
    }

    private void trim(Deque<String> values) {
        while (values.size() > maxEntries) values.removeFirst();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
