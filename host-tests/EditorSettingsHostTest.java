import com.jepongdevxyz.idebuild.core.settings.EditorSettings;

public final class EditorSettingsHostTest {
    private static int passed;

    public static void main(String[] args) {
        defaultsAreStable();
        fontSizeClampsToSafeMobileRange();
        wordWrapIsPreserved();
        copyWithHelpersKeepOtherSetting();
        System.out.println("EDITOR SETTINGS HOST TESTS PASSED: " + passed + "/4");
    }

    private static void defaultsAreStable() {
        EditorSettings settings = EditorSettings.defaults();
        check(settings.getFontSizeSp() == 13, "Default editor font size must be 13sp");
        check(settings.isWordWrap(), "Word wrap must default to enabled on mobile");
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

    private static void copyWithHelpersKeepOtherSetting() {
        EditorSettings original = new EditorSettings(15, true);
        EditorSettings fontChanged = original.withFontSizeSp(20);
        check(fontChanged.getFontSizeSp() == 20, "withFontSizeSp must update font size");
        check(fontChanged.isWordWrap(), "withFontSizeSp must preserve word wrap");
        EditorSettings wrapChanged = original.withWordWrap(false);
        check(wrapChanged.getFontSizeSp() == 15, "withWordWrap must preserve font size");
        check(!wrapChanged.isWordWrap(), "withWordWrap must update word wrap");
        passed++;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
