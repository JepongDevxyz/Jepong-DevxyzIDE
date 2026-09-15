package com.jepongdevxyz.idebuild.core.build;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Pattern;

/** Creates small real Android projects that DevxyzIDE can immediately open and build. */
public final class ProjectTemplateGenerator {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");

    public enum Template {
        CLASSIC_JAVA,
        MODERN_ANDROIDX_JAVA
    }

    private ProjectTemplateGenerator() { }

    public static File create(File projectsParent, String projectName, String applicationId, Template template) throws IOException {
        if (projectsParent == null || !projectsParent.isDirectory()) {
            throw new IllegalArgumentException("projectsParent must be an existing directory");
        }
        if (template == null) throw new IllegalArgumentException("template must not be null");
        String cleanName = validateProjectName(projectName);
        String cleanPackage = validatePackage(applicationId);
        File root = new File(projectsParent, cleanName.replace(' ', '-'));
        if (root.exists()) throw new IllegalArgumentException("Project already exists: " + root.getName());
        if (!root.mkdir()) throw new IOException("Could not create project directory");

        try {
            createCommonDirectories(root, cleanPackage);
            if (template == Template.CLASSIC_JAVA) writeClassic(root, cleanName, cleanPackage);
            else writeModern(root, cleanName, cleanPackage);
            write(new File(root, ".gitignore"), ".gradle/\nlocal.properties\n**/build/\n*.iml\n");
            return root.getCanonicalFile();
        } catch (IOException failure) {
            deleteTree(root);
            throw failure;
        } catch (RuntimeException failure) {
            deleteTree(root);
            throw failure;
        }
    }

    private static void createCommonDirectories(File root, String applicationId) throws IOException {
        mkdirs(new File(root, "app/src/main/java/" + applicationId.replace('.', '/')));
        mkdirs(new File(root, "app/src/main/res/layout"));
        mkdirs(new File(root, "app/src/main/res/values"));
    }

    private static void writeClassic(File root, String projectName, String applicationId) throws IOException {
        write(new File(root, "settings.gradle"), "include ':app'\n");
        write(new File(root, "build.gradle"),
                "buildscript {\n" +
                "    repositories {\n" +
                "        google()\n" +
                "        jcenter()\n" +
                "    }\n" +
                "    dependencies {\n" +
                "        classpath 'com.android.tools.build:gradle:3.2.1'\n" +
                "    }\n" +
                "}\n\n" +
                "allprojects {\n" +
                "    repositories {\n" +
                "        google()\n" +
                "        jcenter()\n" +
                "    }\n" +
                "}\n");
        write(new File(root, "app/build.gradle"),
                "apply plugin: 'com.android.application'\n\n" +
                "android {\n" +
                "    compileSdkVersion 28\n" +
                "    defaultConfig {\n" +
                "        applicationId '" + applicationId + "'\n" +
                "        minSdkVersion 21\n" +
                "        targetSdkVersion 28\n" +
                "        versionCode 1\n" +
                "        versionName '1.0'\n" +
                "    }\n" +
                "    compileOptions {\n" +
                "        sourceCompatibility JavaVersion.VERSION_1_7\n" +
                "        targetCompatibility JavaVersion.VERSION_1_7\n" +
                "    }\n" +
                "}\n\n" +
                "dependencies {\n" +
                "}\n");
        writeManifest(root, applicationId, false);
        writeActivity(root, applicationId, false);
        writeLayout(root, projectName);
        writeValues(root, projectName, false);
        write(new File(root, "DEVXYZ_TEMPLATE.txt"),
                "DevxyzIDE Classic Java template\n" +
                "Verified compatibility target: Gradle 4.6 + AGP 3.2.1 + Android SDK 28.\n");
    }

    private static void writeModern(File root, String projectName, String applicationId) throws IOException {
        write(new File(root, "settings.gradle"),
                "pluginManagement {\n" +
                "    repositories { google(); mavenCentral(); gradlePluginPortal() }\n" +
                "}\n" +
                "dependencyResolutionManagement {\n" +
                "    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n" +
                "    repositories { google(); mavenCentral() }\n" +
                "}\n" +
                "rootProject.name = '" + escapeGroovy(projectName) + "'\n" +
                "include ':app'\n");
        write(new File(root, "build.gradle"),
                "plugins {\n" +
                "    id 'com.android.application' version '8.7.3' apply false\n" +
                "}\n");
        write(new File(root, "gradle.properties"),
                "android.useAndroidX=true\n" +
                "android.nonTransitiveRClass=true\n" +
                "org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8\n");
        write(new File(root, "app/build.gradle"),
                "plugins {\n" +
                "    id 'com.android.application'\n" +
                "}\n\n" +
                "android {\n" +
                "    namespace '" + applicationId + "'\n" +
                "    compileSdk 35\n\n" +
                "    defaultConfig {\n" +
                "        applicationId '" + applicationId + "'\n" +
                "        minSdk 21\n" +
                "        targetSdk 35\n" +
                "        versionCode 1\n" +
                "        versionName '1.0'\n" +
                "    }\n\n" +
                "    compileOptions {\n" +
                "        sourceCompatibility JavaVersion.VERSION_17\n" +
                "        targetCompatibility JavaVersion.VERSION_17\n" +
                "    }\n" +
                "}\n\n" +
                "dependencies {\n" +
                "    implementation 'androidx.appcompat:appcompat:1.7.0'\n" +
                "}\n");
        writeManifest(root, applicationId, true);
        writeActivity(root, applicationId, true);
        writeLayout(root, projectName);
        writeValues(root, projectName, true);
        write(new File(root, "DEVXYZ_TEMPLATE.txt"),
                "DevxyzIDE Modern AndroidX Java template\n" +
                "Requires DevxyzIDE JDK 17 + Gradle 8.9-compatible runtime + Android SDK 35 pack.\n" +
                "Dependencies are resolved by Gradle and retained in the persistent Gradle cache.\n");
    }

    private static void writeManifest(File root, String applicationId, boolean modern) throws IOException {
        String theme = modern ? "@style/AppTheme" : "@android:style/Theme.Material.Light.NoActionBar";
        write(new File(root, "app/src/main/AndroidManifest.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"" + applicationId + "\">\n" +
                "    <application android:allowBackup=\"true\" android:label=\"@string/app_name\" android:theme=\"" + theme + "\">\n" +
                "        <activity android:name=\".MainActivity\" android:exported=\"true\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "</manifest>\n");
    }

    private static void writeActivity(File root, String applicationId, boolean modern) throws IOException {
        String baseImport = modern ? "import androidx.appcompat.app.AppCompatActivity;\n" : "import android.app.Activity;\n";
        String baseClass = modern ? "AppCompatActivity" : "Activity";
        write(new File(root, "app/src/main/java/" + applicationId.replace('.', '/') + "/MainActivity.java"),
                "package " + applicationId + ";\n\n" +
                baseImport +
                "import android.os.Bundle;\n\n" +
                "public class MainActivity extends " + baseClass + " {\n" +
                "    @Override protected void onCreate(Bundle savedInstanceState) {\n" +
                "        super.onCreate(savedInstanceState);\n" +
                "        setContentView(R.layout.activity_main);\n" +
                "    }\n" +
                "}\n");
    }

    private static void writeLayout(File root, String projectName) throws IOException {
        write(new File(root, "app/src/main/res/layout/activity_main.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    android:gravity=\"center\"\n" +
                "    android:orientation=\"vertical\"\n" +
                "    android:padding=\"24dp\">\n" +
                "    <TextView\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"@string/hello_text\"\n" +
                "        android:textSize=\"22sp\" />\n" +
                "</LinearLayout>\n");
    }

    private static void writeValues(File root, String projectName, boolean modern) throws IOException {
        write(new File(root, "app/src/main/res/values/strings.xml"),
                "<resources>\n" +
                "    <string name=\"app_name\">" + escapeXml(projectName) + "</string>\n" +
                "    <string name=\"hello_text\">Hello from " + escapeXml(projectName) + "</string>\n" +
                "</resources>\n");
        if (modern) {
            write(new File(root, "app/src/main/res/values/styles.xml"),
                    "<resources>\n" +
                    "    <style name=\"AppTheme\" parent=\"Theme.AppCompat.DayNight.NoActionBar\" />\n" +
                    "</resources>\n");
        }
    }

    private static String validateProjectName(String value) {
        if (value == null) throw new IllegalArgumentException("Project name must not be null");
        String name = value.trim();
        if (name.length() == 0 || name.length() > 80) throw new IllegalArgumentException("Project name must be 1-80 characters");
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\u0000') >= 0 || name.contains("..")) {
            throw new IllegalArgumentException("Project name contains an unsafe path sequence");
        }
        return name;
    }

    private static String validatePackage(String value) {
        if (value == null) throw new IllegalArgumentException("Application ID must not be null");
        String applicationId = value.trim();
        if (!PACKAGE.matcher(applicationId).matches()) throw new IllegalArgumentException("Invalid Android application ID");
        return applicationId;
    }

    private static void mkdirs(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create directory: " + directory);
    }

    private static void write(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) mkdirs(parent);
        OutputStream output = null;
        try {
            output = new BufferedOutputStream(new FileOutputStream(file));
            output.write(text.getBytes("UTF-8"));
            output.flush();
        } finally {
            if (output != null) try { output.close(); } catch (IOException ignored) { }
        }
    }

    private static String escapeGroovy(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
