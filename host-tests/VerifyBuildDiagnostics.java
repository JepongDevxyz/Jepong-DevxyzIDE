import com.jepongdevxyz.idebuild.core.build.BuildDiagnosticsParser;
import com.jepongdevxyz.idebuild.core.build.BuildProblem;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * CI helper that proves a real compiler log is understood by the same
 * BuildDiagnosticsParser used by DevxyzIDE's Problems surface.
 */
public final class VerifyBuildDiagnostics {
    private VerifyBuildDiagnostics() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: VerifyBuildDiagnostics <project-root> <build-log> <expected-relative-path>");
            System.exit(2);
        }

        File projectRoot = new File(args[0]).getCanonicalFile();
        File logFile = new File(args[1]).getCanonicalFile();
        String expectedPath = args[2].replace('\\', '/');
        if (!projectRoot.isDirectory()) throw new IllegalArgumentException("Project root is not a directory: " + projectRoot);
        if (!logFile.isFile()) throw new IllegalArgumentException("Build log is not a file: " + logFile);

        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                BuildProblem problem = BuildDiagnosticsParser.parseLine(line, projectRoot, "ci-project");
                if (problem == null) continue;
                String relative = problem.getPath().getRelativePath();
                System.out.println("PARSED PROBLEM: " + problem.summary());
                if (problem.getSeverity() == BuildProblem.Severity.ERROR && expectedPath.equals(relative)) {
                    if (problem.getLine() < 1) throw new AssertionError("Parsed problem did not include a valid line");
                    System.out.println("REAL BUILD DIAGNOSTIC VERIFIED: " + relative + ":" + problem.getLine());
                    return;
                }
            }
        } finally {
            reader.close();
        }

        throw new AssertionError("No navigable ERROR diagnostic found for " + expectedPath);
    }
}
