package com.jepongdevxyz.idebuild.core.build;

import java.util.Locale;

/** Describes which supported Gradle tasks are expected to produce an APK artifact. */
public final class BuildTaskPolicy {
    private BuildTaskPolicy() { }

    public static String expectedApkVariant(String task) {
        if (task == null) return null;
        String value = task.trim().toLowerCase(Locale.US);
        if (value.endsWith("assembledebug")) return "debug";
        if (value.endsWith("assemblerelease")) return "release";
        return null;
    }
}
