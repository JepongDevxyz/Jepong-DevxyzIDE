import com.jepongdevxyz.idebuild.core.ProjectFileService;
import com.jepongdevxyz.idebuild.core.ProjectPath;
import com.jepongdevxyz.idebuild.core.WorkspacePathResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ProjectFileServiceHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        createRenameDuplicateDeleteRoundTrip();
        duplicateChoosesNextAvailableName();
        copyAndMoveAcrossDirectories();
        rejectsMovingDirectoryIntoItself();
        rejectsRootMutationAndInvalidNames();
        System.out.println("PROJECT FILE SERVICE HOST TESTS PASSED: " + passed + "/5");
    }

    private static void createRenameDuplicateDeleteRoundTrip() throws Exception {
        File root = Files.createTempDirectory("devxyz-file-service").toFile();
        try {
            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "local-project");
            ProjectFileService service = new ProjectFileService(resolver, "local-project");
            ProjectPath projectRoot = ProjectPath.of("local-project", "");

            ProjectPath app = service.createDirectory(projectRoot, "app");
            ProjectPath source = service.createFile(app, "notes.txt");
            write(resolver.resolve(source), "hello devxyz");

            ProjectPath duplicate = service.duplicate(source);
            assertEquals("app/notes copy.txt", duplicate.getRelativePath());
            assertEquals("hello devxyz", read(resolver.resolve(duplicate)));

            ProjectPath renamed = service.rename(duplicate, "renamed.txt");
            assertEquals("app/renamed.txt", renamed.getRelativePath());
            assertFalse(resolver.resolve(duplicate).exists());
            assertTrue(resolver.resolve(renamed).isFile());

            ProjectPath nested = service.createDirectory(app, "nested");
            ProjectPath child = service.createFile(nested, "child.txt");
            write(resolver.resolve(child), "child");
            service.delete(nested);
            assertFalse(resolver.resolve(nested).exists());
            passed++;
        } finally {
            deleteTree(root);
        }
    }

    private static void duplicateChoosesNextAvailableName() throws Exception {
        File root = Files.createTempDirectory("devxyz-file-copy").toFile();
        try {
            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "local-project");
            ProjectFileService service = new ProjectFileService(resolver, "local-project");
            ProjectPath projectRoot = ProjectPath.of("local-project", "");
            ProjectPath source = service.createFile(projectRoot, "Main.java");
            write(resolver.resolve(source), "class Main {}");

            ProjectPath first = service.duplicate(source);
            ProjectPath second = service.duplicate(source);
            assertEquals("Main copy.java", first.getRelativePath());
            assertEquals("Main copy 2.java", second.getRelativePath());
            passed++;
        } finally {
            deleteTree(root);
        }
    }

    private static void copyAndMoveAcrossDirectories() throws Exception {
        File root = Files.createTempDirectory("devxyz-file-move").toFile();
        try {
            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "local-project");
            ProjectFileService service = new ProjectFileService(resolver, "local-project");
            ProjectPath projectRoot = ProjectPath.of("local-project", "");
            ProjectPath src = service.createDirectory(projectRoot, "src");
            ProjectPath dst = service.createDirectory(projectRoot, "dst");
            ProjectPath file = service.createFile(src, "data.txt");
            write(resolver.resolve(file), "payload");

            ProjectPath copied = service.copyTo(file, dst);
            assertEquals("dst/data.txt", copied.getRelativePath());
            assertTrue(resolver.resolve(file).isFile());
            assertEquals("payload", read(resolver.resolve(copied)));

            ProjectPath moved = service.moveTo(file, dst, "moved.txt");
            assertEquals("dst/moved.txt", moved.getRelativePath());
            assertFalse(resolver.resolve(file).exists());
            assertEquals("payload", read(resolver.resolve(moved)));
            passed++;
        } finally {
            deleteTree(root);
        }
    }

    private static void rejectsMovingDirectoryIntoItself() throws Exception {
        File root = Files.createTempDirectory("devxyz-file-loop").toFile();
        try {
            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "local-project");
            ProjectFileService service = new ProjectFileService(resolver, "local-project");
            ProjectPath projectRoot = ProjectPath.of("local-project", "");
            ProjectPath parent = service.createDirectory(projectRoot, "parent");
            ProjectPath child = service.createDirectory(parent, "child");
            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception { service.moveTo(parent, child, "parent"); }
            });
            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception { service.copyTo(parent, child); }
            });
            passed++;
        } finally {
            deleteTree(root);
        }
    }

    private static void rejectsRootMutationAndInvalidNames() throws Exception {
        File root = Files.createTempDirectory("devxyz-file-guard").toFile();
        try {
            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "local-project");
            ProjectFileService service = new ProjectFileService(resolver, "local-project");
            ProjectPath projectRoot = ProjectPath.of("local-project", "");

            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception { service.delete(projectRoot); }
            });
            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception { service.rename(projectRoot, "other"); }
            });
            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception { service.createFile(projectRoot, "../escape.txt"); }
            });
            passed++;
        } finally {
            deleteTree(root);
        }
    }

    private static void write(File file, String value) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(value.getBytes(StandardCharsets.UTF_8)); }
        finally { out.close(); }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void expectFailure(ThrowingRunnable action) throws Exception {
        boolean failed = false;
        try { action.run(); }
        catch (IllegalArgumentException expected) { failed = true; }
        catch (IOException expected) { failed = true; }
        if (!failed) throw new AssertionError("Expected operation to fail");
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }

    private interface ThrowingRunnable { void run() throws Exception; }
}
