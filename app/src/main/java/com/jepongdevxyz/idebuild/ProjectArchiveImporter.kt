package com.jepongdevxyz.idebuild
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

internal object ProjectArchiveImporter {
    private const val MAX_ENTRIES = 50_000
    private const val MAX_UNCOMPRESSED_BYTES = 1_500L * 1024 * 1024
    private const val MAX_DEPTH = 32
    private const val MAX_ROOT_SEARCH_DEPTH = 4

    fun extract(input: InputStream, staging: File): File {
        require(staging.mkdirs() || staging.isDirectory) { "Cannot create import staging directory" }
        val canonicalRoot = staging.canonicalFile
        var entries = 0
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= MAX_ENTRIES) { "ZIP contains too many entries" }
                val normalized = entry.name.replace('\\', '/')
                require(normalized.isNotBlank() && !normalized.startsWith('/')) { "ZIP contains an invalid path" }
                require(!normalized.split('/').any { it == ".." || it == "." }) { "ZIP contains an unsafe path" }
                require(normalized.split('/').size <= MAX_DEPTH) { "ZIP path is too deeply nested" }
                val output = File(canonicalRoot, normalized).canonicalFile
                require(output.path.startsWith(canonicalRoot.path + File.separator)) { "ZIP path escapes the project directory" }
                if (entry.isDirectory) {
                    require(output.mkdirs() || output.isDirectory) { "Cannot create project directory" }
                } else {
                    output.parentFile?.let { require(it.mkdirs() || it.isDirectory) { "Cannot create project directory" } }
                    output.outputStream().buffered().use { sink ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            totalBytes += read
                            require(totalBytes <= MAX_UNCOMPRESSED_BYTES) { "ZIP expands beyond 1.5 GB" }
                            sink.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val root = findGradleRoot(canonicalRoot)
            ?: throw IllegalArgumentException("ZIP does not contain a Gradle project (settings.gradle or settings.gradle.kts)")
        require(File(root, "build.gradle").isFile || File(root, "build.gradle.kts").isFile) {
            "Gradle project is incomplete: root build.gradle(.kts) is missing"
        }
        return root
    }

    private fun findGradleRoot(root: File): File? {
        fun hasSettings(dir: File) =
            File(dir, "settings.gradle").isFile || File(dir, "settings.gradle.kts").isFile
        if (hasSettings(root)) return root
        val candidates = root.walkTopDown().maxDepth(MAX_ROOT_SEARCH_DEPTH)
            .filter { it.isDirectory && it != root && hasSettings(it) }
            .toList()
        if (candidates.isEmpty()) return null
        val nearestDepth = candidates.minOf { it.relativeTo(root).path.count { ch -> ch == File.separatorChar } + 1 }
        val nearest = candidates.filter {
            it.relativeTo(root).path.count { ch -> ch == File.separatorChar } + 1 == nearestDepth
        }
        require(nearest.size == 1) { "ZIP contains multiple Gradle projects at the same directory level" }
        return nearest.single()
    }
}