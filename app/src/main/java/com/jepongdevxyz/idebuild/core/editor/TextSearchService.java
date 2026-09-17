package com.jepongdevxyz.idebuild.core.editor;

import java.util.ArrayList;
import java.util.List;

/** Literal text search/replace helpers for the editor. No regex semantics are applied. */
public final class TextSearchService {
    private TextSearchService() { }

    public static List<Match> findAll(String text,
                                      String query,
                                      boolean matchCase,
                                      int maxResults) {
        String source = text == null ? "" : text;
        requireQuery(query);
        if (maxResults < 1) throw new IllegalArgumentException("maxResults must be positive");

        List<Match> matches = new ArrayList<Match>();
        int from = 0;
        while (from <= source.length() - query.length() && matches.size() < maxResults) {
            int offset = indexOf(source, query, from, matchCase);
            if (offset < 0) break;
            matches.add(buildMatch(source, query.length(), offset));
            from = offset + Math.max(1, query.length());
        }
        return matches;
    }

    public static Match findNext(String text,
                                 String query,
                                 int fromOffset,
                                 boolean matchCase,
                                 boolean wrap) {
        String source = text == null ? "" : text;
        requireQuery(query);
        int start = clamp(fromOffset, 0, source.length());
        int offset = indexOf(source, query, start, matchCase);
        if (offset < 0 && wrap && start > 0) offset = indexOf(source, query, 0, matchCase);
        return offset < 0 ? null : buildMatch(source, query.length(), offset);
    }

    public static ReplaceResult replaceAll(String text,
                                           String query,
                                           String replacement,
                                           boolean matchCase,
                                           int maxReplacements) {
        String source = text == null ? "" : text;
        String value = replacement == null ? "" : replacement;
        requireQuery(query);
        if (maxReplacements < 1) throw new IllegalArgumentException("maxReplacements must be positive");

        StringBuilder output = new StringBuilder(source.length());
        int copiedThrough = 0;
        int searchFrom = 0;
        int replaced = 0;
        while (searchFrom <= source.length() - query.length() && replaced < maxReplacements) {
            int offset = indexOf(source, query, searchFrom, matchCase);
            if (offset < 0) break;
            output.append(source, copiedThrough, offset);
            output.append(value);
            copiedThrough = offset + query.length();
            searchFrom = copiedThrough;
            replaced++;
        }
        output.append(source, copiedThrough, source.length());
        return new ReplaceResult(output.toString(), replaced);
    }

    private static Match buildMatch(String text, int length, int offset) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        int lineEnd = text.indexOf('\n', offset);
        if (lineEnd < 0) lineEnd = text.length();
        if (lineEnd > lineStart && text.charAt(lineEnd - 1) == '\r') lineEnd--;
        return new Match(offset, length, line, offset - lineStart + 1, text.substring(lineStart, lineEnd));
    }

    private static int indexOf(String source, String query, int from, boolean matchCase) {
        int max = source.length() - query.length();
        for (int i = Math.max(0, from); i <= max; i++) {
            if (source.regionMatches(!matchCase, i, query, 0, query.length())) return i;
        }
        return -1;
    }

    private static void requireQuery(String query) {
        if (query == null || query.length() == 0) throw new IllegalArgumentException("query must not be empty");
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static final class Match {
        private final int offset;
        private final int length;
        private final int lineNumber;
        private final int columnNumber;
        private final String lineText;

        private Match(int offset, int length, int lineNumber, int columnNumber, String lineText) {
            this.offset = offset;
            this.length = length;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.lineText = lineText;
        }

        public int getOffset() { return offset; }
        public int getLength() { return length; }
        public int getLineNumber() { return lineNumber; }
        public int getColumnNumber() { return columnNumber; }
        public String getLineText() { return lineText; }
    }

    public static final class ReplaceResult {
        private final String text;
        private final int replacementCount;

        private ReplaceResult(String text, int replacementCount) {
            this.text = text;
            this.replacementCount = replacementCount;
        }

        public String getText() { return text; }
        public int getReplacementCount() { return replacementCount; }
    }
}
