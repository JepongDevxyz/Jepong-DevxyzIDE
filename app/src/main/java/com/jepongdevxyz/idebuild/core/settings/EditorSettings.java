package com.jepongdevxyz.idebuild.core.settings;

/** Immutable editor preferences with mobile-safe validation. */
public final class EditorSettings {
    public static final int MIN_FONT_SIZE_SP = 10;
    public static final int MAX_FONT_SIZE_SP = 24;
    public static final int DEFAULT_FONT_SIZE_SP = 13;
    public static final boolean DEFAULT_WORD_WRAP = true;
    public static final int MIN_TAB_WIDTH = 2;
    public static final int MAX_TAB_WIDTH = 8;
    public static final int DEFAULT_TAB_WIDTH = 4;
    public static final boolean DEFAULT_AUTOSAVE = false;

    private final int fontSizeSp;
    private final boolean wordWrap;
    private final int tabWidth;
    private final boolean autosaveEnabled;

    public EditorSettings(int fontSizeSp, boolean wordWrap) {
        this(fontSizeSp, wordWrap, DEFAULT_TAB_WIDTH, DEFAULT_AUTOSAVE);
    }

    public EditorSettings(int fontSizeSp, boolean wordWrap, int tabWidth, boolean autosaveEnabled) {
        this.fontSizeSp = clampFontSize(fontSizeSp);
        this.wordWrap = wordWrap;
        this.tabWidth = clampTabWidth(tabWidth);
        this.autosaveEnabled = autosaveEnabled;
    }

    public static EditorSettings defaults() {
        return new EditorSettings(DEFAULT_FONT_SIZE_SP, DEFAULT_WORD_WRAP, DEFAULT_TAB_WIDTH, DEFAULT_AUTOSAVE);
    }

    public int getFontSizeSp() {
        return fontSizeSp;
    }

    public boolean isWordWrap() {
        return wordWrap;
    }

    public int getTabWidth() {
        return tabWidth;
    }

    public boolean isAutosaveEnabled() {
        return autosaveEnabled;
    }

    public EditorSettings withFontSizeSp(int value) {
        return new EditorSettings(value, wordWrap, tabWidth, autosaveEnabled);
    }

    public EditorSettings withWordWrap(boolean value) {
        return new EditorSettings(fontSizeSp, value, tabWidth, autosaveEnabled);
    }

    public EditorSettings withTabWidth(int value) {
        return new EditorSettings(fontSizeSp, wordWrap, value, autosaveEnabled);
    }

    public EditorSettings withAutosaveEnabled(boolean value) {
        return new EditorSettings(fontSizeSp, wordWrap, tabWidth, value);
    }

    public static int clampFontSize(int value) {
        if (value < MIN_FONT_SIZE_SP) return MIN_FONT_SIZE_SP;
        if (value > MAX_FONT_SIZE_SP) return MAX_FONT_SIZE_SP;
        return value;
    }

    public static int clampTabWidth(int value) {
        if (value < MIN_TAB_WIDTH) return MIN_TAB_WIDTH;
        if (value > MAX_TAB_WIDTH) return MAX_TAB_WIDTH;
        return value;
    }
}
