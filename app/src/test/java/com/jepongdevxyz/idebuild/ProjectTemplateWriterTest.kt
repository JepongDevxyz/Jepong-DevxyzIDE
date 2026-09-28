package com.jepongdevxyz.idebuild

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class ProjectTemplateWriterTest {
    @Test fun createsInstallableAndroidProjectSkeleton() {
        val workspace = kotlin.io.path.createTempDirectory("devxyz-template-test").toFile()
        val project = ProjectTemplateWriter.create(workspace, "My Demo App", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        assertTrue(File(project, "settings.gradle.kts").isFile)
        assertTrue(File(project, "build.gradle.kts").isFile)
        assertTrue(File(project, "app/build.gradle.kts").readText().contains("applicationId=\\"com.example.mydemoapp\\""))
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
}