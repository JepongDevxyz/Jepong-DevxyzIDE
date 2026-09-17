import com.jepongdevxyz.idebuild.ApkLocator;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ApkLocatorHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        findsNewestStructurallyValidDebugApk();
        findsReleaseApkByVariant();
        rejectsPlainFileWithApkExtension();
        rejectsZipWithoutAndroidManifest();
        System.out.println("APK LOCATOR HOST TESTS PASSED: " + passed + "/4");
    }

    private static void findsNewestStructurallyValidDebugApk() throws Exception {
        File root = Files.createTempDirectory("devxyz-apk-locator").toFile();
        try {
            File output = new File(root, "app/build/outputs/apk/debug");
            output.mkdirs();
            File valid = new File(output, "app-debug.apk");
            writeApk(valid, true);
            valid.setLastModified(System.currentTimeMillis() - 5000L);
            File invalidNewer = new File(output, "newer.apk");
            writeText(invalidNewer, "not a zip");
            invalidNewer.setLastModified(System.currentTimeMillis());

            File found = ApkLocator.findDebugApk(root);
            assertEquals(valid.getCanonicalFile(), found == null ? null : found.getCanonicalFile());
            assertTrue(ApkLocator.isStructurallyValidApk(valid));
            passed++;
        } finally { deleteTree(root); }
    }

    private static void findsReleaseApkByVariant() throws Exception {
        File root = Files.createTempDirectory("devxyz-release-locator").toFile();
        try {
            File output = new File(root, "app/build/outputs/apk/release");
            output.mkdirs();
            File release = new File(output, "app-release-unsigned.apk");
            writeApk(release, true);
            File found = ApkLocator.findApk(root, "release");
            assertEquals(release.getCanonicalFile(), found == null ? null : found.getCanonicalFile());
            passed++;
        } finally { deleteTree(root); }
    }

    private static void rejectsPlainFileWithApkExtension() throws Exception {
        File file = File.createTempFile("devxyz-invalid", ".apk");
        try {
            writeText(file, "not an apk");
            assertFalse(ApkLocator.isStructurallyValidApk(file));
            passed++;
        } finally { file.delete(); }
    }

    private static void rejectsZipWithoutAndroidManifest() throws Exception {
        File file = File.createTempFile("devxyz-no-manifest", ".apk");
        try {
            writeApk(file, false);
            assertFalse(ApkLocator.isStructurallyValidApk(file));
            passed++;
        } finally { file.delete(); }
    }

    private static void writeApk(File file, boolean manifest) throws Exception {
        File parent = file.getParentFile(); if (parent != null) parent.mkdirs();
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file));
        try {
            if (manifest) {
                zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
                zip.write(new byte[]{0x03, 0x00, 0x08, 0x00});
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write("dex\n035\u0000".getBytes(StandardCharsets.ISO_8859_1));
            zip.closeEntry();
        } finally { zip.close(); }
    }

    private static void writeText(File file, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(text.getBytes(StandardCharsets.UTF_8)); } finally { out.close(); }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
    private static void assertEquals(File expected, File actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }
}
