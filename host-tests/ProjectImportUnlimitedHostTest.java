import com.jepongdevxyz.idebuild.core.ProjectImportService;
import com.jepongdevxyz.idebuild.core.ProjectRootTracker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Regression tests for project import without an app-defined byte/entry ceiling. */
public final class ProjectImportUnlimitedHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        unlimitedOverloadImportsWithoutPolicyArguments();
        rootTrackerFindsDeepSettingsRootWithConstantState();
        System.out.println("PROJECT IMPORT UNLIMITED HOST TESTS PASSED: " + passed + "/2");
    }

    private static void unlimitedOverloadImportsWithoutPolicyArguments() throws Exception {
        File projects = Files.createTempDirectory("devxyz-unlimited-import").toFile();
        try {
            byte[] zip = zip(new String[][] {
                    {"Huge/settings.gradle", "include ':app'"},
                    {"Huge/app/build.gradle", "apply plugin: 'com.android.application'"},
                    {"Huge/app/src/main/AndroidManifest.xml", "<manifest package=\"devxyz.huge\"/>"}
            });
            ProjectImportService.ImportResult result = ProjectImportService.importProject(
                    new ByteArrayInputStream(zip),
                    projects,
                    "Huge.zip",
                    ProjectImportService.NEVER_CANCELLED,
                    ProjectImportService.NO_PROGRESS,
                    ProjectImportService.NO_STORAGE_PROBE);
            assertTrue(new File(result.getProjectRoot(), "settings.gradle").isFile());
            passed++;
        } finally {
            deleteTree(projects);
        }
    }

    private static void rootTrackerFindsDeepSettingsRootWithConstantState() {
        ProjectRootTracker tracker = new ProjectRootTracker();
        tracker.onEntry("outer/one/two/three/app/build.gradle");
        tracker.onEntry("outer/one/two/three/settings.gradle.kts");
        tracker.onEntry("outer/one/two/three/app/src/main/AndroidManifest.xml");
        assertEquals("outer/one/two/three", tracker.getBestRootRelativePath());
        passed++;
    }

    private static byte[] zip(String[][] entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes);
        try {
            for (String[] entry : entries) {
                zip.putNextEntry(new ZipEntry(entry[0]));
                zip.write(entry[1].getBytes("UTF-8"));
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        return bytes.toByteArray();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }
}
