import com.jepongdevxyz.idebuild.core.ProjectRootDetector;
import com.jepongdevxyz.idebuild.core.SafeZip;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimeCapabilities;
import com.jepongdevxyz.idebuild.core.build.GradleCompatibility;
import com.jepongdevxyz.idebuild.core.build.ProjectAnalyzer;
import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;
import java.io.*;

public final class HybridHostTest {
    public static void main(String[] args) throws Exception {
        int passed = 0;
        testNestedProjectRoot(); passed++;
        testRootProjectPreferred(); passed++;
        testLargeLongCounter(); passed++;
        testInternalGradleDetection(); passed++;
        testAgp87RequiresGradle89(); passed++;
        testCompatibleInternalGradleSelection(); passed++;
        testModernProjectRequirements(); passed++;
        testModernRuntimeMissingJdk(); passed++;
        testModernRuntimeComplete(); passed++;
        testModernRuntimeHigherJdk(); passed++;
        System.out.println("HYBRID HOST TESTS PASSED: " + passed + "/10");
    }

    private static void testNestedProjectRoot() throws Exception {
        File t = temp("nested");
        File p = new File(t, "outer/DevxyzMusic");
        if (!p.mkdirs()) throw new IOException();
        touch(new File(p, "settings.gradle"));
        touch(new File(p, "build.gradle"));
        File found = ProjectRootDetector.findBestGradleRoot(t, 4, 1000);
        eq(p.getCanonicalFile(), found.getCanonicalFile(), "nested project root");
    }

    private static void testRootProjectPreferred() throws Exception {
        File t = temp("root");
        touch(new File(t, "settings.gradle"));
        File nested = new File(t, "sample"); nested.mkdirs(); touch(new File(nested, "settings.gradle"));
        File found = ProjectRootDetector.findBestGradleRoot(t, 4, 1000);
        eq(t.getCanonicalFile(), found.getCanonicalFile(), "root project preferred");
    }

    private static void testLargeLongCounter() throws Exception {
        long overTwoGiB = 3L * 1024L * 1024L * 1024L;
        if (!SafeZip.isWithinExpandedLimit(overTwoGiB - 1L, 1L, overTwoGiB)) throw new AssertionError("long counter rejects exact limit");
        if (SafeZip.isWithinExpandedLimit(overTwoGiB, 1L, overTwoGiB)) throw new AssertionError("long counter accepts overflow");
    }

    private static void testInternalGradleDetection() throws Exception {
        File t = temp("gradle");
        File gradle = new File(t, "usr/bin/gradle"); touch(gradle);
        File found = RuntimeLayout.findGradleExecutable(t);
        eq(gradle.getCanonicalFile(), found.getCanonicalFile(), "internal gradle");
    }

    private static void testAgp87RequiresGradle89() throws Exception {
        eq("8.9", GradleCompatibility.minimumGradleForAgp("8.7.3"), "AGP 8.7 minimum Gradle");
    }

    private static void testCompatibleInternalGradleSelection() throws Exception {
        File t = temp("gradle-version");
        File oldGradle = new File(t, "toolchains/gradle-4.6/bin/gradle"); touch(oldGradle);
        File goodGradle = new File(t, "toolchains/gradle-8.9/bin/gradle"); touch(goodGradle);
        File found = RuntimeLayout.findGradleExecutable(t, "8.9");
        eq(goodGradle.getCanonicalFile(), found.getCanonicalFile(), "compatible internal gradle");
    }

    private static void testModernProjectRequirements() throws Exception {
        ProjectRequirements req = modernRequirements();
        eq("8.7.3", req.getAgpVersion(), "modern AGP");
        eq("8.9", req.getMinimumGradleVersion(), "minimum Gradle");
        eq(Integer.valueOf(17), Integer.valueOf(req.getJavaMajor()), "recommended Java");
        eq(Integer.valueOf(35), Integer.valueOf(req.getCompileSdk()), "compile SDK");
    }

    private static void testModernRuntimeMissingJdk() throws Exception {
        File runtime = temp("runtime-missing");
        ProjectRequirements req = modernRequirements();
        RuntimeCapabilities capabilities = RuntimeCapabilities.inspect(runtime, req);
        if (capabilities.isReady()) throw new AssertionError("missing runtime unexpectedly ready");
        eq("JDK 17+ required", capabilities.getMissingRequirement(), "missing JDK message");
    }

    private static void testModernRuntimeComplete() throws Exception {
        File runtime = temp("runtime-complete");
        touch(new File(runtime, "toolchains/jdk17/bin/java"));
        touch(new File(runtime, "toolchains/gradle-8.9/bin/gradle"));
        touch(new File(runtime, "toolchains/android-sdk/platforms/android-35/android.jar"));
        touch(new File(runtime, "toolchains/android-sdk/build-tools/35.0.0/aapt2"));
        ProjectRequirements req = modernRequirements();
        RuntimeCapabilities capabilities = RuntimeCapabilities.inspect(runtime, req);
        if (!capabilities.isReady()) throw new AssertionError("complete runtime not ready: " + capabilities.getMissingRequirement());
        eq(new File(runtime, "toolchains/jdk17").getCanonicalFile(), capabilities.getJavaHome().getCanonicalFile(), "JDK 17 selected");
        eq(new File(runtime, "toolchains/gradle-8.9/bin/gradle").getCanonicalFile(), capabilities.getGradleExecutable().getCanonicalFile(), "Gradle 8.9 selected");
    }

    private static void testModernRuntimeHigherJdk() throws Exception {
        File runtime = temp("runtime-jdk21");
        touch(new File(runtime, "usr/lib/jvm/java-21-openjdk/bin/java"));
        touch(new File(runtime, "toolchains/gradle-8.9/bin/gradle"));
        touch(new File(runtime, "toolchains/android-sdk/platforms/android-35/android.jar"));
        touch(new File(runtime, "usr/bin/aapt2"));
        ProjectRequirements req = modernRequirements();
        RuntimeCapabilities capabilities = RuntimeCapabilities.inspect(runtime, req);
        if (!capabilities.isReady()) throw new AssertionError("JDK 21 runtime should satisfy JDK 17 minimum: " + capabilities.getMissingRequirement());
        eq(new File(runtime, "usr/lib/jvm/java-21-openjdk").getCanonicalFile(), capabilities.getJavaHome().getCanonicalFile(), "JDK 21 selected");
        eq(new File(runtime, "usr/bin/aapt2").getCanonicalFile(), capabilities.getAapt2().getCanonicalFile(), "Termux aapt2 selected");
    }

    private static ProjectRequirements modernRequirements() throws Exception {
        File p = temp("modern-project");
        write(new File(p, "settings.gradle"), "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n");
        write(new File(p, "build.gradle"), "plugins { id 'com.android.application' version '8.7.3' apply false }\n");
        File app = new File(p, "app"); app.mkdirs();
        write(new File(app, "build.gradle"), "plugins { id 'com.android.application' }\nandroid { compileSdk 35\n defaultConfig { minSdk 23; targetSdk 35 } }\n");
        return ProjectAnalyzer.analyze(p);
    }

    private static File temp(String name) throws IOException {
        File f = File.createTempFile("devxyz-" + name, "");
        if (!f.delete() || !f.mkdirs()) throw new IOException("temp");
        return f;
    }
    private static void touch(File f) throws IOException { File p=f.getParentFile(); if(p!=null) p.mkdirs(); new FileOutputStream(f).close(); }
    private static void write(File f, String text) throws IOException { File p=f.getParentFile(); if(p!=null) p.mkdirs(); FileOutputStream out=new FileOutputStream(f); try { out.write(text.getBytes("UTF-8")); } finally { out.close(); } }
    private static void eq(Object a,Object b,String m){ if(a==null?b!=null:!a.equals(b)) throw new AssertionError(m+": "+a+" != "+b); }
}
