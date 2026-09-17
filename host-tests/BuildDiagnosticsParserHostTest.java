import com.jepongdevxyz.idebuild.core.ProjectPath;
import com.jepongdevxyz.idebuild.core.build.BuildDiagnosticsParser;
import com.jepongdevxyz.idebuild.core.build.BuildProblem;

import java.io.File;
import java.nio.file.Files;

public final class BuildDiagnosticsParserHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        parsesJavaCompilerError();
        parsesAaptErrorWithColumn();
        parsesKotlinDiagnostics();
        ignoresNoiseAndExternalPaths();
        System.out.println("BUILD DIAGNOSTICS PARSER HOST TESTS PASSED: " + passed + "/4");
    }

    private static void parsesJavaCompilerError() throws Exception {
        File root = Files.createTempDirectory("devxyz-problems-java").toFile();
        try {
            File source = new File(root, "app/src/main/java/demo/Main.java");
            String line = source.getAbsolutePath() + ":12: error: cannot find symbol";
            BuildProblem problem = BuildDiagnosticsParser.parseLine(line, root, "local-project");
            assertNotNull(problem);
            assertEquals(BuildProblem.Severity.ERROR, problem.getSeverity());
            assertEquals("app/src/main/java/demo/Main.java", problem.getPath().getRelativePath());
            assertEquals(12, problem.getLine());
            assertEquals(1, problem.getColumn());
            assertEquals("cannot find symbol", problem.getMessage());
            passed++;
        } finally { root.delete(); }
    }

    private static void parsesAaptErrorWithColumn() throws Exception {
        File root = Files.createTempDirectory("devxyz-problems-aapt").toFile();
        try {
            File source = new File(root, "app/src/main/res/layout/activity_main.xml");
            String line = source.getAbsolutePath() + ":7:5-18: Error: resource color/missing not found.";
            BuildProblem problem = BuildDiagnosticsParser.parseLine(line, root, "local-project");
            assertNotNull(problem);
            assertEquals(BuildProblem.Severity.ERROR, problem.getSeverity());
            assertEquals(7, problem.getLine());
            assertEquals(5, problem.getColumn());
            assertContains(problem.getMessage(), "resource color/missing");
            passed++;
        } finally { root.delete(); }
    }

    private static void parsesKotlinDiagnostics() throws Exception {
        File root = Files.createTempDirectory("devxyz-problems-kotlin").toFile();
        try {
            File source = new File(root, "app/src/main/java/demo/Main.kt");
            BuildProblem error = BuildDiagnosticsParser.parseLine(
                    "e: " + source.getAbsolutePath() + ": (9, 3): Unresolved reference: nope", root, "local-project");
            BuildProblem warning = BuildDiagnosticsParser.parseLine(
                    "w: " + source.getAbsolutePath() + ": (4, 2): Variable is never used", root, "local-project");
            assertNotNull(error);
            assertNotNull(warning);
            assertEquals(BuildProblem.Severity.ERROR, error.getSeverity());
            assertEquals(BuildProblem.Severity.WARNING, warning.getSeverity());
            assertEquals(9, error.getLine());
            assertEquals(3, error.getColumn());
            passed++;
        } finally { root.delete(); }
    }

    private static void ignoresNoiseAndExternalPaths() throws Exception {
        File root = Files.createTempDirectory("devxyz-problems-noise").toFile();
        try {
            assertNull(BuildDiagnosticsParser.parseLine("BUILD FAILED in 3s", root, "local-project"));
            assertNull(BuildDiagnosticsParser.parseLine("/outside/Main.java:2: error: nope", root, "local-project"));
            passed++;
        } finally { root.delete(); }
    }

    private static void assertNotNull(Object value) { if (value == null) throw new AssertionError("Expected non-null"); }
    private static void assertNull(Object value) { if (value != null) throw new AssertionError("Expected null"); }
    private static void assertEquals(int expected, int actual) { if (expected != actual) throw new AssertionError("Expected " + expected + " but was " + actual); }
    private static void assertEquals(String expected, String actual) { if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual); }
    private static void assertEquals(BuildProblem.Severity expected, BuildProblem.Severity actual) { if (expected != actual) throw new AssertionError("Expected " + expected + " but was " + actual); }
    private static void assertContains(String actual, String expected) { if (actual.indexOf(expected) < 0) throw new AssertionError("Missing: " + expected); }
}
