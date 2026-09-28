package com.jepongdevxyz.idebuild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectArchiveImporterTest {
    @Test fun rejectsArchiveWithoutGradleProject() {
        val staging = tempDirectory()
        assertThrows(IllegalArgumentException::class.java) {
            ProjectArchiveImporter.extract(zip("notes.txt" to "not a project"), staging)
        }
        staging.deleteRecursively()
    }

    @Test fun rejectsZipSlipWithoutWritingOutsideStaging() {
        val staging = tempDirectory()
        val outside = File(staging.parentFile, "escaped-${staging.name}.txt")
        assertThrows(IllegalArgumentException::class.java) {
            ProjectArchiveImporter.extract(zip("../${outside.name}" to "bad"), staging)
        }
        assertEquals(false, outside.exists())
        staging.deleteRecursively()
    }

    @Test fun findsNestedValidGradleProject() {
        val staging = tempDirectory()
        val root = ProjectArchiveImporter.extract(zip(
            "wrapper/Project/settings.gradle.kts" to "rootProject.name=\"Demo\"",
            "wrapper/Project/build.gradle.kts" to "plugins {}",
            "wrapper/Project/app/build.gradle.kts" to "plugins {}"
        ), staging)
        assertEquals("Project", root.name)
        staging.deleteRecursively()
    }

    private fun tempDirectory() = kotlin.io.path.createTempDirectory("devxyz-archive-test").toFile()

    private fun zip(vararg files: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            files.forEach { (name, text) ->
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray())
                out.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}