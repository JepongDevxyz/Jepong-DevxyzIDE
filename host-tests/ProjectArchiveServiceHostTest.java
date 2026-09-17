import com.jepongdevxyz.idebuild.core.ProjectArchiveService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ProjectArchiveServiceHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        exportsSourcesAndSkipsGeneratedCaches();
        enforcesArchiveLimits();
        rejectsInvalidRoot();
        System.out.println("PROJECT ARCHIVE SERVICE HOST TESTS PASSED: " + passed + "/3");
    }

    private static void exportsSourcesAndSkipsGeneratedCaches() throws Exception {
        File root = Files.createTempDirectory("devxyz-archive").toFile();
        try {
            write(new File(root, "settings.gradle"), "include ':app'");
            write(new File(root, "app/src/main/java/demo/Main.java"), "class Main {}");
            write(new File(root, "app/build/generated.txt"), "skip");
            write(new File(root, ".gradle/cache.bin"), "skip");
            write(new File(root, ".git/config"), "skip");
            write(new File(root, ".idea/workspace.xml"), "skip");

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ProjectArchiveService.ArchiveResult result = ProjectArchiveService.writeSourceArchive(root, bytes, 100, 1024 * 1024);
            Set<String> names = zipNames(bytes.toByteArray());

            assertTrue(names.contains("settings.gradle"));
            assertTrue(names.contains("app/src/main/java/demo/Main.java"));
            assertFalse(names.contains("app/build/generated.txt"));
            assertFalse(names.contains(".gradle/cache.bin"));
            assertFalse(names.contains(".git/config"));
            assertFalse(names.contains(".idea/workspace.xml"));
            assertEquals(2, result.getFileEntries());
            assertTrue(result.getUncompressedBytes() > 0);
            passed++;
        } finally {
            deleteTree(root);
        }
    }

    private static void enforcesArchiveLimits() throws Exception {
        File root = Files.createTempDirectory("devxyz-archive-limit").toFile();
        try {
            write(new File(root, "one.txt"), "12345");
            write(new File(root, "two.txt"), "67890");

            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception {
                    ProjectArchiveService.writeSourceArchive(root, new ByteArrayOutputStream(), 1, 1024);
                }
            });
            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception {
                    ProjectArchiveService.writeSourceArchive(root, new ByteArrayOutputStream(), 10, 7);
                }
            });
            passed++;
        } finally {
            deleteTree(root);
        }
    }

    private static void rejectsInvalidRoot() throws Exception {
        File file = File.createTempFile("devxyz-archive-file", ".tmp");
        try {
            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception {
                    ProjectArchiveService.writeSourceArchive(file, new ByteArrayOutputStream(), 10, 1024);
                }
            });
            passed++;
        } finally {
            file.delete();
        }
    }

    private static Set<String> zipNames(byte[] bytes) throws Exception {
        Set<String> names = new HashSet<String>();
        ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) names.add(entry.getName());
        } finally {
            input.close();
        }
        return names;
    }

    private static void write(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new Exception("mkdir failed");
        FileOutputStream output = new FileOutputStream(file);
        try { output.write(value.getBytes(StandardCharsets.UTF_8)); }
        finally { output.close(); }
    }

    private static void expectFailure(ThrowingRunnable action) throws Exception {
        boolean failed = false;
        try { action.run(); } catch (IllegalArgumentException expected) { failed = true; } catch (java.io.IOException expected) { failed = true; }
        if (!failed) throw new AssertionError("Expected operation to fail");
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private interface ThrowingRunnable { void run() throws Exception; }
}
