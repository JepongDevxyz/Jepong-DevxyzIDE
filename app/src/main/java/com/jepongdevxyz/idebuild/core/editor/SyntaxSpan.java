package com.jepongdevxyz.idebuild.core.editor;

/** Immutable syntax token range in editor-text coordinates. */
public final class SyntaxSpan {
    public static final String KEYWORD = "keyword";
    public static final String COMMENT = "comment";
    public static final String STRING = "string";
    public static final String NUMBER = "number";
    public static final String TAG = "tag";
    public static final String ATTRIBUTE = "attribute";

    private final int start;
    private final int end;
    private final String kind;

    public SyntaxSpan(int start, int end, String kind) {
        if (start < 0) throw new IllegalArgumentException("start must not be negative");
        if (end <= start) throw new IllegalArgumentException("end must be greater than start");
        if (kind == null || kind.length() == 0) throw new IllegalArgumentException("kind must not be blank");
        this.start = start;
        this.end = end;
        this.kind = kind;
    }

    public int getStart() { return start; }
    public int getEnd() { return end; }
    public String getKind() { return kind; }

    @Override public String toString() {
        return kind + "@" + start + ".." + end;
    }
}
