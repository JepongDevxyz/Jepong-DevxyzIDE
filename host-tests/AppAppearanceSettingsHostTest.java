import com.jepongdevxyz.idebuild.core.settings.AppAppearanceSettings;

public final class AppAppearanceSettingsHostTest {
    private static int passed;

    public static void main(String[] args) {
        defaultsToDark();
        normalizesKnownModes();
        rejectsUnknownModes();
        System.out.println("APP APPEARANCE SETTINGS HOST TESTS PASSED: " + passed + "/3");
    }

    private static void defaultsToDark() {
        AppAppearanceSettings settings = AppAppearanceSettings.defaults();
        assertEquals(AppAppearanceSettings.MODE_DARK, settings.getMode());
        passed++;
    }

    private static void normalizesKnownModes() {
        assertEquals(AppAppearanceSettings.MODE_DARK, AppAppearanceSettings.of("dark").getMode());
        assertEquals(AppAppearanceSettings.MODE_LIGHT, AppAppearanceSettings.of("LIGHT").getMode());
        assertEquals(AppAppearanceSettings.MODE_SYSTEM, AppAppearanceSettings.of(" system ").getMode());
        passed++;
    }

    private static void rejectsUnknownModes() {
        boolean failed = false;
        try { AppAppearanceSettings.of("neon"); }
        catch (IllegalArgumentException expected) { failed = true; }
        if (!failed) throw new AssertionError("Expected invalid appearance mode to fail");
        passed++;
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }
}
