import com.jepongdevxyz.idebuild.core.SafeZip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SafeZipCancellationHostTest {
    public static void main(String[] args) throws Exception {
        preparedCancellationStopsAndCleansEmptyDestination();
        successfulLegacyExtractionStillWorks();
        System.out.println("SAFE ZIP CANCELLATION HOST TESTS PASSED: 2/2");
    }

    private static void preparedCancellationStopsAndCleansEmptyDestination() throws Exception {
        File parent = Files.createTempDirectory("devxyz-safezip-cancel").toFile();
        File destination = new File(parent, "project");
        if (!destination.mkdir()) throw new AssertionError("Could not create destination");
        try {
            SafeZip.prepareProjectExtraction();
            SafeZip.cancelPreparedOrActiveProjectExtraction();
            boolean canceled = false;
            try {
                SafeZip.extractProject(new ByteArrayInputStream(zip("app/file.txt", "hello")), destination, 20, 1024);
            } catch (SafeZip.ExtractionCanceledException expected) {
                canceled = true;
            }
            if (!canceled) throw new AssertionError("Expected prepared extraction cancellation");
            if (destination.exists()) throw new AssertionError("Canceled empty import destination must be cleaned");
        } finally {
            SafeZip.clearProjectExtractionRequest();
            deleteTree(parent);
        }
    }

    private static void successfulLegacyExtractionStillWorks() throws Exception {
        File parent = Files.createTempDirectory("devxyz-safezip-success").toFile();
        File destination = new File(parent, "project");
        if (!destination.mkdir()) throw new AssertionError("Could not create destination");
        try {
            SafeZip.prepareProjectExtraction();
            SafeZip.extractProject(new ByteArrayInputStream(zip("settings.gradle", "include ':app'")), destination, 20, 1024);
            if (!new File(destination, "settings.gradle").isFile()) throw new AssertionError("Expected extracted file");
        } finally {
            SafeZip.clearProjectExtractionRequest();
            deleteTree(parent);
        }
    }

    private static byte[] zip(String name, String text) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes);
        try {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(text.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } finally {
            zip.close();
        }
        return bytes.toByteArray();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
