import com.jepongdevxyz.idebuild.core.settings.EditorSettings;

public final class EditorSettingsHostTest {
    private static int passed;

    public static void main(String[] args) {
        defaultsAreStable();
        fontSizeClampsToSafeMobileRange();
        wordWrapIsPreserved();
        tabWidthClampsToPracticalRange();
        autosaveIsPreserved();
        copyWithHelpersKeepOtherSetting();
        System.out.println("EDITOR SETTINGS HOST TESTS PASSED: " + passed + "/6");
    }

    private static void defaultsAreStable() {
        EditorSettings settings = EditorSettings.defaults();
        check(settings.getFontSizeSp() == 13, "Default editor font size must be 13sp");
        check(settings.isWordWrap(), "Word wrap must default to enabled on mobile");
        check(settings.getTabWidth() == 4, "Tab width must default to 4 spaces");
        check(!settings.isAutosaveEnabled(), "Autosave must default to disabled");
        passed++;
    }

    private static void fontSizeClampsToSafeMobileRange() {
        check(new EditorSettings(1, true).getFontSizeSp() == 10, "Font size must clamp to 10sp minimum");
        check(new EditorSettings(99, true).getFontSizeSp() == 24, "Font size must clamp to 24sp maximum");
        check(new EditorSettings(17, true).getFontSizeSp() == 17, "Valid font size must be preserved");
        passed++;
    }

    private static void wordWrapIsPreserved() {
        check(!new EditorSettings(13, false).isWordWrap(), "Disabled word wrap must be preserved");
        check(new EditorSettings(13, true).isWordWrap(), "Enabled word wrap must be preserved");
        passed++;
    }

    private static void tabWidthClampsToPracticalRange() {
        check(new EditorSettings(13, true, 1, false).getTabWidth() == 2, "Tab width must clamp to 2 minimum");
        check(new EditorSettings(13, true, 99, false).getTabWidth() == 8, "Tab width must clamp to 8 maximum");
        check(new EditorSettings(13, true, 6, false).getTabWidth() == 6, "Valid tab width must be preserved");
        passed++;
    }

    private static void autosaveIsPreserved() {
        check(new EditorSettings(13, true, 4, true).isAutosaveEnabled(), "Enabled autosave must be preserved");
        check(!new EditorSettings(13, true, 4, false).isAutosaveEnabled(), "Disabled autosave must be preserved");
        passed++;
    }

    private static void copyWithHelpersKeepOtherSetting() {
        EditorSettings original = new EditorSettings(15, true, 4, false);
        EditorSettings fontChanged = original.withFontSizeSp(20);
        check(fontChanged.getFontSizeSp() == 20, "withFontSizeSp must update font size");
        check(fontChanged.isWordWrap(), "withFontSizeSp must preserve word wrap");
        check(fontChanged.getTabWidth() == 4, "withFontSizeSp must preserve tab width");
        check(!fontChanged.isAutosaveEnabled(), "withFontSizeSp must preserve autosave");
        EditorSettings wrapChanged = original.withWordWrap(false);
        check(wrapChanged.getFontSizeSp() == 15, "withWordWrap must preserve font size");
        check(!wrapChanged.isWordWrap(), "withWordWrap must update word wrap");
        check(wrapChanged.getTabWidth() == 4, "withWordWrap must preserve tab width");
        check(!wrapChanged.isAutosaveEnabled(), "withWordWrap must preserve autosave");
        EditorSettings tabChanged = original.withTabWidth(2);
        check(tabChanged.getFontSizeSp() == 15, "withTabWidth must preserve font size");
        check(tabChanged.isWordWrap(), "withTabWidth must preserve word wrap");
        check(tabChanged.getTabWidth() == 2, "withTabWidth must update tab width");
        check(!tabChanged.isAutosaveEnabled(), "withTabWidth must preserve autosave");
        EditorSettings autosaveChanged = original.withAutosaveEnabled(true);
        check(autosaveChanged.getFontSizeSp() == 15, "withAutosaveEnabled must preserve font size");
        check(autosaveChanged.isWordWrap(), "withAutosaveEnabled must preserve word wrap");
        check(autosaveChanged.getTabWidth() == 4, "withAutosaveEnabled must preserve tab width");
        check(autosaveChanged.isAutosaveEnabled(), "withAutosaveEnabled must update autosave");
        passed++;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
