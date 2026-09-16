package com.jepongdevxyz.idebuild.core.log;

import java.util.LinkedList;

/**
 * Thread-safe in-memory line buffer that keeps the newest output within a
 * deterministic line/character budget. Intended for build/terminal consoles
 * where unbounded TextView/process output would otherwise grow indefinitely.
 */
public final class BoundedLogBuffer {
    private static final String TRUNCATION_MARKER = "[older log output truncated]";

    private final int maxChars;
    private final int maxLines;
    private final LinkedList<String> lines = new LinkedList<String>();
    private long droppedLineCount;

    public BoundedLogBuffer(int maxChars, int maxLines) {
        if (maxChars <= TRUNCATION_MARKER.length() + 1) {
            throw new IllegalArgumentException("maxChars is too small for truncation marker");
        }
        if (maxLines < 1) throw new IllegalArgumentException("maxLines must be positive");
        this.maxChars = maxChars;
        this.maxLines = maxLines;
    }

    public synchronized void appendLine(String value) {
        String line = value == null ? "null" : normalize(value);
        lines.add(line);
        enforceBounds();
    }

    public synchronized String snapshot() {
        StringBuilder out = new StringBuilder(Math.min(maxChars, 1024));
        if (droppedLineCount > 0) out.append(TRUNCATION_MARKER);
        for (String line : lines) {
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        if (out.length() > maxChars) return out.substring(out.length() - maxChars);
        return out.toString();
    }

    public synchronized int lineCount() {
        return lines.size();
    }

    public synchronized long getDroppedLineCount() {
        return droppedLineCount;
    }

    public synchronized void clear() {
        lines.clear();
        droppedLineCount = 0;
    }

    private void enforceBounds() {
        while (lines.size() > maxLines) dropOldest();

        while (renderedLength() > maxChars && lines.size() > 1) dropOldest();

        if (renderedLength() > maxChars && lines.size() == 1) {
            String line = lines.getFirst();
            int markerCost = droppedLineCount > 0 ? TRUNCATION_MARKER.length() + 1 : 0;
            int available = maxChars - markerCost;
            if (available < 0) available = 0;
            if (line.length() > available) {
                lines.set(0, line.substring(line.length() - available));
            }
        }
    }

    private int renderedLength() {
        int length = droppedLineCount > 0 ? TRUNCATION_MARKER.length() : 0;
        for (String line : lines) {
            if (length > 0) length++;
            length += line.length();
            if (length > maxChars && lines.size() > 1) break;
        }
        return length;
    }

    private void dropOldest() {
        if (!lines.isEmpty()) {
            lines.removeFirst();
            droppedLineCount++;
        }
    }

    private static String normalize(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }
}
