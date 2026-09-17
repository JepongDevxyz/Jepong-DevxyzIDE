import com.jepongdevxyz.idebuild.BuildRunner;
import com.jepongdevxyz.idebuild.core.build.BuildArtifact;
import com.jepongdevxyz.idebuild.core.build.BuildOutputScanner;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class BuildOutputScannerHostTest {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("devxyz-output-scan").toFile();
        try {
            write(new File(root, "app/build/outputs/apk/debug/app-debug.apk"), "apk");
            write(new File(root, "feature/build/outputs/bundle/release/feature-release.aab"), "aab");
            write(new File(root, "app/build/intermediates/ignored.apk"), "ignore");
            write(new File(root, ".gradle/cache.apk"), "ignore");

            List<BuildArtifact> results = BuildOutputScanner.scan(root, 10000);
            if (results.size() != 2) throw new AssertionError("Expected 2 artifacts but got " + results.size());
            BuildArtifact apk = results.get(0);
            BuildArtifact aab = results.get(1);
            if (!"APK".equals(apk.getType())) throw new AssertionError("APK should sort first");
            if (!apk.getRelativePath().equals("app/build/outputs/apk/debug/app-debug.apk")) throw new AssertionError(apk.getRelativePath());
            if (!"debug".equals(apk.getVariant())) throw new AssertionError("Wrong APK variant: " + apk.getVariant());
            if (!"AAB".equals(aab.getType())) throw new AssertionError("Expected AAB");
            if (!"release".equals(aab.getVariant())) throw new AssertionError("Wrong AAB variant: " + aab.getVariant());
            if (apk.getSizeBytes() != 3L) throw new AssertionError("Wrong size");

            String report = BuildRunner.describeArtifact(apk);
            if (report.indexOf("APK") < 0) throw new AssertionError("Type missing: " + report);
            if (report.indexOf("debug") < 0) throw new AssertionError("Variant missing: " + report);
            if (report.indexOf("3 B") < 0) throw new AssertionError("Size missing: " + report);
            if (report.indexOf("app/build/outputs/apk/debug/app-debug.apk") < 0) throw new AssertionError("Path missing: " + report);
            if (report.indexOf("modified=") < 0) throw new AssertionError("Modified time missing: " + report);
            System.out.println("BUILD OUTPUT SCANNER HOST TESTS PASSED: 2/2");
        } finally { deleteTree(root); }
    }

    private static void write(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new Exception("mkdir failed");
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(value.getBytes(StandardCharsets.UTF_8)); } finally { out.close(); }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
