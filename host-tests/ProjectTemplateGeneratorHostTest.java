import com.jepongdevxyz.idebuild.core.build.ProjectTemplateGenerator;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ProjectTemplateGeneratorHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        createsClassicJavaProject();
        createsModernAndroidxProject();
        createsModernAndroidxKotlinProject();
        rejectsUnsafeNamesAndPackages();
        System.out.println("PROJECT TEMPLATE GENERATOR HOST TESTS PASSED: " + passed + "/4");
    }

    private static void createsClassicJavaProject() throws Exception {
        File parent = Files.createTempDirectory("devxyz-classic-template").toFile();
        try {
            File root = ProjectTemplateGenerator.create(
                    parent,
                    "Hello Classic",
                    "com.example.classicapp",
                    ProjectTemplateGenerator.Template.CLASSIC_JAVA);
            assertTrue(new File(root, "settings.gradle").isFile());
            assertTrue(new File(root, "build.gradle").isFile());
            assertTrue(new File(root, "app/build.gradle").isFile());
            assertTrue(new File(root, "app/src/main/AndroidManifest.xml").isFile());
            assertTrue(new File(root, "app/src/main/java/com/example/classicapp/MainActivity.java").isFile());
            String appGradle = read(new File(root, "app/build.gradle"));
            String manifest = read(new File(root, "app/src/main/AndroidManifest.xml"));
            assertContains(appGradle, "compileSdkVersion 28");
            assertContains(appGradle, "applicationId 'com.example.classicapp'");
            assertContains(manifest, "package=\"com.example.classicapp\"");
            assertContains(read(new File(root, "build.gradle")), "com.android.tools.build:gradle:3.2.1");
            passed++;
        } finally {
            deleteTree(parent);
        }
    }

    private static void createsModernAndroidxProject() throws Exception {
        File parent = Files.createTempDirectory("devxyz-modern-template").toFile();
        try {
            File root = ProjectTemplateGenerator.create(
                    parent,
                    "Hello Modern",
                    "com.example.modernapp",
                    ProjectTemplateGenerator.Template.MODERN_ANDROIDX_JAVA);
            String appGradle = read(new File(root, "app/build.gradle"));
            String rootGradle = read(new File(root, "build.gradle"));
            String manifest = read(new File(root, "app/src/main/AndroidManifest.xml"));
            String activity = read(new File(root, "app/src/main/java/com/example/modernapp/MainActivity.java"));
            assertContains(rootGradle, "com.android.application");
            assertContains(appGradle, "namespace 'com.example.modernapp'");
            assertContains(appGradle, "compileSdk 35");
            assertContains(appGradle, "androidx.appcompat:appcompat");
            assertContains(activity, "androidx.appcompat.app.AppCompatActivity");
            assertNotContains(manifest, "package=\"");
            passed++;
        } finally {
            deleteTree(parent);
        }
    }

    private static void createsModernAndroidxKotlinProject() throws Exception {
        File parent = Files.createTempDirectory("devxyz-kotlin-template").toFile();
        try {
            File root = ProjectTemplateGenerator.create(
                    parent,
                    "Hello Kotlin",
                    "com.example.kotlinapp",
                    ProjectTemplateGenerator.Template.MODERN_ANDROIDX_KOTLIN);
            String appGradle = read(new File(root, "app/build.gradle"));
            String rootGradle = read(new File(root, "build.gradle"));
            String activity = read(new File(root, "app/src/main/kotlin/com/example/kotlinapp/MainActivity.kt"));
            assertContains(rootGradle, "org.jetbrains.kotlin.android");
            assertContains(rootGradle, "2.0.21");
            assertContains(appGradle, "id 'org.jetbrains.kotlin.android'");
            assertContains(appGradle, "namespace 'com.example.kotlinapp'");
            assertContains(appGradle, "compileSdk 35");
            assertContains(activity, "class MainActivity : AppCompatActivity()");
            assertContains(activity, "override fun onCreate");
            passed++;
        } finally {
            deleteTree(parent);
        }
    }

    private static void rejectsUnsafeNamesAndPackages() throws Exception {
        File parent = Files.createTempDirectory("devxyz-template-guard").toFile();
        try {
            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception {
                    ProjectTemplateGenerator.create(parent, "../escape", "com.example.safe", ProjectTemplateGenerator.Template.CLASSIC_JAVA);
                }
            });
            expectFailure(new ThrowingRunnable() {
                @Override public void run() throws Exception {
                    ProjectTemplateGenerator.create(parent, "Safe", "bad-package", ProjectTemplateGenerator.Template.CLASSIC_JAVA);
                }
            });
            passed++;
        } finally {
            deleteTree(parent);
        }
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void expectFailure(ThrowingRunnable action) throws Exception {
        boolean failed = false;
        try { action.run(); } catch (IllegalArgumentException expected) { failed = true; }
        if (!failed) throw new AssertionError("Expected IllegalArgumentException");
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void assertContains(String value, String expected) {
        if (value.indexOf(expected) < 0) throw new AssertionError("Expected text: " + expected + "\nActual:\n" + value);
    }

    private static void assertNotContains(String value, String unexpected) {
        if (value.indexOf(unexpected) >= 0) throw new AssertionError("Unexpected text: " + unexpected + "\nActual:\n" + value);
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private interface ThrowingRunnable { void run() throws Exception; }
}
