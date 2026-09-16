import com.jepongdevxyz.idebuild.core.ProjectImportService;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Normal-CI streaming stress test. It expands a 32 MiB project asset without
 * holding the expanded project in memory. The optional manual stress workflow
 * exercises the same code path at 1 GiB class.
 */
public final class ProjectImportStressHostTest {
    private static final long EXPANDED_BYTES = 32L * 1024L * 1024L;

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("devxyz-import-stress").toFile();
        File zipFile = new File(root, "stress.zip");
        File projects = new File(root, "projects");
        if (!projects.mkdir()) throw new AssertionError("Could not create projects directory");
        try {
            createLargeProjectZip(zipFile, EXPANDED_BYTES);
            final long[] latest = new long[]{0L};
            ProjectImportService.ImportResult result;
            FileInputStream input = new FileInputStream(zipFile);
            try {
                result = ProjectImportService.importProject(
                        input, projects, "StressProject.zip", 100,
                        EXPANDED_BYTES + (4L * 1024L * 1024L),
                        ProjectImportService.NEVER_CANCELLED,
                        new ProjectImportService.ProgressListener() {
                            @Override public void onProgress(int entries, long expandedBytes) {
                                latest[0] = expandedBytes;
                            }
                        });
            } finally { input.close(); }

            File expanded = new File(result.getProjectRoot(), "app/src/main/assets/payload.bin");
            if (!expanded.isFile()) throw new AssertionError("Expanded payload missing");
            if (expanded.length() != EXPANDED_BYTES) throw new AssertionError("Wrong expanded size: " + expanded.length());
            if (latest[0] < EXPANDED_BYTES) throw new AssertionError("Progress did not reach payload size: " + latest[0]);
            System.out.println("PROJECT IMPORT STRESS TEST PASSED: expanded=" + expanded.length());
        } finally { deleteTree(root); }
    }

    private static void createLargeProjectZip(File zipFile, long expandedBytes) throws Exception {
        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
        try {
            putText(zip, "StressProject/settings.gradle", "include ':app'\n");
            putText(zip, "StressProject/app/build.gradle", "apply plugin: 'com.android.application'\n");
            putText(zip, "StressProject/app/src/main/AndroidManifest.xml", "<manifest package=\"devxyz.stress\"/>\n");
            zip.putNextEntry(new ZipEntry("StressProject/app/src/main/assets/payload.bin"));
            byte[] chunk = new byte[64 * 1024];
            for (int i = 0; i < chunk.length; i++) chunk[i] = (byte) ((i * 17) & 0xff);
            long remaining = expandedBytes;
            while (remaining > 0) {
                int count = (int) Math.min((long) chunk.length, remaining);
                zip.write(chunk, 0, count);
                remaining -= count;
            }
            zip.closeEntry();
        } finally { zip.close(); }
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes("UTF-8"));
        zip.closeEntry();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
