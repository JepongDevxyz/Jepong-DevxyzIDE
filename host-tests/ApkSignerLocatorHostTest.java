import com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

public final class ApkSignerLocatorHostTest {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("devxyz-apksigner-locator").toFile();
        try {
            File oldSigner = new File(root, "toolchains/android-sdk/build-tools/28.0.3/apksigner");
            File newSigner = new File(root, "toolchains/android-sdk/build-tools/35.0.0/apksigner");
            touch(oldSigner);
            touch(newSigner);
            File located = RuntimeLayout.findApksigner(root);
            if (located == null || !located.getCanonicalFile().equals(newSigner.getCanonicalFile())) {
                throw new AssertionError("Expected newest apksigner but got " + located);
            }
            System.out.println("APK SIGNER LOCATOR HOST TESTS PASSED: 1/1");
        } finally {
            deleteTree(root);
        }
    }

    private static void touch(File file) throws Exception {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new AssertionError("mkdir failed");
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(1); }
        finally { out.close(); }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
