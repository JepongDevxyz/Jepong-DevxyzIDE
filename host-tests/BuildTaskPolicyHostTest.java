import com.jepongdevxyz.idebuild.core.build.BuildTaskPolicy;

public final class BuildTaskPolicyHostTest {
    private static int passed;

    public static void main(String[] args) {
        classifiesArtifactProducingTasks();
        leavesMaintenanceTasksWithoutApkRequirement();
        System.out.println("BUILD TASK POLICY HOST TESTS PASSED: " + passed + "/2");
    }

    private static void classifiesArtifactProducingTasks() {
        assertEquals("debug", BuildTaskPolicy.expectedApkVariant("assembleDebug"));
        assertEquals("release", BuildTaskPolicy.expectedApkVariant("assembleRelease"));
        assertEquals("debug", BuildTaskPolicy.expectedApkVariant(":app:assembleDebug"));
        passed++;
    }

    private static void leavesMaintenanceTasksWithoutApkRequirement() {
        assertNull(BuildTaskPolicy.expectedApkVariant("clean"));
        assertNull(BuildTaskPolicy.expectedApkVariant("tasks"));
        assertNull(BuildTaskPolicy.expectedApkVariant("lint"));
        passed++;
    }

    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertNull(Object value) {
        if (value != null) throw new AssertionError("Expected null but was " + value);
    }
}
