import com.jepongdevxyz.idebuild.core.ProjectPath;
import com.jepongdevxyz.idebuild.core.WorkspacePathResolver;
import com.jepongdevxyz.idebuild.core.search.ProjectSearchService;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class ProjectSearchServiceHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        streamsMatchesWithProjectPaths();
        skipsGeneratedAndBinaryContent();
        honorsCancellation();
        enforcesResultLimit();
        System.out.println("PROJECT SEARCH SERVICE HOST TESTS PASSED: " + passed + "/4");
    }

    private static void streamsMatchesWithProjectPaths() throws Exception {
        File root = Files.createTempDirectory("devxyz-project-search").toFile();
        try {
            write(new File(root, "app/src/Main.java"), "hello world\nsecond hello\n");
            write(new File(root, "app/src/layout.xml"), "<TextView text=\"hello\"/>\n");
            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "local-project");
            final List<ProjectSearchService.SearchMatch> matches = new ArrayList<ProjectSearchService.SearchMatch>();

            ProjectSearchService.SearchSummary summary = ProjectSearchService.search(
                    resolver,
                    ProjectPath.of("local-project", ""),
                    "hello",
                    true,
                    100,
                    100,
                    1024 * 1024,
                    ProjectSearchService.NEVER_CANCELLED,
                    new ProjectSearchService.Listener() {
                        @Override public void onMatch(ProjectSearchService.SearchMatch match) { matches.add(match); }
                    });

            assertEquals(3, matches.size());
            ProjectSearchService.SearchMatch javaFirst = findMatch(matches, "app/src/Main.java", 1);
            ProjectSearchService.SearchMatch javaSecond = findMatch(matches, "app/src/Main.java", 2);
            ProjectSearchService.SearchMatch xml = findMatch(matches, "app/src/layout.xml", 1);
            assertNotNull(javaFirst);
            assertNotNull(javaSecond);
            assertNotNull(xml);
            assertEquals(1, javaFirst.getLineNumber());
            assertEquals(1, javaFirst.getColumnNumber());
            assertEquals(3, summary.getMatches());
            assertFalse(summary.isCancelled());
            passed++;
        } finally { deleteTree(root); }
    }

    private static void skipsGeneratedAndBinaryContent() throws Exception {
        File root = Files.createTempDirectory("devxyz-project-search-skip").toFile();
        try {
            write(new File(root, "src/Keep.java"), "needle\n");
            write(new File(root, "build/Generated.java"), "needle\n");
            write(new File(root, ".gradle/cache.txt"), "needle\n");
            write(new File(root, "src/image.png"), "needle\n");
            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "local-project");
            final List<ProjectSearchService.SearchMatch> matches = new ArrayList<ProjectSearchService.SearchMatch>();
            ProjectSearchService.search(resolver, ProjectPath.of("local-project", ""), "needle", true,
                    100, 100, 1024 * 1024, ProjectSearchService.NEVER_CANCELLED,
                    new ProjectSearchService.Listener() {
                        @Override public void onMatch(ProjectSearchService.SearchMatch match) { matches.add(match); }
                    });
            assertEquals(1, matches.size());
            assertEquals("src/Keep.java", matches.get(0).getPath().getRelativePath());
            passed++;
        } finally { deleteTree(root); }
    }

    private static void honorsCancellation() throws Exception {
        File root = Files.createTempDirectory("devxyz-project-search-cancel").toFile();
        try {
            write(new File(root, "a.java"), "find\nfind\n");
            write(new File(root, "b.java"), "find\n");
            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "local-project");
            final MutableCancellation cancellation = new MutableCancellation();
            final int[] count = new int[]{0};
            ProjectSearchService.SearchSummary summary = ProjectSearchService.search(
                    resolver, ProjectPath.of("local-project", ""), "find", true,
                    100, 100, 1024 * 1024, cancellation,
                    new ProjectSearchService.Listener() {
                        @Override public void onMatch(ProjectSearchService.SearchMatch match) {
                            count[0]++;
                            cancellation.cancelled = true;
                        }
                    });
            assertEquals(1, count[0]);
            assertTrue(summary.isCancelled());
            passed++;
        } finally { deleteTree(root); }
    }

    private static void enforcesResultLimit() throws Exception {
        File root = Files.createTempDirectory("devxyz-project-search-limit").toFile();
        try {
            write(new File(root, "a.java"), "x x x x x\n");
            WorkspacePathResolver resolver = new WorkspacePathResolver(root, "local-project");
            final int[] count = new int[]{0};
            ProjectSearchService.SearchSummary summary = ProjectSearchService.search(
                    resolver, ProjectPath.of("local-project", ""), "x", true,
                    100, 2, 1024 * 1024, ProjectSearchService.NEVER_CANCELLED,
                    new ProjectSearchService.Listener() {
                        @Override public void onMatch(ProjectSearchService.SearchMatch match) { count[0]++; }
                    });
            assertEquals(2, count[0]);
            assertEquals(2, summary.getMatches());
            assertTrue(summary.isTruncated());
            passed++;
        } finally { deleteTree(root); }
    }

    private static ProjectSearchService.SearchMatch findMatch(
            List<ProjectSearchService.SearchMatch> matches,
            String relativePath,
            int line) {
        for (ProjectSearchService.SearchMatch match : matches) {
            if (relativePath.equals(match.getPath().getRelativePath()) && match.getLineNumber() == line) return match;
        }
        return null;
    }

    private static final class MutableCancellation implements ProjectSearchService.Cancellation {
        boolean cancelled;
        @Override public boolean isCancelled() { return cancelled; }
    }

    private static void write(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new Exception("mkdir failed");
        FileOutputStream output = new FileOutputStream(file);
        try { output.write(value.getBytes(StandardCharsets.UTF_8)); }
        finally { output.close(); }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but was " + actual);
    }
    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }
    private static void assertNotNull(Object value) { if (value == null) throw new AssertionError("Expected non-null"); }
    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
}
