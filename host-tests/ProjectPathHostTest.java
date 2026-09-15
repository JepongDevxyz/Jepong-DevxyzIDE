import com.jepongdevxyz.idebuild.core.ProjectPath;

public final class ProjectPathHostTest {
    public static void main(String[] args) {
        normalizesProjectRelativePaths();
        rejectsParentTraversal();
        rejectsAbsolutePaths();
        validatesChildNames();
        System.out.println("PROJECT PATH HOST TESTS PASSED: 4/4");
    }

    private static void normalizesProjectRelativePaths() {
        ProjectPath path = ProjectPath.of("private", "./app//src/./main");
        require("private".equals(path.getBackendId()), "backend id must be preserved");
        require("app/src/main".equals(path.getRelativePath()), "relative path must normalize separators and dot segments");
    }

    private static void rejectsParentTraversal() {
        boolean rejected = false;
        try {
            ProjectPath.of("private", "app/../../escape");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "parent traversal must be rejected");
    }

    private static void rejectsAbsolutePaths() {
        boolean rejected = false;
        try {
            ProjectPath.of("private", "/data/local/tmp");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "absolute paths must be rejected");
    }

    private static void validatesChildNames() {
        ProjectPath root = ProjectPath.of("private", "app/src");
        require("app/src/main".equals(root.child("main").getRelativePath()), "valid child must append to parent");

        boolean rejected = false;
        try {
            root.child("../escape");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "child names containing traversal/separators must be rejected");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
