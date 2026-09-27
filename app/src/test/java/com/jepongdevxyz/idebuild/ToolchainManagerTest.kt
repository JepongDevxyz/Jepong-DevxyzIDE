package com.jepongdevxyz.idebuild
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class ToolchainManagerTest{
 @Test fun missingToolchainIsReported(){
  val root=kotlin.io.path.createTempDirectory("devxyz-toolchain-test").toFile()
  val check=ToolchainManager(root).inspect(null)
  assertFalse(check.ready)
  assertTrue(check.report.contains("Embedded JDK 17 runtime"))
  root.deleteRecursively()
 }
}