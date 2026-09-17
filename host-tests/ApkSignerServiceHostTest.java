import com.jepongdevxyz.idebuild.core.signing.ApkSignerService;
import com.jepongdevxyz.idebuild.core.signing.ApkSigningResult;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ApkSignerServiceHostTest {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("devxyz-signer").toFile();
        try {
            File signer = new File(root, "apksigner");
            String script = "#!/bin/sh\n" +
                    "set -eu\n" +
                    "if [ \"$1\" = sign ]; then\n" +
                    "  echo store=$DEVXYZ_KS_PASS\n" +
                    "  echo key=$DEVXYZ_KEY_PASS 1>&2\n" +
                    "  out=\"\"\n" +
                    "  prev=\"\"\n" +
                    "  last=\"\"\n" +
                    "  for arg in \"$@\"; do\n" +
                    "    if [ \"$prev\" = --out ]; then out=\"$arg\"; fi\n" +
                    "    prev=\"$arg\"\n" +
                    "    last=\"$arg\"\n" +
                    "  done\n" +
                    "  cp \"$last\" \"$out\"\n" +
                    "  exit 0\n" +
                    "fi\n" +
                    "if [ \"$1\" = verify ]; then\n" +
                    "  echo verified\n" +
                    "  exit 0\n" +
                    "fi\n" +
                    "exit 2\n";
            write(signer, script);
            if (!signer.setExecutable(true)) throw new AssertionError("Could not mark fake signer executable");

            File input = new File(root, "input.apk");
            write(input, "fake apk bytes");
            File keyStore = new File(root, "release.jks");
            write(keyStore, "fake keystore bytes");
            File output = new File(root, "output.apk");

            String storePass = "store-secret-123";
            String keyPass = "key-secret-456";
            ApkSigningResult result = ApkSignerService.signAndVerify(
                    signer, input, output, keyStore, "release", storePass, keyPass);

            if (!result.isSuccess()) {
                throw new AssertionError(
                        "Signing result failed: signExit=" + result.getSignExitCode()
                                + " verifyExit=" + result.getVerifyExitCode()
                                + " outputExists=" + output.isFile()
                                + " outputBytes=" + (output.isFile() ? output.length() : -1L)
                                + "\nsign stdout:\n" + result.getSignStdout()
                                + "\nsign stderr:\n" + result.getSignStderr()
                                + "\nverify stdout:\n" + result.getVerifyStdout()
                                + "\nverify stderr:\n" + result.getVerifyStderr());
            }
            assertTrue(output.isFile());
            String allLogs = result.getSignStdout() + result.getSignStderr() + result.getVerifyStdout() + result.getVerifyStderr();
            assertFalse(allLogs.contains(storePass));
            assertFalse(allLogs.contains(keyPass));
            assertTrue(allLogs.contains("***"));
            assertTrue(result.getVerifyStdout().contains("verified"));
            System.out.println("APK SIGNER SERVICE HOST TESTS PASSED: 1/1");
        } finally {
            deleteTree(root);
        }
    }

    private static void write(File file, String value) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(value.getBytes(StandardCharsets.UTF_8)); }
        finally { out.close(); }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
}
