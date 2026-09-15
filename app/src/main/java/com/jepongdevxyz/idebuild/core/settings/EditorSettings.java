package com.jepongdevxyz.idebuild.core.settings;

/** Immutable editor preferences with mobile-safe validation. */
public final class EditorSettings {
    public static final int MIN_FONT_SIZE_SP = 10;
    public static final int MAX_FONT_SIZE_SP = 24;
    public static final int DEFAULT_FONT_SIZE_SP = 13;
    public static final boolean DEFAULT_WORD_WRAP = true;

    private final int fontSizeSp;
    private final boolean wordWrap;

    public EditorSettings(int fontSizeSp, boolean wordWrap) {
        this.fontSizeSp = clampFontSize(fontSizeSp);
        this.wordWrap = wordWrap;
    }

    public static EditorSettings defaults() {
        return new EditorSettings(DEFAULT_FONT_SIZE_SP, DEFAULT_WORD_WRAP);
    }

    public int getFontSizeSp() {
        return fontSizeSp;
    }

    public boolean isWordWrap() {
        return wordWrap;
    }

    public EditorSettings withFontSizeSp(int value) {
        return new EditorSettings(value, wordWrap);
    }

    public EditorSettings withWordWrap(boolean value) {
        return new EditorSettings(fontSizeSp, value);
    }

    public static int clampFontSize(int value) {
        if (value < MIN_FONT_SIZE_SP) return MIN_FONT_SIZE_SP;
        if (value > MAX_FONT_SIZE_SP) return MAX_FONT_SIZE_SP;
        return value;
    }
}
