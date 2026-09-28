package com.jepongdevxyz.idebuild
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class ToolchainManagerTest {
    @Test fun missingToolchainAndWrapperAreReported() {
        val root=kotlin.io.path.createTempDirectory("devxyz-toolchain-test").toFile()
        val check=ToolchainManager(root).inspect(null)
        assertFalse(check.ready)
        assertTrue(check.report.contains("Embedded JDK 17 runtime"))
        assertTrue(check.report.contains("Gradle wrapper JAR"))
        assertTrue(check.report.contains("Android build-tool / apksigner"))
        root.deleteRecursively()
    }
}