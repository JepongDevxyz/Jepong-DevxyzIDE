import com.jepongdevxyz.idebuild.core.process.ProcessEngine;
import com.jepongdevxyz.idebuild.core.process.ProcessRequest;
import com.jepongdevxyz.idebuild.core.process.ProcessResult;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ProcessEngineHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        capturesStdoutStderrExitAndEnvironment();
        supportsCancellation();
        redactsConfiguredSecretsFromOutput();
        System.out.println("PROCESS ENGINE HOST TESTS PASSED: " + passed + "/3");
    }

    private static void capturesStdoutStderrExitAndEnvironment() throws Exception {
        File dir = Files.createTempDirectory("devxyz-process").toFile();
        try {
            List<String> command = new ArrayList<String>();
            command.add("sh");
            command.add("-c");
            command.add("printf 'out:%s\\n' \"$DEVXYZ_TEST\"; printf 'err-line\\n' >&2; exit 7");
            Map<String, String> env = new HashMap<String, String>();
            env.put("DEVXYZ_TEST", "ok");
            ProcessRequest request = new ProcessRequest(command, dir, env, false, new ArrayList<String>());
            final List<String> stdout = new ArrayList<String>();
            final List<String> stderr = new ArrayList<String>();
            final CountDownLatch finished = new CountDownLatch(1);
            final ProcessResult[] result = new ProcessResult[1];

            ProcessEngine.start(request, new ProcessEngine.Listener() {
                @Override public void onStdout(String line) { stdout.add(line); }
                @Override public void onStderr(String line) { stderr.add(line); }
                @Override public void onFinished(ProcessResult value) { result[0] = value; finished.countDown(); }
            });

            assertTrue(finished.await(10, TimeUnit.SECONDS));
            assertEquals(7, result[0].getExitCode());
            assertFalse(result[0].isCancelled());
            assertContains(stdout, "out:ok");
            assertContains(stderr, "err-line");
            passed++;
        } finally { dir.delete(); }
    }

    private static void supportsCancellation() throws Exception {
        List<String> command = new ArrayList<String>();
        command.add("sh");
        command.add("-c");
        command.add("printf 'started\\n'; exec sleep 20");
        ProcessRequest request = new ProcessRequest(command, null, new HashMap<String, String>(), true, new ArrayList<String>());
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        final ProcessResult[] result = new ProcessResult[1];

        ProcessEngine.RunningProcess running = ProcessEngine.start(request, new ProcessEngine.Listener() {
            @Override public void onStdout(String line) { if ("started".equals(line)) started.countDown(); }
            @Override public void onStderr(String line) { }
            @Override public void onFinished(ProcessResult value) { result[0] = value; finished.countDown(); }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        running.cancel();
        assertTrue(finished.await(8, TimeUnit.SECONDS));
        assertTrue(result[0].isCancelled());
        passed++;
    }

    private static void redactsConfiguredSecretsFromOutput() throws Exception {
        List<String> command = new ArrayList<String>();
        command.add("sh");
        command.add("-c");
        command.add("printf 'token=super-secret-value\\n'; printf 'super-secret-value\\n' >&2");
        List<String> secrets = new ArrayList<String>();
        secrets.add("super-secret-value");
        ProcessRequest request = new ProcessRequest(command, null, new HashMap<String, String>(), false, secrets);
        final List<String> all = new ArrayList<String>();
        final CountDownLatch finished = new CountDownLatch(1);

        ProcessEngine.start(request, new ProcessEngine.Listener() {
            @Override public void onStdout(String line) { all.add(line); }
            @Override public void onStderr(String line) { all.add(line); }
            @Override public void onFinished(ProcessResult value) { finished.countDown(); }
        });
        assertTrue(finished.await(10, TimeUnit.SECONDS));
        for (String line : all) {
            assertFalse(line.indexOf("super-secret-value") >= 0);
        }
        assertContains(all, "token=***");
        passed++;
    }

    private static void assertContains(List<String> values, String expected) {
        if (!values.contains(expected)) throw new AssertionError("Missing expected line: " + expected + " from " + values);
    }
    private static void assertEquals(int expected, int actual) { if (expected != actual) throw new AssertionError("Expected " + expected + " but was " + actual); }
    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
}
