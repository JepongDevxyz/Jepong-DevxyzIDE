package com.jepongdevxyz.idebuild;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.IOException;

/** Persists explicit user trust for project roots before Gradle scripts are executed. */
public final class ProjectTrustStore {
    private static final String PREFS = "devxyz_project_trust";
    private static final String PREFIX = "trusted:";

    private ProjectTrustStore() { }

    public static boolean isTrusted(Context context, File projectRoot) {
        if (context == null || projectRoot == null) return false;
        try {
            return preferences(context).getBoolean(key(projectRoot), false);
        } catch (IOException ignored) {
            return false;
        }
    }

    public static void setTrusted(Context context, File projectRoot, boolean trusted) throws IOException {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (projectRoot == null) throw new IllegalArgumentException("projectRoot must not be null");
        SharedPreferences.Editor editor = preferences(context).edit();
        String key = key(projectRoot);
        if (trusted) editor.putBoolean(key, true);
        else editor.remove(key);
        if (!editor.commit()) throw new IOException("Could not persist project trust state");
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(File projectRoot) throws IOException {
        return PREFIX + projectRoot.getCanonicalPath();
    }
}
