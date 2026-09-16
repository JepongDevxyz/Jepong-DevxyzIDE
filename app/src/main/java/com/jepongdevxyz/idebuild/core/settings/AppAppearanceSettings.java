package com.jepongdevxyz.idebuild.core.settings;

import java.util.Locale;

/** Validated persistent appearance preference for DevxyzIDE. */
public final class AppAppearanceSettings {
    public static final String MODE_DARK = "dark";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_SYSTEM = "system";

    private final String mode;

    private AppAppearanceSettings(String mode) {
        this.mode = mode;
    }

    public static AppAppearanceSettings defaults() {
        return new AppAppearanceSettings(MODE_DARK);
    }

    public static AppAppearanceSettings of(String rawMode) {
        if (rawMode == null) throw new IllegalArgumentException("Appearance mode must not be null");
        String mode = rawMode.trim().toLowerCase(Locale.US);
        if (!MODE_DARK.equals(mode) && !MODE_LIGHT.equals(mode) && !MODE_SYSTEM.equals(mode)) {
            throw new IllegalArgumentException("Unsupported appearance mode: " + rawMode);
        }
        return new AppAppearanceSettings(mode);
    }

    public String getMode() { return mode; }

    public boolean isDark() { return MODE_DARK.equals(mode); }
    public boolean isLight() { return MODE_LIGHT.equals(mode); }
    public boolean followsSystem() { return MODE_SYSTEM.equals(mode); }
}
