package com.jepongdevxyz.idebuild.core.terminal;

import com.jepongdevxyz.idebuild.core.process.ProcessRequest;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds real shell process requests for the DevxyzIDE terminal.
 *
 * The app-private runtime is preferred when installed and executable. When it
 * is unavailable, Android's platform shell is used so the terminal still
 * exposes the commands actually available on the device instead of simulating
 * output.
 */
public final class TerminalCommandPlanner {
    private static final String ANDROID_SYSTEM_SHELL = "/system/bin/sh";

    private TerminalCommandPlanner() { }

    public static ProcessRequest plan(File filesDir, File projectRoot, String command) throws IOException {
        if (filesDir == null) throw new IllegalArgumentException("filesDir must not be null");
        if (command == null || command.trim().length() == 0) throw new IllegalArgumentException("command must not be blank");

        File canonicalFiles = filesDir.getCanonicalFile();
        File runtimeShell = findRuntimeShell(canonicalFiles);
        String shell = runtimeShell == null ? ANDROID_SYSTEM_SHELL : runtimeShell.getCanonicalPath();
        File workingDirectory = chooseWorkingDirectory(canonicalFiles, projectRoot);
        Map<String, String> environment = runtimeShell == null
                ? Collections.<String, String>emptyMap()
                : runtimeEnvironment(canonicalFiles);

        return new ProcessRequest(
                Arrays.asList(shell, "-c", command),
                workingDirectory,
                environment,
                false,
                Collections.<String>emptyList());
    }

    public static String describeShell(File filesDir) throws IOException {
        if (filesDir == null) throw new IllegalArgumentException("filesDir must not be null");
        File runtimeShell = findRuntimeShell(filesDir.getCanonicalFile());
        return runtimeShell == null ? ANDROID_SYSTEM_SHELL : runtimeShell.getCanonicalPath();
    }

    private static File findRuntimeShell(File filesDir) {
        File bash = new File(filesDir, "usr/bin/bash");
        if (isExecutableFile(bash)) return bash;
        File sh = new File(filesDir, "usr/bin/sh");
        if (isExecutableFile(sh)) return sh;
        return null;
    }

    private static boolean isExecutableFile(File file) {
        return file.isFile() && file.canExecute();
    }

    private static File chooseWorkingDirectory(File filesDir, File projectRoot) throws IOException {
        if (projectRoot != null && projectRoot.isDirectory()) return projectRoot.getCanonicalFile();
        File home = new File(filesDir, "home");
        if (home.isDirectory()) return home.getCanonicalFile();
        return filesDir;
    }

    private static Map<String, String> runtimeEnvironment(File filesDir) throws IOException {
        File prefix = new File(filesDir, "usr").getCanonicalFile();
        File home = new File(filesDir, "home").getCanonicalFile();
        File bin = new File(prefix, "bin");
        File lib = new File(prefix, "lib");
        File tmp = new File(filesDir, "tmp");

        HashMap<String, String> environment = new HashMap<String, String>();
        environment.put("PREFIX", prefix.getAbsolutePath());
        environment.put("HOME", home.getAbsolutePath());
        environment.put("PATH", bin.getAbsolutePath() + ":/system/bin:/system/xbin");
        environment.put("LD_LIBRARY_PATH", lib.getAbsolutePath());
        environment.put("TMPDIR", tmp.getAbsolutePath());
        return environment;
    }
}
