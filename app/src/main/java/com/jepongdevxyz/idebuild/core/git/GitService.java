package com.jepongdevxyz.idebuild.core.git;

import com.jepongdevxyz.idebuild.core.process.ProcessEngine;
import com.jepongdevxyz.idebuild.core.process.ProcessRequest;
import com.jepongdevxyz.idebuild.core.process.ProcessResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Real Git integration backed by DevxyzIDE's shared ProcessEngine.
 * No Git output is synthesized: every result comes from the installed git executable.
 */
public final class GitService {
    private static final long COMMAND_TIMEOUT_SECONDS = 60L;

    private GitService() { }

    public static boolean isGitAvailable(File workingDirectory) {
        GitResult result = execute(workingDirectory,
                list("git", "--version"),
                Collections.<String, String>emptyMap(),
                Collections.<String>emptyList());
        return result.isSuccess() && result.getStdout().toLowerCase().indexOf("git version") >= 0;
    }

    public static GitResult init(File repository) {
        return execute(repository, list("git", "init"), emptyEnvironment(), emptySecrets());
    }

    public static GitResult status(File repository) {
        return execute(repository, list("git", "status", "--porcelain"), emptyEnvironment(), emptySecrets());
    }

    public static GitResult stage(File repository, String path) {
        requirePath(path);
        return execute(repository, list("git", "add", "--", path), emptyEnvironment(), emptySecrets());
    }

    public static GitResult unstage(File repository, String path) {
        requirePath(path);
        return execute(repository, list("git", "reset", "HEAD", "--", path), emptyEnvironment(), emptySecrets());
    }

    public static GitResult commit(File repository, String message, String authorName, String authorEmail) {
        requireText(message, "message");
        requireText(authorName, "authorName");
        requireText(authorEmail, "authorEmail");
        return execute(repository,
                list("git",
                        "-c", "user.name=" + authorName,
                        "-c", "user.email=" + authorEmail,
                        "commit", "-m", message),
                emptyEnvironment(),
                emptySecrets());
    }

    public static GitResult diff(File repository, boolean staged) {
        return staged
                ? execute(repository, list("git", "diff", "--cached"), emptyEnvironment(), emptySecrets())
                : execute(repository, list("git", "diff"), emptyEnvironment(), emptySecrets());
    }

    public static GitResult createBranch(File repository, String branchName) {
        requireText(branchName, "branchName");
        return execute(repository, list("git", "branch", branchName), emptyEnvironment(), emptySecrets());
    }

    public static GitResult branches(File repository) {
        return execute(repository, list("git", "branch", "--list"), emptyEnvironment(), emptySecrets());
    }

    public static GitResult checkout(File repository, String branchName) {
        requireText(branchName, "branchName");
        return execute(repository, list("git", "checkout", branchName), emptyEnvironment(), emptySecrets());
    }

    public static GitResult currentBranch(File repository) {
        return execute(repository, list("git", "branch", "--show-current"), emptyEnvironment(), emptySecrets());
    }

    public static GitResult remoteAdd(File repository, String name, String url) {
        requireText(name, "name");
        requireText(url, "url");
        return execute(repository, list("git", "remote", "add", name, url), emptyEnvironment(), emptySecrets());
    }

    public static GitResult fetch(File repository,
                                  String remote,
                                  Map<String, String> environment,
                                  List<String> secrets) {
        requireText(remote, "remote");
        return execute(repository, list("git", "fetch", "--prune", remote), environment, secrets);
    }

    public static GitResult pull(File repository,
                                 String remote,
                                 String branch,
                                 Map<String, String> environment,
                                 List<String> secrets) {
        requireText(remote, "remote");
        requireText(branch, "branch");
        return execute(repository, list("git", "pull", "--ff-only", remote, branch), environment, secrets);
    }

    public static GitResult push(File repository,
                                 String remote,
                                 String branch,
                                 Map<String, String> environment,
                                 List<String> secrets) {
        requireText(remote, "remote");
        requireText(branch, "branch");
        return execute(repository, list("git", "push", "-u", remote, branch), environment, secrets);
    }

    public static GitResult cloneRepository(File workingDirectory,
                                            String url,
                                            String destination,
                                            Map<String, String> environment,
                                            List<String> secrets) {
        requireText(url, "url");
        requireText(destination, "destination");
        return execute(workingDirectory, list("git", "clone", "--", url, destination), environment, secrets);
    }

    /**
     * Small general-purpose Git runner retained for tests and advanced Git actions.
     * Command shape: git + prefixArgs + args + trailingArg (when nonblank).
     */
    public static GitResult run(File workingDirectory,
                                List<String> prefixArgs,
                                List<String> args,
                                String trailingArg) {
        ArrayList<String> command = new ArrayList<String>();
        command.add("git");
        if (prefixArgs != null) command.addAll(prefixArgs);
        if (args != null) command.addAll(args);
        if (trailingArg != null && trailingArg.length() > 0) command.add(trailingArg);
        return execute(workingDirectory, command, emptyEnvironment(), emptySecrets());
    }

    public static GitResult run(File workingDirectory,
                                List<String> gitArguments,
                                Map<String, String> environment,
                                List<String> secrets) {
        ArrayList<String> command = new ArrayList<String>();
        command.add("git");
        if (gitArguments != null) command.addAll(gitArguments);
        return execute(workingDirectory, command, environment, secrets);
    }

    private static GitResult execute(File workingDirectory,
                                     List<String> command,
                                     Map<String, String> environment,
                                     List<String> secrets) {
        final StringBuilder stdout = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();
        final GitResult[] completed = new GitResult[1];
        final CountDownLatch finished = new CountDownLatch(1);

        ProcessRequest request = new ProcessRequest(
                command,
                workingDirectory,
                environment == null ? emptyEnvironment() : environment,
                false,
                secrets == null ? emptySecrets() : secrets);

        final ProcessEngine.RunningProcess running = ProcessEngine.start(request, new ProcessEngine.Listener() {
            @Override public void onStdout(String line) {
                appendLine(stdout, line);
            }

            @Override public void onStderr(String line) {
                appendLine(stderr, line);
            }

            @Override public void onFinished(ProcessResult result) {
                completed[0] = new GitResult(
                        result.getExitCode(),
                        result.isCancelled(),
                        result.getDurationMillis(),
                        stdout.toString(),
                        stderr.toString());
                finished.countDown();
            }
        });

        try {
            if (!finished.await(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                running.cancel();
                finished.await(5L, TimeUnit.SECONDS);
                GitResult late = completed[0];
                if (late != null) return late;
                return new GitResult(124, true, COMMAND_TIMEOUT_SECONDS * 1000L,
                        stdout.toString(), appendTimeout(stderr));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            running.cancel();
            return new GitResult(130, true, 0L, stdout.toString(), appendInterrupted(stderr));
        }

        GitResult result = completed[0];
        return result == null
                ? new GitResult(1, false, 0L, stdout.toString(), appendMissingResult(stderr))
                : result;
    }

    private static void appendLine(StringBuilder builder, String line) {
        if (line == null) return;
        builder.append(line).append('\n');
    }

    private static String appendTimeout(StringBuilder stderr) {
        if (stderr.length() > 0 && stderr.charAt(stderr.length() - 1) != '\n') stderr.append('\n');
        stderr.append("Git command timed out\n");
        return stderr.toString();
    }

    private static String appendInterrupted(StringBuilder stderr) {
        if (stderr.length() > 0 && stderr.charAt(stderr.length() - 1) != '\n') stderr.append('\n');
        stderr.append("Git command interrupted\n");
        return stderr.toString();
    }

    private static String appendMissingResult(StringBuilder stderr) {
        if (stderr.length() > 0 && stderr.charAt(stderr.length() - 1) != '\n') stderr.append('\n');
        stderr.append("Git process ended without a result\n");
        return stderr.toString();
    }

    private static void requirePath(String path) {
        requireText(path, "path");
        if (path.indexOf('\u0000') >= 0) throw new IllegalArgumentException("path contains NUL");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static Map<String, String> emptyEnvironment() {
        return Collections.unmodifiableMap(new HashMap<String, String>());
    }

    private static List<String> emptySecrets() {
        return Collections.emptyList();
    }

    private static List<String> list(String... values) {
        ArrayList<String> list = new ArrayList<String>();
        if (values != null) {
            for (String value : values) list.add(value);
        }
        return list;
    }
}
