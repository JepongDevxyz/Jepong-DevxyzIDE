import com.jepongdevxyz.idebuild.core.SafeZip;
import com.jepongdevxyz.idebuild.core.TextFileClassifier;
import com.jepongdevxyz.idebuild.core.ProjectFiles;
import com.jepongdevxyz.idebuild.core.build.ProjectAnalyzer;
import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;
import com.jepongdevxyz.idebuild.core.build.JvmCompatibility;
import com.jepongdevxyz.idebuild.core.build.BuildPlan;
import com.jepongdevxyz.idebuild.core.build.BuildPlanner;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainInventory;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainPackInstaller;
import com.jepongdevxyz.idebuild.core.toolchain.ExecutionPolicy;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainProvisioningPlan;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimePackDescriptor;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimePackDownloader;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimeProvisioner;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout;
import com.jepongdevxyz.idebuild.core.toolchain.TerminalBootstrapInstaller;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class HostSelfTest {
    public static void main(String[] args) throws Exception {
        testTextClassifier();
        testSafeZipExtractsNormalArchive();
        testSafeZipRejectsZipSlip();
        testProjectZipSkipsGeneratedContent();
        testProjectFilesStableRelativePaths();
        testProjectAnalyzerDetectsLegacyAndroidProject();
        testProjectAnalyzerResolvesAgpVersionVariable();
        testProjectAnalyzerDetectsCustomRepositoriesWithoutCredentials();
        testJvmCompatibilitySelection();
        testBuildPlannerUsesWrapperCacheAndAapt2Override();
        testBuildPlannerBlocksMissingRequiredToolchain();
        testVersionCatalogAndModernProjectDetection();
        testCompatibleJdkFallbackSelection();
        testToolchainInventory();
        testToolchainPackInstallAndChecksum();
        testBuildPlannerCustomTaskAndOfflineMode();
        testExecutionPolicyBoundary();
        testProvisioningPlanForLegacyAndModernProjects();
        testRuntimePackDescriptorValidation();
        testRuntimePackDownloaderChecksumAndAtomicWrite();
        testRuntimeProvisionerEndToEndWithVerifiedPack();
        testRuntimeLayoutRecognizesTermuxStylePaths();
        testTerminalBootstrapInstaller();
        System.out.println("HOST SELF-TESTS PASSED: 23/23");
    }

    private static void testTextClassifier() {
        require(TextFileClassifier.isTextFile("MainActivity.java"), "java must be text");
        require(TextFileClassifier.isTextFile("build.gradle.kts"), "kts must be text");
        require(TextFileClassifier.isTextFile("AndroidManifest.xml"), "xml must be text");
        require(!TextFileClassifier.isTextFile("app-release.apk"), "apk must not be text");
        require(!TextFileClassifier.isTextFile("icon.png"), "png must not be text");
    }

    private static void testSafeZipExtractsNormalArchive() throws Exception {
        File temp = Files.createTempDirectory("devxyz-normal-").toFile();
        File zip = new File(temp, "project.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("app/src/main/AndroidManifest.xml"));
            out.write("<manifest/>".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        File target = new File(temp, "out");
        try (InputStream in = new FileInputStream(zip)) {
            SafeZip.extract(in, target, 100, 1024 * 1024);
        }
        require(new File(target, "app/src/main/AndroidManifest.xml").isFile(), "normal zip must extract");
    }

    private static void testSafeZipRejectsZipSlip() throws Exception {
        File temp = Files.createTempDirectory("devxyz-slip-").toFile();
        File zip = new File(temp, "bad.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("../escape.txt"));
            out.write("nope".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        boolean rejected = false;
        try (InputStream in = new FileInputStream(zip)) {
            SafeZip.extract(in, new File(temp, "out"), 100, 1024 * 1024);
        } catch (IOException expected) {
            rejected = true;
        }
        require(rejected, "zip-slip entry must be rejected");
        require(!new File(temp, "escape.txt").exists(), "zip-slip must not escape target");
    }

    private static void testProjectZipSkipsGeneratedContent() throws Exception {
        File temp = Files.createTempDirectory("devxyz-smartzip-").toFile();
        File zip = new File(temp, "project.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            put(out, "Demo/app/src/main/AndroidManifest.xml", "<manifest/>");
            put(out, "Demo/app/build/intermediates/huge.bin", "generated");
            put(out, "Demo/.gradle/cache.bin", "cache");
            put(out, "Demo/local.properties", "sdk.dir=C:/old/sdk");
        }
        File target = new File(temp, "out");
        try (InputStream in = new FileInputStream(zip)) {
            SafeZip.extractProject(in, target, 100, 1024 * 1024);
        }
        require(new File(target, "Demo/app/src/main/AndroidManifest.xml").isFile(), "project source must extract");
        require(!new File(target, "Demo/app/build/intermediates/huge.bin").exists(), "module build output must be skipped");
        require(!new File(target, "Demo/.gradle/cache.bin").exists(), ".gradle cache must be skipped");
        require(!new File(target, "Demo/local.properties").exists(), "machine-local SDK path must be skipped");
    }

    private static void put(ZipOutputStream out, String name, String text) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static void testProjectFilesStableRelativePaths() throws Exception {
        File root = Files.createTempDirectory("devxyz-tree-").toFile();
        write(new File(root, "settings.gradle"), "rootProject.name='x'");
        write(new File(root, "app/src/main/MainActivity.java"), "class X {}");
        write(new File(root, "app/build/intermediates/tmp.txt"), "ignore");
        List<String> result = ProjectFiles.listRelativeFiles(root, 5000);
        require(result.equals(Arrays.asList("app/src/main/MainActivity.java", "settings.gradle")), "project file list must be sorted and skip build dirs: " + result);
    }

    private static void testProjectAnalyzerDetectsLegacyAndroidProject() throws Exception {
        File root = Files.createTempDirectory("devxyz-analyze-").toFile();
        write(new File(root, "gradle/wrapper/gradle-wrapper.properties"),
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-7.4.2-bin.zip\n");
        write(new File(root, "gradlew"), "#!/bin/sh\n");
        write(new File(root, "gradle/wrapper/gradle-wrapper.jar"), "jar");
        write(new File(root, "build.gradle"),
                "buildscript { dependencies { classpath \"com.android.tools.build:gradle:7.2.1\" } }\n" +
                "allprojects { repositories { google(); mavenCentral(); maven { url \"https://jitpack.io\" } } }\n");
        write(new File(root, "app/build.gradle"),
                "plugins { id 'com.android.application' }\nandroid { compileSdk 34\n defaultConfig { minSdkVersion 21; targetSdkVersion 34 } }\n" +
                "dependencies { implementation 'androidx.appcompat:appcompat:1.6.1' }\n");
        ProjectRequirements req = ProjectAnalyzer.analyze(root);
        require("7.4.2".equals(req.getGradleVersion()), "Gradle wrapper version detection failed: " + req.getGradleVersion());
        require("7.2.1".equals(req.getAgpVersion()), "AGP version detection failed: " + req.getAgpVersion());
        require(req.getCompileSdk() == 34, "compileSdk detection failed");
        require(req.getMinSdk() == 21, "minSdk detection failed");
        require(req.getTargetSdk() == 34, "targetSdk detection failed");
        require(req.usesAndroidX(), "AndroidX detection failed");
        require(req.isWrapperComplete(), "wrapper completeness detection failed");
        require(req.getRepositories().contains("google()"), "google repo missing");
        require(req.getRepositories().contains("mavenCentral()"), "mavenCentral repo missing");
        require(req.getRepositories().contains("https://jitpack.io"), "custom maven repo missing");
    }

    private static void testProjectAnalyzerResolvesAgpVersionVariable() throws Exception {
        File root = Files.createTempDirectory("devxyz-agp-var-").toFile();
        write(new File(root, "gradle/wrapper/gradle-wrapper.properties"),
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-7.4.2-bin.zip\n");
        write(new File(root, "gradlew"), "#!/bin/sh\n");
        write(new File(root, "gradle/wrapper/gradle-wrapper.jar"), "jar");
        write(new File(root, "build.gradle"),
                "buildscript {\n" +
                "  ext.build_gradle_version = '7.2.1'\n" +
                "  dependencies { classpath \"com.android.tools.build:gradle:$build_gradle_version\" }\n" +
                "}\n");
        ProjectRequirements req = ProjectAnalyzer.analyze(root);
        require("7.2.1".equals(req.getAgpVersion()), "AGP variable version detection failed: " + req.getAgpVersion());
        require(JvmCompatibility.recommendedJavaMajor(req.getAgpVersion(), req.getGradleVersion()) == 11, "AGP variable should select JDK 11");
    }

    private static void testProjectAnalyzerDetectsCustomRepositoriesWithoutCredentials() throws Exception {
        File root = Files.createTempDirectory("devxyz-repos-").toFile();
        write(new File(root, "settings.gradle.kts"),
                "pluginManagement { repositories { maven { url = uri(\"https://user:secret@example.com/private\") } } }\n");
        write(new File(root, "build.gradle.kts"),
                "plugins { id(\"com.android.application\") version \"9.4.0\" apply false }\n");
        ProjectRequirements req = ProjectAnalyzer.analyze(root);
        String joined = String.join("|", req.getRepositories());
        require(joined.contains("example.com/private"), "custom repository host/path should be visible");
        require(!joined.contains("secret") && !joined.contains("user:"), "repository diagnostics leaked credentials: " + joined);
        require(req.usesKotlinDsl(), "Kotlin DSL detection failed");
    }

    private static void testJvmCompatibilitySelection() {
        require(JvmCompatibility.recommendedJavaMajor("7.2.1", "7.4.2") == 11, "AGP 7.x should prefer JDK 11");
        require(JvmCompatibility.recommendedJavaMajor("8.8.0", "8.10.2") == 17, "AGP 8.x should prefer JDK 17");
        require(JvmCompatibility.recommendedJavaMajor("9.4.0", "9.6.0") == 17, "AGP 9.x should prefer JDK 17");
        require(JvmCompatibility.canRunGradle(17, "7.4.2"), "Gradle 7.4.2 should run on Java 17");
        require(!JvmCompatibility.canRunGradle(11, "9.6.0"), "Gradle 9.x must not be planned on Java 11");
        require(JvmCompatibility.canRunGradle(21, "8.5"), "Gradle 8.5 should run on Java 21");
    }

    private static void testBuildPlannerUsesWrapperCacheAndAapt2Override() throws Exception {
        File root = Files.createTempDirectory("devxyz-plan-project-").toFile();
        write(new File(root, "gradlew"), "#!/bin/sh\n");
        write(new File(root, "gradle/wrapper/gradle-wrapper.jar"), "jar");
        write(new File(root, "gradle/wrapper/gradle-wrapper.properties"),
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-7.4.2-bin.zip\n");
        write(new File(root, "build.gradle"), "buildscript { dependencies { classpath 'com.android.tools.build:gradle:7.2.1' } }\n");
        write(new File(root, "app/build.gradle"), "android { compileSdk 34 }\n");

        File filesDir = Files.createTempDirectory("devxyz-plan-files-").toFile();
        File jdk = new File(filesDir, "toolchains/jdk11/bin/java");
        write(jdk, "java");
        File androidJar = new File(filesDir, "toolchains/android-sdk/platforms/android-34/android.jar");
        write(androidJar, "jar");
        File aapt2 = new File(filesDir, "toolchains/aapt2/aapt2");
        write(aapt2, "aapt2");

        ProjectRequirements req = ProjectAnalyzer.analyze(root);
        BuildPlan plan = BuildPlanner.planDebugBuild(req, root, filesDir, false);
        require(plan.getBlockers().isEmpty(), "complete test toolchain should not block: " + plan.getBlockers());
        require(plan.getJavaMajor() == 11, "planner should select JDK 11");
        require(plan.getArguments().contains("assembleDebug"), "assembleDebug missing");
        boolean hasOverride = false;
        for (String arg : plan.getArguments()) if (arg.startsWith("-Pandroid.aapt2FromMavenOverride=")) hasOverride = true;
        require(hasOverride, "aapt2 override missing");
        require(plan.getEnvironment().get("GRADLE_USER_HOME").endsWith("gradle-home"), "persistent Gradle cache not configured");
        require(plan.getEnvironment().get("ANDROID_HOME").endsWith("toolchains" + File.separator + "android-sdk"), "Android SDK env missing");
    }

    private static void testBuildPlannerBlocksMissingRequiredToolchain() throws Exception {
        File root = Files.createTempDirectory("devxyz-block-project-").toFile();
        write(new File(root, "gradlew"), "#!/bin/sh\n");
        write(new File(root, "gradle/wrapper/gradle-wrapper.jar"), "jar");
        write(new File(root, "gradle/wrapper/gradle-wrapper.properties"),
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.6.0-bin.zip\n");
        write(new File(root, "build.gradle"), "plugins { id 'com.android.application' version '9.4.0' apply false }\n");
        write(new File(root, "app/build.gradle"), "android { compileSdk 37 }\n");
        ProjectRequirements req = ProjectAnalyzer.analyze(root);
        File filesDir = Files.createTempDirectory("devxyz-block-files-").toFile();
        BuildPlan plan = BuildPlanner.planDebugBuild(req, root, filesDir, false);
        require(!plan.getBlockers().isEmpty(), "missing toolchains must block build");
        String blockers = String.join("|", plan.getBlockers());
        require(blockers.contains("JDK 17"), "missing JDK blocker absent: " + blockers);
        require(blockers.contains("Android SDK platform 37"), "missing SDK platform blocker absent: " + blockers);
        require(blockers.contains("aapt2"), "missing aapt2 blocker absent: " + blockers);
    }


    private static void testVersionCatalogAndModernProjectDetection() throws Exception {
        File root = Files.createTempDirectory("devxyz-modern-").toFile();
        write(new File(root, "gradle/wrapper/gradle-wrapper.properties"),
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.6.0-bin.zip\n");
        write(new File(root, "gradlew"), "#!/bin/sh\n");
        write(new File(root, "gradle/wrapper/gradle-wrapper.jar"), "jar");
        write(new File(root, "gradle/libs.versions.toml"),
                "[versions]\n" +
                "agp = \"9.4.0\"\n" +
                "compileSdk = \"37\"\n" +
                "minSdk = \"23\"\n" +
                "targetSdk = \"37\"\n" +
                "[plugins]\n" +
                "android-application = { id = \"com.android.application\", version.ref = \"agp\" }\n");
        write(new File(root, "settings.gradle.kts"),
                "dependencyResolutionManagement { repositories { google(); mavenCentral() } }\n");
        write(new File(root, "build.gradle.kts"),
                "plugins { alias(libs.plugins.android.application) apply false }\n");
        write(new File(root, "app/build.gradle.kts"),
                "plugins { alias(libs.plugins.android.application); id(\"org.jetbrains.kotlin.android\"); id(\"org.jetbrains.kotlin.plugin.compose\") }\n" +
                "android { compileSdk = libs.versions.compileSdk.get().toInt(); defaultConfig { minSdk = libs.versions.minSdk.get().toInt(); targetSdk = libs.versions.targetSdk.get().toInt() }; buildFeatures { compose = true } }\n" +
                "dependencies { implementation(\"androidx.compose.ui:ui:1.10.0\") }\n");
        write(new File(root, "app/src/main/java/demo/MainActivity.kt"), "class MainActivity\n");
        write(new File(root, "app/src/main/cpp/CMakeLists.txt"), "cmake_minimum_required(VERSION 3.22)\n");
        ProjectRequirements req = ProjectAnalyzer.analyze(root);
        require("9.4.0".equals(req.getAgpVersion()), "version catalog AGP not detected: " + req.getAgpVersion());
        require(req.getCompileSdk() == 37 && req.getMinSdk() == 23 && req.getTargetSdk() == 37, "version catalog SDK values not detected: " + req.summary());
        require(req.usesVersionCatalog(), "version catalog flag missing");
        require(req.usesKotlin(), "Kotlin flag missing");
        require(req.usesCompose(), "Compose flag missing");
        require(req.usesNativeBuild(), "native build flag missing");
    }

    private static void testCompatibleJdkFallbackSelection() throws Exception {
        File filesDir = Files.createTempDirectory("devxyz-jdk-select-").toFile();
        write(new File(filesDir, "toolchains/jdk17/bin/java"), "java");
        int selectedLegacy = JvmCompatibility.chooseInstalledJavaMajor(filesDir, "7.2.1", "7.4.2");
        require(selectedLegacy == 17, "AGP 7 / Gradle 7 should fall back to compatible JDK 17 when JDK 11 is absent: " + selectedLegacy);
        int selectedModern = JvmCompatibility.chooseInstalledJavaMajor(filesDir, "9.4.0", "9.6.0");
        require(selectedModern == 17, "AGP 9 should select JDK 17: " + selectedModern);
        File jdk17 = new File(filesDir, "toolchains/jdk17/bin/java");
        require(jdk17.delete(), "test failed to remove JDK17 java");
        write(new File(filesDir, "toolchains/jdk21/bin/java"), "java");
        require(JvmCompatibility.chooseInstalledJavaMajor(filesDir, "9.4.0", "9.6.0") == 21, "AGP 9 / Gradle 9 should use compatible JDK 21 fallback");
    }

    private static void testToolchainInventory() throws Exception {
        File filesDir = Files.createTempDirectory("devxyz-inventory-").toFile();
        write(new File(filesDir, "toolchains/jdk11/bin/java"), "java");
        write(new File(filesDir, "toolchains/jdk17/bin/java"), "java");
        write(new File(filesDir, "toolchains/android-sdk/platforms/android-34/android.jar"), "jar");
        write(new File(filesDir, "toolchains/android-sdk/platforms/android-37/android.jar"), "jar");
        write(new File(filesDir, "toolchains/android-sdk/build-tools/36.0.0/aapt"), "aapt");
        write(new File(filesDir, "toolchains/aapt2/aapt2"), "aapt2");
        new File(filesDir, "toolchains/android-sdk/ndk/28.0.0").mkdirs();
        new File(filesDir, "toolchains/android-sdk/cmake/3.31.0").mkdirs();
        ToolchainInventory inv = ToolchainInventory.scan(filesDir);
        require(inv.getInstalledJdks().equals(Arrays.asList(11, 17)), "JDK inventory wrong: " + inv.getInstalledJdks());
        require(inv.getAndroidPlatforms().equals(Arrays.asList(34, 37)), "SDK inventory wrong: " + inv.getAndroidPlatforms());
        require(inv.getBuildTools().contains("36.0.0"), "build-tools inventory missing");
        require(inv.hasAapt2(), "aapt2 inventory missing");
        require(inv.hasNdk(), "NDK inventory missing");
        require(inv.hasCmake(), "CMake inventory missing");
    }

    private static void testToolchainPackInstallAndChecksum() throws Exception {
        File temp = Files.createTempDirectory("devxyz-pack-").toFile();
        byte[] javaBytes = "java-runtime".getBytes(StandardCharsets.UTF_8);
        String javaSha = sha256(javaBytes);
        File zip = new File(temp, "jdk17.devxyz-toolchain.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            put(out, "devxyz-toolchain.properties",
                    "format=1\n" +
                    "target=toolchains/jdk17\n" +
                    "file.bin/java=" + javaSha + "\n");
            out.putNextEntry(new ZipEntry("bin/java"));
            out.write(javaBytes);
            out.closeEntry();
        }
        File filesDir = new File(temp, "files");
        ToolchainPackInstaller.InstallResult result = ToolchainPackInstaller.install(zip, filesDir);
        require(result.getInstalledDirectory().equals(new File(filesDir, "toolchains/jdk17")), "toolchain target wrong");
        require(new File(filesDir, "toolchains/jdk17/bin/java").isFile(), "toolchain java not installed");

        File badZip = new File(temp, "bad.devxyz-toolchain.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(badZip))) {
            put(out, "devxyz-toolchain.properties",
                    "format=1\n" +
                    "target=toolchains/jdk17\n" +
                    "file.bin/java=" + "0".repeat(64) + "\n");
            out.putNextEntry(new ZipEntry("bin/java"));
            out.write(javaBytes);
            out.closeEntry();
        }
        boolean rejected = false;
        try { ToolchainPackInstaller.install(badZip, new File(temp, "bad-files")); }
        catch (IOException expected) { rejected = true; }
        require(rejected, "checksum mismatch must reject toolchain pack");
    }

    private static void testBuildPlannerCustomTaskAndOfflineMode() throws Exception {
        File root = Files.createTempDirectory("devxyz-custom-task-").toFile();
        write(new File(root, "gradlew"), "#!/bin/sh\n");
        write(new File(root, "gradle/wrapper/gradle-wrapper.jar"), "jar");
        write(new File(root, "gradle/wrapper/gradle-wrapper.properties"),
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.6.0-bin.zip\n");
        write(new File(root, "build.gradle"), "plugins { id 'com.android.application' version '9.4.0' apply false }\n");
        write(new File(root, "app/build.gradle"), "android { compileSdk 37 }\n");
        File filesDir = Files.createTempDirectory("devxyz-custom-files-").toFile();
        write(new File(filesDir, "toolchains/jdk17/bin/java"), "java");
        write(new File(filesDir, "toolchains/android-sdk/platforms/android-37/android.jar"), "jar");
        write(new File(filesDir, "toolchains/aapt2/aapt2"), "aapt2");
        ProjectRequirements req = ProjectAnalyzer.analyze(root);
        BuildPlan plan = BuildPlanner.planBuild(req, root, filesDir, ":app:bundleDebug", true);
        require(plan.canBuild(), "custom task plan unexpectedly blocked: " + plan.getBlockers());
        require(plan.getArguments().get(0).equals(":app:bundleDebug"), "custom task not first argument: " + plan.getArguments());
        require(plan.getArguments().contains("--offline"), "offline flag missing");
        boolean invalidRejected = false;
        try { BuildPlanner.planBuild(req, root, filesDir, "assembleDebug;rm -rf /", false); }
        catch (IllegalArgumentException expected) { invalidRejected = true; }
        require(invalidRejected, "unsafe Gradle task syntax must be rejected");
    }

    private static String sha256(byte[] bytes) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder out = new StringBuilder();
        for (byte b : hash) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }

    private static void write(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static void testExecutionPolicyBoundary() {
        require(ExecutionPolicy.canExecuteAppPrivateToolchains(28), "targetSdk 28 must permit app-private toolchain execution model");
        require(!ExecutionPolicy.canExecuteAppPrivateToolchains(29), "targetSdk 29+ must be rejected for app-private executable model");
        require(ExecutionPolicy.REQUIRED_HOST_TARGET_SDK == 28, "DevxyzIDE execution target must stay 28");
    }

    private static void testProvisioningPlanForLegacyAndModernProjects() throws Exception {
        File legacy = Files.createTempDirectory("devxyz-provision-legacy-").toFile();
        write(new File(legacy, "gradlew"), "#!/bin/sh\n");
        write(new File(legacy, "gradle/wrapper/gradle-wrapper.jar"), "jar");
        write(new File(legacy, "gradle/wrapper/gradle-wrapper.properties"),
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-7.4.2-bin.zip\n");
        write(new File(legacy, "build.gradle"), "buildscript { dependencies { classpath 'com.android.tools.build:gradle:7.2.1' } }\n");
        write(new File(legacy, "app/build.gradle"), "android { compileSdk 34 }\n");
        File emptyFiles = Files.createTempDirectory("devxyz-provision-files-").toFile();
        ToolchainProvisioningPlan lp = ToolchainProvisioningPlan.create(ProjectAnalyzer.analyze(legacy), emptyFiles);
        require(lp.getRequiredComponents().contains("jdk11-compatible"), "legacy plan must request compatible JDK: " + lp.getRequiredComponents());
        require(lp.getRequiredComponents().contains("android-platform-34"), "legacy plan must request SDK 34");
        require(lp.getRequiredComponents().contains("android-aapt2"), "legacy plan must request Android aapt2");

        File modern = Files.createTempDirectory("devxyz-provision-modern-").toFile();
        write(new File(modern, "gradlew"), "#!/bin/sh\n");
        write(new File(modern, "gradle/wrapper/gradle-wrapper.jar"), "jar");
        write(new File(modern, "gradle/wrapper/gradle-wrapper.properties"),
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.6.0-bin.zip\n");
        write(new File(modern, "build.gradle.kts"), "plugins { id(\"com.android.application\") version \"9.4.0\" apply false }\n");
        write(new File(modern, "app/build.gradle.kts"), "android { compileSdk = 37 }\n");
        write(new File(modern, "app/src/main/cpp/CMakeLists.txt"), "cmake_minimum_required(VERSION 3.22)\n");
        ToolchainProvisioningPlan mp = ToolchainProvisioningPlan.create(ProjectAnalyzer.analyze(modern), emptyFiles);
        require(mp.getRequiredComponents().contains("jdk17-compatible"), "modern plan must request JDK17-compatible runtime");
        require(mp.getRequiredComponents().contains("android-platform-37"), "modern plan must request SDK 37");
        require(mp.getRequiredComponents().contains("android-aapt2"), "modern plan must request aapt2");
        require(mp.getRequiredComponents().contains("android-ndk"), "native plan must request NDK");
        require(mp.getRecommendedComponents().contains("cmake"), "native plan should recommend CMake");
    }

    private static void testRuntimePackDescriptorValidation() throws Exception {
        Properties p = new Properties();
        p.setProperty("format", "1");
        p.setProperty("id", "jdk17-arm64");
        p.setProperty("component", "jdk");
        p.setProperty("version", "17.0.16");
        p.setProperty("abi", "arm64-v8a");
        p.setProperty("url", "https://example.invalid/jdk17.devxyz-toolchain.zip");
        p.setProperty("sha256", "a".repeat(64));
        p.setProperty("size", "12345");
        RuntimePackDescriptor d = RuntimePackDescriptor.from(p);
        require("jdk17-arm64".equals(d.getId()), "runtime descriptor id wrong");
        require("arm64-v8a".equals(d.getAbi()), "runtime descriptor ABI wrong");
        require(d.getExpectedBytes() == 12345L, "runtime descriptor size wrong");

        p.setProperty("url", "http://example.invalid/insecure.zip");
        boolean rejected = false;
        try { RuntimePackDescriptor.from(p); } catch (IOException expected) { rejected = true; }
        require(rejected, "runtime descriptor must reject non-HTTPS URLs");
    }

    private static void testRuntimePackDownloaderChecksumAndAtomicWrite() throws Exception {
        File temp = Files.createTempDirectory("devxyz-runtime-download-").toFile();
        byte[] payload = "verified-runtime-pack".getBytes(StandardCharsets.UTF_8);
        String sha = sha256(payload);
        RuntimePackDescriptor descriptor = RuntimePackDescriptor.forTest(
                "test-pack", "jdk", "17", "arm64-v8a", sha, payload.length);
        File destination = new File(temp, "downloaded.zip");
        RuntimePackDownloader.download(descriptor, destination, () -> new ByteArrayInputStream(payload));
        require(destination.isFile(), "verified runtime download not committed");
        require(Arrays.equals(payload, Files.readAllBytes(destination.toPath())), "runtime payload changed");

        RuntimePackDescriptor bad = RuntimePackDescriptor.forTest(
                "bad-pack", "jdk", "17", "arm64-v8a", "0".repeat(64), payload.length);
        File badDest = new File(temp, "bad.zip");
        boolean rejected = false;
        try { RuntimePackDownloader.download(bad, badDest, () -> new ByteArrayInputStream(payload)); }
        catch (IOException expected) { rejected = true; }
        require(rejected, "bad runtime checksum must be rejected");
        require(!badDest.exists(), "failed runtime download must not leave final file");
    }

    private static void testRuntimeProvisionerEndToEndWithVerifiedPack() throws Exception {
        File temp = Files.createTempDirectory("devxyz-runtime-provision-").toFile();
        byte[] javaBytes = "java-runtime-v17".getBytes(StandardCharsets.UTF_8);
        String innerSha = sha256(javaBytes);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            put(out, "devxyz-toolchain.properties",
                    "format=1\n" +
                    "target=toolchains/jdk17\n" +
                    "file.bin/java=" + innerSha + "\n");
            out.putNextEntry(new ZipEntry("bin/java"));
            out.write(javaBytes);
            out.closeEntry();
        }
        byte[] pack = bytes.toByteArray();
        RuntimePackDescriptor descriptor = RuntimePackDescriptor.forTest(
                "jdk17-test", "jdk", "17", "arm64-v8a", sha256(pack), pack.length);
        File downloads = new File(temp, "downloads");
        File filesDir = new File(temp, "files");
        ToolchainPackInstaller.InstallResult result = RuntimeProvisioner.installForTest(
                descriptor, downloads, filesDir, () -> new ByteArrayInputStream(pack));
        require(result.getInstalledDirectory().equals(new File(filesDir, "toolchains/jdk17")), "runtime provision target wrong");
        require(new File(filesDir, "toolchains/jdk17/bin/java").isFile(), "runtime provision did not install java");
        require(!new File(downloads, "jdk17-test.devxyz-toolchain.zip").exists(), "runtime provision should clean downloaded pack after install");
    }


    private static void testRuntimeLayoutRecognizesTermuxStylePaths() throws Exception {
        File files = Files.createTempDirectory("devxyz-layout-").toFile();
        write(new File(files, "usr/lib/jvm/java-17-openjdk/bin/java"), "java17");
        write(new File(files, "home/android-sdk/platforms/android-37/android.jar"), "android");
        write(new File(files, "home/android-sdk/build-tools/36.0.0/aapt2"), "aapt2");
        RuntimeLayout layout = RuntimeLayout.resolve(files, 17);
        require(layout.getJavaHome().equals(new File(files, "usr/lib/jvm/java-17-openjdk")), "Termux JDK layout not resolved: " + layout.getJavaHome());
        require(layout.getAndroidSdk().equals(new File(files, "home/android-sdk")), "Android SDK home layout not resolved");
        require(layout.getGradleHome().equals(new File(files, "home/.gradle")), "Gradle home layout not resolved");
        require(layout.getAapt2().equals(new File(files, "home/android-sdk/build-tools/36.0.0/aapt2")), "Build-tools aapt2 not resolved");
        ToolchainInventory inv = ToolchainInventory.scan(files);
        require(inv.hasJdk(17), "inventory must see Termux-style JDK17");
        require(inv.hasAndroidPlatform(37), "inventory must see home/android-sdk platform");
        require(inv.hasAapt2(), "inventory must see Android SDK build-tools aapt2");
    }

    private static void testTerminalBootstrapInstaller() throws Exception {
        File temp = Files.createTempDirectory("devxyz-bootstrap-").toFile();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            put(out, "devxyz-bootstrap.properties", "format=1\napplicationId=com.jepongdevxyz.idebuild\narch=aarch64\n");
            put(out, "bin/java", "java");
            put(out, "lib/jvm/java-17-openjdk/bin/java", "java17");
            put(out, "SYMLINKS.txt", "lib/jvm/java-17-openjdk/bin/java←bin/java17\n");
        }
        TerminalBootstrapInstaller.InstallResult result = TerminalBootstrapInstaller.install(
                new ByteArrayInputStream(bytes.toByteArray()), temp, "com.jepongdevxyz.idebuild");
        File prefix = new File(temp, "usr");
        require(result.getPrefix().equals(prefix), "bootstrap prefix wrong");
        require(new File(prefix, "bin/java").isFile(), "bootstrap normal file missing");
        require(Files.isSymbolicLink(new File(prefix, "bin/java17").toPath()), "bootstrap symlink missing");

        ByteArrayOutputStream wrong = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(wrong)) {
            put(out, "devxyz-bootstrap.properties", "format=1\napplicationId=com.other.app\narch=aarch64\n");
            put(out, "bin/sh", "sh");
            put(out, "SYMLINKS.txt", "bin/sh←bin/shell\n");
        }
        boolean rejected = false;
        try { TerminalBootstrapInstaller.install(new ByteArrayInputStream(wrong.toByteArray()), new File(temp, "wrong"), "com.jepongdevxyz.idebuild"); }
        catch (IOException expected) { rejected = true; }
        require(rejected, "bootstrap built for another applicationId must be rejected");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
