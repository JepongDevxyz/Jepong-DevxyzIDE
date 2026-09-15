import com.jepongdevxyz.idebuild.core.ProjectDirectoryListing;
import com.jepongdevxyz.idebuild.core.ProjectDirectoryService;
import com.jepongdevxyz.idebuild.core.ProjectEntry;
import com.jepongdevxyz.idebuild.core.ProjectPath;
import com.jepongdevxyz.idebuild.core.WorkspacePathResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ProjectDirectoryServiceHostTest {
    public static void main(String[] args) throws Exception {
        listsOnlyImmediateVisibleChildrenWithDirectoriesFirst();
        reportsTruncatedListings();
        omitsSymlinkEntriesThatEscapeTheProject();
        System.out.println("PROJECT DIRECTORY SERVICE HOST TESTS PASSED: 3/3");
    }

    private static void listsOnlyImmediateVisibleChildrenWithDirectoriesFirst() throws Exception {
        File root = createTempDirectory("devxyz-tree");
        try {
            require(new File(root, "app/src/main").mkdirs(), "app source tree must be created");
            require(new File(root, "build/generated").mkdirs(), "build tree must be created");
            require(new File(root, ".gradle/caches").mkdirs(), "gradle cache tree must be created");
            touch(new File(root, "settings.gradle"));
            touch(new File(root, "app/src/main/Main.java"));

            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "local-project");
            ProjectDirectoryService service = new ProjectDirectoryService(resolver);
            ProjectDirectoryListing listing = service.listChildren(ProjectPath.of("local-project", ""), 20);
            List<ProjectEntry> entries = listing.getEntries();

            require(entries.size() == 2, "only app and settings.gradle should be visible at root");
            require(entries.get(0).isDirectory(), "directories must sort before files");
            require("app".equals(entries.get(0).getName()), "app directory must be first");
            require("settings.gradle".equals(entries.get(1).getName()), "root file must follow directories");
            require("app".equals(entries.get(0).getPath().getRelativePath()), "entry path must stay project-relative");
            require(!listing.isTruncated(), "small listing must not be marked truncated");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void reportsTruncatedListings() throws Exception {
        File root = createTempDirectory("devxyz-tree");
        try {
            touch(new File(root, "a.txt"));
            touch(new File(root, "b.txt"));
            touch(new File(root, "c.txt"));

            ProjectDirectoryService service = new ProjectDirectoryService(new WorkspacePathResolver(root, "local-project"));
            ProjectDirectoryListing listing = service.listChildren(ProjectPath.of("local-project", ""), 2);
            require(listing.getEntries().size() == 2, "listing must honor the caller limit");
            require(listing.isTruncated(), "listing must report when more visible children exist");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void omitsSymlinkEntriesThatEscapeTheProject() throws Exception {
        File root = createTempDirectory("devxyz-tree");
        File outside = createTempDirectory("devxyz-outside");
        try {
            touch(new File(outside, "secret.txt"));
            Path link = new File(root, "outside-link").toPath();
            try {
                Files.createSymbolicLink(link, outside.toPath());
            } catch (UnsupportedOperationException unavailable) {
                return;
            } catch (IOException unavailable) {
                return;
            } catch (SecurityException unavailable) {
                return;
            }

            ProjectDirectoryService service = new ProjectDirectoryService(new WorkspacePathResolver(root, "local-project"));
            ProjectDirectoryListing listing = service.listChildren(ProjectPath.of("local-project", ""), 20);
            require(listing.getEntries().isEmpty(), "escaping symlink must not appear in project explorer");
        } finally {
            deleteRecursively(root);
            deleteRecursively(outside);
        }
    }

    private static void touch(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create parent");
        FileOutputStream output = new FileOutputStream(file);
        output.close();
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
