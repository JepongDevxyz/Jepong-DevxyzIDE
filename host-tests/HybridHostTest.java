import com.jepongdevxyz.idebuild.core.ProjectRootDetector;
import com.jepongdevxyz.idebuild.core.SafeZip;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout;
import com.jepongdevxyz.idebuild.core.build.GradleCompatibility;
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
        System.out.println("HYBRID HOST TESTS PASSED: " + passed + "/6");
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

    private static File temp(String name) throws IOException {
        File f = File.createTempFile("devxyz-" + name, "");
        if (!f.delete() || !f.mkdirs()) throw new IOException("temp");
        return f;
    }
    private static void touch(File f) throws IOException { File p=f.getParentFile(); if(p!=null) p.mkdirs(); new FileOutputStream(f).close(); }
    private static void eq(Object a,Object b,String m){ if(!a.equals(b)) throw new AssertionError(m+": "+a+" != "+b); }
}
