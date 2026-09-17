package com.jepongdevxyz.idebuild;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import com.jepongdevxyz.idebuild.core.settings.AppAppearanceSettings;

/** Applies the app-local dark/light preference without AppCompat dependencies. */
public final class AppearanceContext {
    public static final String PREFS_NAME = "devxyz_app_settings";
    public static final String KEY_MODE = "appearance_mode";

    private AppearanceContext() { }

    public static Context wrap(Context base) {
        if (base == null || Build.VERSION.SDK_INT < 17) return base;
        SharedPreferences preferences = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String stored = preferences.getString(KEY_MODE, AppAppearanceSettings.MODE_DARK);
        AppAppearanceSettings settings;
        try { settings = AppAppearanceSettings.of(stored); }
        catch (IllegalArgumentException ignored) { settings = AppAppearanceSettings.defaults(); }
        if (settings.followsSystem()) return base;

        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        int current = configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK;
        configuration.uiMode = current | (settings.isDark()
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(configuration);
    }

    public static AppAppearanceSettings load(Context context) {
        if (context == null) return AppAppearanceSettings.defaults();
        String stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MODE, AppAppearanceSettings.MODE_DARK);
        try { return AppAppearanceSettings.of(stored); }
        catch (IllegalArgumentException ignored) { return AppAppearanceSettings.defaults(); }
    }

    public static void save(Context context, AppAppearanceSettings settings) {
        if (context == null || settings == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, settings.getMode())
                .apply();
    }
}
