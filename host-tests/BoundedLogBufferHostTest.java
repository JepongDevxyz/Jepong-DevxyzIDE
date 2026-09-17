import com.jepongdevxyz.idebuild.core.log.BoundedLogBuffer;

public final class BoundedLogBufferHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        keepsNewestLinesWithinBounds();
        marksTruncationAfterDroppingOldLogs();
        concurrentAppendsDoNotCorruptState();
        System.out.println("BOUNDED LOG BUFFER HOST TESTS PASSED: " + passed + "/3");
    }

    private static void keepsNewestLinesWithinBounds() {
        BoundedLogBuffer log = new BoundedLogBuffer(80, 3);
        log.appendLine("one");
        log.appendLine("two");
        log.appendLine("three");
        log.appendLine("four");

        String snapshot = log.snapshot();
        assertFalse(snapshot.contains("one"));
        assertTrue(snapshot.contains("two"));
        assertTrue(snapshot.contains("three"));
        assertTrue(snapshot.contains("four"));
        assertTrue(log.lineCount() <= 3);
        assertTrue(snapshot.length() <= 80);
        passed++;
    }

    private static void marksTruncationAfterDroppingOldLogs() {
        BoundedLogBuffer log = new BoundedLogBuffer(45, 10);
        log.appendLine("abcdefghijklmnopqrstuvwxyz");
        log.appendLine("01234567890123456789012345");

        String snapshot = log.snapshot();
        assertTrue(log.getDroppedLineCount() > 0);
        assertTrue(snapshot.startsWith("[older log output truncated]"));
        assertTrue(snapshot.length() <= 45);
        passed++;
    }

    private static void concurrentAppendsDoNotCorruptState() throws Exception {
        final BoundedLogBuffer log = new BoundedLogBuffer(20000, 500);
        Thread[] workers = new Thread[6];
        for (int worker = 0; worker < workers.length; worker++) {
            final int id = worker;
            workers[worker] = new Thread(new Runnable() {
                @Override public void run() {
                    for (int i = 0; i < 100; i++) log.appendLine("worker-" + id + "-" + i);
                }
            });
            workers[worker].start();
        }
        for (Thread worker : workers) worker.join();

        assertTrue(log.lineCount() <= 500);
        assertTrue(log.snapshot().length() <= 20000);
        assertTrue(log.getDroppedLineCount() >= 100);
        passed++;
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }
}
