package com.jepongdevxyz.idebuild;

import java.io.File;
import java.io.FilenameFilter;

public final class ApkLocator {
    private ApkLocator() {}

    public static File findDebugApk(File projectRoot) {
        File output = new File(projectRoot, "app/build/outputs/apk/debug");
        File[] files = output.listFiles(new FilenameFilter() {
            @Override public boolean accept(File dir, String name) { return name != null && name.endsWith(".apk"); }
        });
        if (files == null || files.length == 0) return null;
        File newest = files[0];
        for (File file : files) if (file.lastModified() > newest.lastModified()) newest = file;
        return newest;
    }
}
