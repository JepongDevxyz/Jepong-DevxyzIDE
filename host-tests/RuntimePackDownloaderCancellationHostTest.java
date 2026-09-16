import com.jepongdevxyz.idebuild.core.toolchain.RuntimePackDescriptor;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimePackDownloader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;

public final class RuntimePackDownloaderCancellationHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        cancellationRemovesPartFileAndPreservesExistingDestination();
        successfulDownloadReportsProgressAndCommitsAtomically();
        System.out.println("RUNTIME PACK DOWNLOAD CANCELLATION TESTS PASSED: " + passed + "/2");
    }

    private static void cancellationRemovesPartFileAndPreservesExistingDestination() throws Exception {
        File dir = Files.createTempDirectory("devxyz-runtime-cancel").toFile();
        try {
            final byte[] payload = payload(256 * 1024);
            RuntimePackDescriptor descriptor = RuntimePackDescriptor.forTest(
                    "cancel-pack", "gradle", "8.9", "arm64-v8a", sha256(payload), payload.length);
            final File destination = new File(dir, "pack.zip");
            Files.write(destination.toPath(), "old".getBytes("UTF-8"));
            final MutableCancellation cancellation = new MutableCancellation();
            boolean cancelled = false;
            try {
                RuntimePackDownloader.download(descriptor, destination,
                        new RuntimePackDownloader.InputStreamFactory() {
                            @Override public java.io.InputStream open() { return new ByteArrayInputStream(payload); }
                        },
                        cancellation,
                        new RuntimePackDownloader.ProgressListener() {
                            @Override public void onProgress(long downloadedBytes, long expectedBytes) {
                                if (downloadedBytes > 0) cancellation.cancelled = true;
                            }
                        });
            } catch (RuntimePackDownloader.DownloadCanceledException expected) {
                cancelled = true;
            }
            assertTrue(cancelled);
            assertEquals("old", new String(Files.readAllBytes(destination.toPath()), "UTF-8"));
            assertFalse(hasPartFile(dir));
            passed++;
        } finally { deleteTree(dir); }
    }

    private static void successfulDownloadReportsProgressAndCommitsAtomically() throws Exception {
        File dir = Files.createTempDirectory("devxyz-runtime-success").toFile();
        try {
            final byte[] payload = payload(130 * 1024);
            RuntimePackDescriptor descriptor = RuntimePackDescriptor.forTest(
                    "success-pack", "gradle", "8.9", "arm64-v8a", sha256(payload), payload.length);
            final long[] lastProgress = new long[]{0L};
            File destination = new File(dir, "pack.zip");
            RuntimePackDownloader.download(descriptor, destination,
                    new RuntimePackDownloader.InputStreamFactory() {
                        @Override public java.io.InputStream open() { return new ByteArrayInputStream(payload); }
                    },
                    RuntimePackDownloader.NEVER_CANCELLED,
                    new RuntimePackDownloader.ProgressListener() {
                        @Override public void onProgress(long downloadedBytes, long expectedBytes) {
                            lastProgress[0] = downloadedBytes;
                            if (expectedBytes != payload.length) throw new AssertionError("Wrong expected byte count");
                        }
                    });
            assertTrue(destination.isFile());
            assertEquals(payload.length, Files.readAllBytes(destination.toPath()).length);
            assertEquals(payload.length, lastProgress[0]);
            assertFalse(hasPartFile(dir));
            passed++;
        } finally { deleteTree(dir); }
    }

    private static byte[] payload(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 31 + 7);
        return bytes;
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder out = new StringBuilder();
        for (byte value : digest) out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }

    private static boolean hasPartFile(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File file : files) if (file.getName().contains(".part-")) return true;
        return false;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }
    private static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private static final class MutableCancellation implements RuntimePackDownloader.CancellationSignal {
        volatile boolean cancelled;
        @Override public boolean isCancelled() { return cancelled; }
    }
}
