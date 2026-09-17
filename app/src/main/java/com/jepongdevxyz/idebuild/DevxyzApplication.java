package com.jepongdevxyz.idebuild;

import android.app.Application;
import android.content.res.Configuration;

import com.jepongdevxyz.idebuild.core.settings.AppAppearanceSettings;

/** Applies DevxyzIDE's app-local appearance preference before activities render. */
public final class DevxyzApplication extends Application {
    private int systemNightBits = Configuration.UI_MODE_NIGHT_UNDEFINED;

    @Override public void onCreate() {
        super.onCreate();
        systemNightBits = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        applyAppearance();
    }

    public void applyAppearance() {
        AppAppearanceSettings settings = AppearanceContext.load(this);
        int desiredNightBits;
        if (settings.isDark()) {
            desiredNightBits = Configuration.UI_MODE_NIGHT_YES;
        } else if (settings.isLight()) {
            desiredNightBits = Configuration.UI_MODE_NIGHT_NO;
        } else {
            desiredNightBits = systemNightBits;
            if (desiredNightBits != Configuration.UI_MODE_NIGHT_YES
                    && desiredNightBits != Configuration.UI_MODE_NIGHT_NO) return;
        }

        Configuration current = getResources().getConfiguration();
        if ((current.uiMode & Configuration.UI_MODE_NIGHT_MASK) == desiredNightBits) return;
        Configuration updated = new Configuration(current);
        updated.uiMode = (updated.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | desiredNightBits;
        getResources().updateConfiguration(updated, getResources().getDisplayMetrics());
    }
}
