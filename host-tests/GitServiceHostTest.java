import com.jepongdevxyz.idebuild.core.git.GitResult;
import com.jepongdevxyz.idebuild.core.git.GitService;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

public final class GitServiceHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("devxyz-git-test").toFile();
        try {
            File repo = new File(root, "repo");
            if (!repo.mkdir()) throw new AssertionError("Could not create repo");

            assertTrue(GitService.isGitAvailable(repo));
            assertOk(GitService.init(repo));
            assertTrue(new File(repo, ".git").isDirectory());

            write(new File(repo, "hello.txt"), "hello\n");
            GitResult status = GitService.status(repo);
            assertOk(status);
            assertContains(status.getStdout(), "?? hello.txt");

            assertOk(GitService.stage(repo, "hello.txt"));
            GitResult staged = GitService.status(repo);
            assertContains(staged.getStdout(), "A  hello.txt");

            assertOk(GitService.commit(repo, "Initial commit", "Devxyz Test", "devxyz@example.test"));
            GitResult clean = GitService.status(repo);
            assertEquals("", clean.getStdout().trim());

            write(new File(repo, "hello.txt"), "hello\nchanged\n");
            GitResult diff = GitService.diff(repo, false);
            assertOk(diff);
            assertContains(diff.getStdout(), "+changed");

            assertOk(GitService.stage(repo, "hello.txt"));
            GitResult stagedDiff = GitService.diff(repo, true);
            assertContains(stagedDiff.getStdout(), "+changed");
            assertOk(GitService.unstage(repo, "hello.txt"));
            assertContains(GitService.status(repo).getStdout(), " M hello.txt");

            assertOk(GitService.createBranch(repo, "feature/test"));
            assertContains(GitService.branches(repo).getStdout(), "feature/test");
            assertOk(GitService.checkout(repo, "feature/test"));
            assertContains(GitService.currentBranch(repo).getStdout(), "feature/test");

            File remote = new File(root, "remote.git");
            assertOk(GitService.run(root, Collections.singletonList("init"), Collections.singletonList("--bare"), remote.getAbsolutePath()));
            assertOk(GitService.remoteAdd(repo, "origin", remote.getAbsolutePath()));
            assertOk(GitService.stage(repo, "hello.txt"));
            assertOk(GitService.commit(repo, "Change", "Devxyz Test", "devxyz@example.test"));
            assertOk(GitService.push(repo, "origin", "feature/test", Collections.<String, String>emptyMap(), Collections.<String>emptyList()));
            assertOk(GitService.run(remote, Collections.singletonList("symbolic-ref"), Collections.singletonList("HEAD"), "refs/heads/feature/test"));

            File clone = new File(root, "clone");
            assertOk(GitService.cloneRepository(root, remote.getAbsolutePath(), clone.getAbsolutePath(), Collections.<String, String>emptyMap(), Collections.<String>emptyList()));
            assertTrue(new File(clone, ".git").isDirectory());
            assertContains(GitService.currentBranch(clone).getStdout(), "feature/test");

            write(new File(repo, "fetch.txt"), "available after fetch\n");
            assertOk(GitService.stage(repo, "fetch.txt"));
            assertOk(GitService.commit(repo, "Fetch target", "Devxyz Test", "devxyz@example.test"));
            assertOk(GitService.push(repo, "origin", "feature/test", Collections.<String, String>emptyMap(), Collections.<String>emptyList()));
            assertOk(GitService.fetch(clone, "origin", Collections.<String, String>emptyMap(), Collections.<String>emptyList()));
            GitResult fetched = GitService.run(clone,
                    Collections.singletonList("show"),
                    Collections.singletonList("refs/remotes/origin/feature/test:fetch.txt"),
                    null);
            assertOk(fetched);
            assertContains(fetched.getStdout(), "available after fetch");

            write(new File(repo, "pull.txt"), "available after pull\n");
            assertOk(GitService.stage(repo, "pull.txt"));
            assertOk(GitService.commit(repo, "Pull target", "Devxyz Test", "devxyz@example.test"));
            assertOk(GitService.push(repo, "origin", "feature/test", Collections.<String, String>emptyMap(), Collections.<String>emptyList()));
            assertOk(GitService.pull(clone, "origin", "feature/test", Collections.<String, String>emptyMap(), Collections.<String>emptyList()));
            assertContains(new String(Files.readAllBytes(new File(clone, "pull.txt").toPath()), StandardCharsets.UTF_8), "available after pull");

            passed++;
            System.out.println("GIT SERVICE HOST TESTS PASSED: " + passed + "/1");
        } finally {
            deleteTree(root);
        }
    }

    private static void write(File file, String value) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(value.getBytes(StandardCharsets.UTF_8)); }
        finally { out.close(); }
    }

    private static void assertOk(GitResult result) {
        if (result == null || !result.isSuccess()) {
            throw new AssertionError("Expected Git success, got: " + (result == null ? "null" : result.getExitCode() + "\n" + result.getStderr()));
        }
    }

    private static void assertContains(String value, String expected) {
        if (value == null || value.indexOf(expected) < 0) throw new AssertionError("Expected text: " + expected + "\nActual:\n" + value);
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected [" + expected + "] but was [" + actual + "]");
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
