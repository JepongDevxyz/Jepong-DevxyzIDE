import com.jepongdevxyz.idebuild.core.capability.Capability;
import com.jepongdevxyz.idebuild.core.capability.CapabilityRegistry;
import com.jepongdevxyz.idebuild.core.capability.CapabilityStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

public final class CapabilityRegistryHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        missingAndroidToolchainIsNeedsInstall();
        completeAndroidToolchainIsAvailable();
        System.out.println("CAPABILITY REGISTRY HOST TESTS PASSED: " + passed + "/2");
    }

    private static void missingAndroidToolchainIsNeedsInstall() throws Exception {
        Fixture fixture = fixture("missing");
        try {
            touch(new File(fixture.runtime, "toolchains/gradle-8.9/bin/gradle"));
            Capability capability = CapabilityRegistry.gradleBuild(fixture.project, fixture.runtime);
            assertEquals(CapabilityStatus.NEEDS_INSTALL, capability.getStatus());
            assertContains(capability.getUserMessage(), "JDK");
            assertContains(capability.getUserMessage(), "android-platform-35");
            assertContains(capability.getUserMessage(), "android-aapt2");
            passed++;
        } finally { fixture.delete(); }
    }

    private static void completeAndroidToolchainIsAvailable() throws Exception {
        Fixture fixture = fixture("ready");
        try {
            touch(new File(fixture.runtime, "toolchains/gradle-8.9/bin/gradle"));
            touch(new File(fixture.runtime, "toolchains/jdk17/bin/java"));
            touch(new File(fixture.runtime, "toolchains/android-sdk/platforms/android-35/android.jar"));
            touch(new File(fixture.runtime, "toolchains/android-sdk/build-tools/35.0.0/aapt2"));
            Capability capability = CapabilityRegistry.gradleBuild(fixture.project, fixture.runtime);
            assertEquals(CapabilityStatus.AVAILABLE, capability.getStatus());
            passed++;
        } finally { fixture.delete(); }
    }

    private static Fixture fixture(String name) throws Exception {
        File root = Files.createTempDirectory("devxyz-capability-" + name).toFile();
        File project = new File(root, "project");
        File runtime = new File(root, "runtime");
        project.mkdirs();
        runtime.mkdirs();
        write(new File(project, "settings.gradle"), "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n");
        write(new File(project, "build.gradle"), "plugins { id 'com.android.application' version '8.7.3' apply false }\n");
        write(new File(project, "app/build.gradle"), "plugins { id 'com.android.application' }\nandroid { compileSdk 35\n defaultConfig { minSdk 23; targetSdk 35 } }\n");
        return new Fixture(root, project, runtime);
    }

    private static void write(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(text.getBytes("UTF-8")); } finally { out.close(); }
    }

    private static void touch(File file) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        new FileOutputStream(file).close();
    }

    private static void assertContains(String actual, String expected) {
        if (actual == null || actual.indexOf(expected) < 0) {
            throw new AssertionError("Expected [" + expected + "] in [" + actual + "]");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static final class Fixture {
        final File root;
        final File project;
        final File runtime;

        Fixture(File root, File project, File runtime) {
            this.root = root;
            this.project = project;
            this.runtime = runtime;
        }

        void delete() { deleteTree(root); }
    }
}
