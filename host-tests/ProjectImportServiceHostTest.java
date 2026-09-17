import com.jepongdevxyz.idebuild.core.ProjectImportService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ProjectImportServiceHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        successfulImportFinalizesWithoutStagingResidue();
        cancellationCleansPartialImport();
        maliciousZipFailureCleansPartialImport();
        System.out.println("PROJECT IMPORT SERVICE HOST TESTS PASSED: " + passed + "/3");
    }

    private static void successfulImportFinalizesWithoutStagingResidue() throws Exception {
        File projects = Files.createTempDirectory("devxyz-import-success").toFile();
        try {
            byte[] zip = zip(new String[][] {
                    {"Demo/settings.gradle", "include ':app'"},
                    {"Demo/app/build.gradle", "apply plugin: 'com.android.application'"},
                    {"Demo/app/src/main/AndroidManifest.xml", "<manifest package=\"demo.app\"/>"}
            });
            ProjectImportService.ImportResult result = ProjectImportService.importProject(
                    new ByteArrayInputStream(zip), projects, "Demo.zip", 100, 1024 * 1024,
                    ProjectImportService.NEVER_CANCELLED, ProjectImportService.NO_PROGRESS);

            assertTrue(result.getImportedDirectory().isDirectory());
            assertTrue(result.getProjectRoot().isDirectory());
            assertTrue(new File(result.getProjectRoot(), "settings.gradle").isFile());
            assertFalse(hasStagingDirectory(projects));
            passed++;
        } finally {
            deleteTree(projects);
        }
    }

    private static void cancellationCleansPartialImport() throws Exception {
        File projects = Files.createTempDirectory("devxyz-import-cancel").toFile();
        try {
            StringBuilder large = new StringBuilder();
            for (int i = 0; i < 200000; i++) large.append('x');
            byte[] zip = zip(new String[][] {
                    {"CancelDemo/settings.gradle", "include ':app'"},
                    {"CancelDemo/app/src/main/assets/large.txt", large.toString()},
                    {"CancelDemo/app/src/main/AndroidManifest.xml", "<manifest package=\"demo.cancel\"/>"}
            });
            final MutableCancellation cancellation = new MutableCancellation();
            boolean canceled = false;
            try {
                ProjectImportService.importProject(
                        new ByteArrayInputStream(zip), projects, "CancelDemo.zip", 100, 1024 * 1024,
                        cancellation,
                        new ProjectImportService.ProgressListener() {
                            @Override public void onProgress(int entries, long bytes) {
                                if (bytes > 0) cancellation.cancelled = true;
                            }
                        });
            } catch (ProjectImportService.ImportCanceledException expected) {
                canceled = true;
            }
            assertTrue(canceled);
            assertFalse(hasStagingDirectory(projects));
            assertFalse(new File(projects, "CancelDemo").exists());
            passed++;
        } finally {
            deleteTree(projects);
        }
    }

    private static void maliciousZipFailureCleansPartialImport() throws Exception {
        File projects = Files.createTempDirectory("devxyz-import-malicious").toFile();
        File escaped = new File(projects.getParentFile(), "devxyz-escape.txt");
        if (escaped.exists()) escaped.delete();
        try {
            byte[] zip = zip(new String[][] {
                    {"Safe/settings.gradle", "include ':app'"},
                    {"../devxyz-escape.txt", "escape"}
            });
            boolean failed = false;
            try {
                ProjectImportService.importProject(
                        new ByteArrayInputStream(zip), projects, "Unsafe.zip", 100, 1024 * 1024,
                        ProjectImportService.NEVER_CANCELLED, ProjectImportService.NO_PROGRESS);
            } catch (java.io.IOException expected) {
                failed = true;
            }
            assertTrue(failed);
            assertFalse(hasStagingDirectory(projects));
            assertFalse(new File(projects, "Unsafe").exists());
            assertFalse(escaped.exists());
            passed++;
        } finally {
            if (escaped.exists()) escaped.delete();
            deleteTree(projects);
        }
    }

    private static byte[] zip(String[][] entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes);
        try {
            for (String[] entry : entries) {
                zip.putNextEntry(new ZipEntry(entry[0]));
                zip.write(entry[1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        return bytes.toByteArray();
    }

    private static boolean hasStagingDirectory(File projects) {
        File[] children = projects.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.getName().startsWith(".import-")) return true;
        }
        return false;
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

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }

    private static final class MutableCancellation implements ProjectImportService.CancellationSignal {
        volatile boolean cancelled;
        @Override public boolean isCancelled() { return cancelled; }
    }
}
