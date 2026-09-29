package com.jepongdevxyz.idebuild

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit

class ProjectTemplateWriterTest {
    @Test(timeout = 900_000)
    fun generatedProjectBuildsAndProducesSignedInstallableApk() {
        val repository = findRepositoryRoot()
        val workspace = kotlin.io.path.createTempDirectory("devxyz-generated-build").toFile()
        try {
            val project = ProjectTemplateWriter.create(
                workspace,
                "GeneratedSmokeApp",
                File(repository, "app/src/main/assets/gradle-wrapper.jar").inputStream()
            )
            val process = ProcessBuilder("sh", "./gradlew", "--no-daemon", "--stacktrace", "assembleDebug")
                .directory(project)
                .redirectErrorStream(true)
                .redirectOutput(File(project, "template-build.log"))
                .start()
            val completed = process.waitFor(10, TimeUnit.MINUTES)
            if (!completed) process.destroyForcibly().waitFor()
            val output = File(project, "template-build.log").takeIf { it.isFile }?.readText().orEmpty()
            assertTrue("Generated starter project build timed out:\n$output", completed)
            assertEquals("Generated starter project did not build:\n$output", 0, process.waitFor())

            val apk = File(project, "app/build/outputs/apk/debug/app-debug.apk")
            assertTrue("Generated debug APK is missing", apk.isFile && apk.length() > 0)
            val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
                ?: error("Android SDK path is required for the generated APK check")
            val apksigner = File(androidHome, "build-tools/35.0.0/apksigner")
            val verify = ProcessBuilder(apksigner.absolutePath, "verify", "--verbose", apk.absolutePath)
                .redirectErrorStream(true)
                .start()
            val signatureOutput = verify.inputStream.bufferedReader().use { it.readText() }
            assertEquals("Generated APK signature verification failed:\n$signatureOutput", 0, verify.waitFor())

            val handoff = File(repository, "app/build/template-smoke").apply { mkdirs() }
            apk.copyTo(File(handoff, "generated-app.apk"), overwrite = true)
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test fun createsInstallableAndroidProjectSkeleton() {
        val workspace = kotlin.io.path.createTempDirectory("devxyz-template-test").toFile()
        val project = ProjectTemplateWriter.create(workspace, "My Demo App", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        assertTrue(File(project, "settings.gradle.kts").isFile)
        assertTrue(File(project, "build.gradle.kts").isFile)
        assertTrue(File(project, "app/build.gradle.kts").readText().contains("com.example.mydemoapp"))
        assertTrue(File(project, "app/src/main/AndroidManifest.xml").readText().contains("android.intent.action.MAIN"))
        assertTrue(File(project, "app/src/main/java/com/example/mydemoapp/MainActivity.kt").isFile)
        assertTrue(File(project, "gradle/wrapper/gradle-wrapper.jar").length() > 0)
        assertTrue(File(project, "gradlew").canExecute())
        assertTrue(File(project, "gradle/wrapper/gradle-wrapper.properties").readText().contains("gradle-8.10.2-bin.zip"))
        workspace.deleteRecursively()
    }

    @Test fun sanitizesNamesAndDoesNotOverwriteExistingProject() {
        val workspace = kotlin.io.path.createTempDirectory("devxyz-template-test").toFile()
        val project = ProjectTemplateWriter.create(workspace, "../4!!!", ByteArrayInputStream(byteArrayOf(1)))
        assertEquals("App4", project.name)
        assertThrows(IllegalArgumentException::class.java) {
            ProjectTemplateWriter.create(workspace, "../4!!!", ByteArrayInputStream(byteArrayOf(1)))
        }
        assertTrue(File(project, "app/src/main/AndroidManifest.xml").isFile)
        workspace.deleteRecursively()
    }

    private fun findRepositoryRoot(): File {
        var directory: File? = File(System.getProperty("user.dir")).canonicalFile
        while (directory != null && !File(directory, "app/src/main/assets/gradle-wrapper.jar").isFile) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Could not locate the DevxyzIDE source root" }
    }
}
