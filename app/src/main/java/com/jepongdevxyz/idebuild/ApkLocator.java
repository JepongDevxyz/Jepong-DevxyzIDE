package com.jepongdevxyz.idebuild;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ApkLocator {
    private ApkLocator() {}

    public static File findDebugApk(File projectRoot) {
        if (projectRoot == null) return null;
        File output = new File(projectRoot, "app/build/outputs/apk/debug");
        File[] files = output.listFiles(new FilenameFilter() {
            @Override public boolean accept(File dir, String name) {
                return name != null && name.toLowerCase(java.util.Locale.US).endsWith(".apk");
            }
        });
        if (files == null || files.length == 0) return null;

        File newestValid = null;
        for (File file : files) {
            if (!isStructurallyValidApk(file)) continue;
            if (newestValid == null || file.lastModified() > newestValid.lastModified()) newestValid = file;
        }
        return newestValid;
    }

    /**
     * Lightweight structural validation used before DevxyzIDE reports an APK
     * output as build success. Signature verification is handled separately.
     */
    public static boolean isStructurallyValidApk(File file) {
        if (file == null || !file.isFile() || file.length() <= 0) return false;
        ZipFile zip = null;
        try {
            zip = new ZipFile(file);
            ZipEntry manifest = zip.getEntry("AndroidManifest.xml");
            if (manifest == null || manifest.isDirectory()) return false;
            return zip.size() >= 1;
        } catch (IOException invalidArchive) {
            return false;
        } finally {
            if (zip != null) try { zip.close(); } catch (IOException ignored) { }
        }
    }
}
