import com.jepongdevxyz.idebuild.core.ProjectPath;
import com.jepongdevxyz.idebuild.core.WorkspacePathResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkspacePathResolverHostTest {
    public static void main(String[] args) throws Exception {
        resolvesInsideProjectRoot();
        rejectsWrongBackend();
        rejectsCanonicalEscapeThroughSymlinkWhenSupported();
        System.out.println("WORKSPACE PATH RESOLVER HOST TESTS PASSED: 3/3");
    }

    private static void resolvesInsideProjectRoot() throws Exception {
        File root = createTempDirectory("devxyz-root");
        try {
            File source = new File(root, "app/src/Main.java");
            require(source.getParentFile().mkdirs(), "test source parent must be created");
            require(source.createNewFile(), "test source must be created");

            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "private");
            File resolved = resolver.resolve(ProjectPath.of("private", "app/src/Main.java"));
            require(source.getCanonicalFile().equals(resolved), "resolver must return the canonical in-root file");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void rejectsWrongBackend() throws Exception {
        File root = createTempDirectory("devxyz-root");
        try {
            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "private");
            boolean rejected = false;
            try {
                resolver.resolve(ProjectPath.of("saf", "app/src/Main.java"));
            } catch (IOException expected) {
                rejected = true;
            }
            require(rejected, "paths from another backend must be rejected");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void rejectsCanonicalEscapeThroughSymlinkWhenSupported() throws Exception {
        File root = createTempDirectory("devxyz-root");
        File outside = createTempDirectory("devxyz-outside");
        try {
            File secret = new File(outside, "secret.txt");
            FileOutputStream stream = new FileOutputStream(secret);
            try {
                stream.write('x');
            } finally {
                stream.close();
            }

            Path link = new File(root, "linked-outside").toPath();
            try {
                Files.createSymbolicLink(link, outside.toPath());
            } catch (UnsupportedOperationException unsupported) {
                return;
            } catch (IOException unavailable) {
                return;
            } catch (SecurityException unavailable) {
                return;
            }

            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "private");
            boolean rejected = false;
            try {
                resolver.resolve(ProjectPath.of("private", "linked-outside/secret.txt"));
            } catch (IOException expected) {
                rejected = true;
            }
            require(rejected, "canonical paths escaping through symlinks must be rejected");
        } finally {
            deleteRecursively(root);
            deleteRecursively(outside);
        }
    }

    private static File createTempDirectory(String prefix) throws IOException {
        File file = File.createTempFile(prefix, ".tmp");
        if (!file.delete() || !file.mkdirs()) throw new IOException("Cannot create temporary directory");
        return file;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
