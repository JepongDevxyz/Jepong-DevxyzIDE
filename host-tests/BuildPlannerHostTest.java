import com.jepongdevxyz.idebuild.core.build.BuildPlan;
import com.jepongdevxyz.idebuild.core.build.BuildPlanner;
import com.jepongdevxyz.idebuild.core.build.ProjectAnalyzer;
import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

public final class BuildPlannerHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        offlineModeUsesPersistentCacheAndOfflineFlag();
        onlineModeDoesNotForceOfflineFlag();
        unsafeGradleTaskIsRejected();
        System.out.println("BUILD PLANNER HOST TESTS PASSED: " + passed + "/3");
    }

    private static void offlineModeUsesPersistentCacheAndOfflineFlag() throws Exception {
        Fixture fixture = fixture("offline");
        try {
            BuildPlan plan = BuildPlanner.planBuild(fixture.requirements, fixture.project, fixture.runtime, "assembleDebug", true);
            assertTrue(plan.canBuild());
            assertTrue(plan.getArguments().contains("--offline"));
            assertEquals(new File(fixture.runtime, "gradle-home").getAbsolutePath(), plan.getEnvironment().get("GRADLE_USER_HOME"));
            passed++;
        } finally { fixture.delete(); }
    }

    private static void onlineModeDoesNotForceOfflineFlag() throws Exception {
        Fixture fixture = fixture("online");
        try {
            BuildPlan plan = BuildPlanner.planBuild(fixture.requirements, fixture.project, fixture.runtime, "assembleDebug", false);
            assertTrue(plan.canBuild());
            assertFalse(plan.getArguments().contains("--offline"));
            assertTrue(plan.getArguments().contains("--no-daemon"));
            passed++;
        } finally { fixture.delete(); }
    }

    private static void unsafeGradleTaskIsRejected() throws Exception {
        Fixture fixture = fixture("task-guard");
        try {
            boolean failed = false;
            try {
                BuildPlanner.planBuild(fixture.requirements, fixture.project, fixture.runtime, "assembleDebug; rm -rf /", false);
            } catch (IllegalArgumentException expected) { failed = true; }
            assertTrue(failed);
            passed++;
        } finally { fixture.delete(); }
    }

    private static Fixture fixture(String name) throws Exception {
        File root = Files.createTempDirectory("devxyz-build-plan-" + name).toFile();
        File project = new File(root, "project");
        File runtime = new File(root, "runtime");
        project.mkdirs(); runtime.mkdirs();
        write(new File(project, "settings.gradle"), "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n");
        write(new File(project, "build.gradle"), "plugins { id 'com.android.application' version '8.7.3' apply false }\n");
        write(new File(project, "app/build.gradle"), "plugins { id 'com.android.application' }\nandroid { compileSdk 35\n defaultConfig { minSdk 23; targetSdk 35 } }\n");

        touch(new File(runtime, "toolchains/jdk17/bin/java"));
        touch(new File(runtime, "toolchains/gradle-8.9/bin/gradle"));
        touch(new File(runtime, "toolchains/android-sdk/platforms/android-35/android.jar"));
        touch(new File(runtime, "toolchains/android-sdk/build-tools/35.0.0/aapt2"));

        ProjectRequirements requirements = ProjectAnalyzer.analyze(project);
        return new Fixture(root, project, runtime, requirements);
    }

    private static void write(File file, String text) throws Exception {
        File parent = file.getParentFile(); if (parent != null) parent.mkdirs();
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(text.getBytes("UTF-8")); } finally { out.close(); }
    }

    private static void touch(File file) throws Exception {
        File parent = file.getParentFile(); if (parent != null) parent.mkdirs();
        new FileOutputStream(file).close();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private static final class Fixture {
        final File root, project, runtime;
        final ProjectRequirements requirements;
        Fixture(File root, File project, File runtime, ProjectRequirements requirements) {
            this.root = root; this.project = project; this.runtime = runtime; this.requirements = requirements;
        }
        void delete() { deleteTree(root); }
    }
}
