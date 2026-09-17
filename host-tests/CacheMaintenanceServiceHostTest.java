import com.jepongdevxyz.idebuild.core.storage.CacheMaintenanceService;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class CacheMaintenanceServiceHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        measuresAndClearsOnlyNamedCache();
        rejectsTraversalAndUnknownCacheNames();
        rejectsSymlinkEscape();
        System.out.println("CACHE MAINTENANCE SERVICE HOST TESTS PASSED: " + passed + "/3");
    }

    private static void measuresAndClearsOnlyNamedCache() throws Exception {
        File root = Files.createTempDirectory("devxyz-cache-root").toFile();
        try {
            File cache = new File(root, "database-viewer");
            write(new File(cache, "a.bin"), "12345");
            write(new File(cache, "nested/b.bin"), "6789");
            write(new File(root, "projects/keep.txt"), "project-source");

            CacheMaintenanceService service = new CacheMaintenanceService(
                    root, new String[]{"database-viewer", "web-preview", "signing-temp"});
            CacheMaintenanceService.CacheStats stats = service.measure("database-viewer", 100);
            assertEquals(9L, stats.getBytes());
            assertEquals(2, stats.getFiles());

            CacheMaintenanceService.CacheStats removed = service.clear("database-viewer", 100);
            assertEquals(9L, removed.getBytes());
            assertFalse(cache.exists());
            assertTrue(new File(root, "projects/keep.txt").isFile());
            passed++;
        } finally {
            deleteTree(root);
        }
    }

    private static void rejectsTraversalAndUnknownCacheNames() throws Exception {
        File root = Files.createTempDirectory("devxyz-cache-guard").toFile();
        try {
            CacheMaintenanceService service = new CacheMaintenanceService(
                    root, new String[]{"database-viewer"});
            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception { service.clear("../projects", 100); }
            });
            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception { service.measure("projects", 100); }
            });
            passed++;
        } finally {
            deleteTree(root);
        }
    }

    private static void rejectsSymlinkEscape() throws Exception {
        File root = Files.createTempDirectory("devxyz-cache-link-root").toFile();
        File outside = Files.createTempDirectory("devxyz-cache-outside").toFile();
        try {
            write(new File(outside, "secret.txt"), "do-not-delete");
            java.nio.file.Path link = new File(root, "database-viewer").toPath();
            try {
                Files.createSymbolicLink(link, outside.toPath());
            } catch (UnsupportedOperationException unsupported) {
                passed++;
                return;
            }

            final CacheMaintenanceService service = new CacheMaintenanceService(
                    root, new String[]{"database-viewer"});
            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception { service.clear("database-viewer", 100); }
            });
            assertTrue(new File(outside, "secret.txt").isFile());
            passed++;
        } finally {
            deleteTree(root);
            deleteTree(outside);
        }
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
        try { action.run(); }
        catch (IllegalArgumentException expected) { failed = true; }
        catch (java.io.IOException expected) { failed = true; }
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
    private static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but was " + actual);
    }
    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private interface ThrowingRunnable { void run() throws Exception; }
}
