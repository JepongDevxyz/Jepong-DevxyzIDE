import com.jepongdevxyz.idebuild.core.process.ProcessRequest;
import com.jepongdevxyz.idebuild.core.terminal.TerminalCommandPlanner;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

public final class TerminalCommandPlannerHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        testRejectsBlankCommand();
        testFallsBackToAndroidSystemShell();
        testPrefersInstalledRuntimeShellAndEnvironment();
        testUsesProjectAsWorkingDirectory();
        System.out.println("TERMINAL COMMAND PLANNER HOST TESTS PASSED: " + passed + "/4");
    }

    private static void testRejectsBlankCommand() throws Exception {
        File root = tempDirectory("terminal-blank");
        try {
            boolean rejected = false;
            try { TerminalCommandPlanner.plan(root, null, "   "); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Blank terminal commands must be rejected");
        } finally { delete(root); }
    }

    private static void testFallsBackToAndroidSystemShell() throws Exception {
        File root = tempDirectory("terminal-system-shell");
        try {
            ProcessRequest request = TerminalCommandPlanner.plan(root, null, "pwd");
            check("/system/bin/sh".equals(request.getCommand().get(0)), "Missing runtime must use Android system shell");
            check("-c".equals(request.getCommand().get(1)), "Terminal command must use shell -c execution");
            check("pwd".equals(request.getCommand().get(2)), "Terminal command text must be preserved as one shell argument");
        } finally { delete(root); }
    }

    private static void testPrefersInstalledRuntimeShellAndEnvironment() throws Exception {
        File root = tempDirectory("terminal-runtime-shell");
        try {
            File shell = new File(root, "usr/bin/bash");
            if (!shell.getParentFile().mkdirs()) throw new AssertionError("Cannot create runtime bin directory");
            FileOutputStream output = new FileOutputStream(shell);
            output.write("#!/system/bin/sh\n".getBytes("UTF-8"));
            output.close();
            shell.setExecutable(true);
            File home = new File(root, "home");
            if (!home.mkdirs()) throw new AssertionError("Cannot create runtime home");

            ProcessRequest request = TerminalCommandPlanner.plan(root, null, "echo ready");
            check(shell.getCanonicalPath().equals(new File(request.getCommand().get(0)).getCanonicalPath()), "Installed runtime bash must be preferred");
            Map<String, String> environment = request.getEnvironment();
            check(new File(root, "usr").getCanonicalPath().equals(new File(environment.get("PREFIX")).getCanonicalPath()), "PREFIX must point at app-private runtime");
            check(home.getCanonicalPath().equals(new File(environment.get("HOME")).getCanonicalPath()), "HOME must point at app-private runtime home");
            check(environment.get("PATH").startsWith(new File(root, "usr/bin").getAbsolutePath()), "Runtime bin must lead PATH");
        } finally { delete(root); }
    }

    private static void testUsesProjectAsWorkingDirectory() throws Exception {
        File root = tempDirectory("terminal-working-dir");
        try {
            File project = new File(root, "projects/MyApp");
            if (!project.mkdirs()) throw new AssertionError("Cannot create project directory");
            ProcessRequest request = TerminalCommandPlanner.plan(root, project, "pwd");
            check(project.getCanonicalPath().equals(request.getWorkingDirectory().getCanonicalPath()), "Loaded project must be terminal working directory");
        } finally { delete(root); }
    }

    private static File tempDirectory(String name) throws Exception {
        File root = File.createTempFile(name, "-devxyz");
        if (!root.delete() || !root.mkdirs()) throw new AssertionError("Cannot create temp directory");
        return root;
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
}
