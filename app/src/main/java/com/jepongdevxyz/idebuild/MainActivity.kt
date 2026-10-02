package com.jepongdevxyz.idebuild

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Xml
import android.view.View
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.jepongdevxyz.idebuild.databinding.ActivityMainBinding
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * DevxyzIDE main hub — single activity hosting the five pixel-perfect
 * sections (Files / Search / Build / Tools / Settings) plus the code
 * editor overlay, bottom navigation and ALL legacy IDE logic
 * (ZIP import with Zip Slip protection, export, terminal, git,
 * toolchain import, Gradle build, APK install, project search).
 * Nothing from the original MainActivity was removed.
 */
class MainActivity : AppCompatActivity() {

    lateinit var b: ActivityMainBinding
    val prefs by lazy { getSharedPreferences("devxyzide", MODE_PRIVATE) }

    var projectRoot: File? = null
    var currentFile: File? = null
    var editorContent: String = ""
    var pendingFile: File? = null
    var pendingTitle: String = "MainActivity.java"
    val openTabs = mutableMapOf<String, String>()
    var visibleFiles = listOf<File>()
    var dirty = false
    var loadingEditor = false

    var lastBuildCode: Int? = null
    var lastBuildLog: String = ""
    var lastBuildApk: File? = null
    var buildStartMs: Long = 0L

    var explorerListener: (() -> Unit)? = null
    var buildListener: (() -> Unit)? = null
    var editorSyncListener: (() -> Unit)? = null

    private val importer =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) importProject(u) }
    private val toolchainImporter =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) importToolchain(u) }
    private val apkPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) signApkUri(u) }

    // ---------- lifecycle ----------

    override fun onCreate(s: Bundle?) {
        // Saved theme must apply before inflation (standing requirement).
        val theme = prefs.getString("theme", "dark") ?: "dark"
        AppCompatDelegate.setDefaultNightMode(
            if (theme == "light") AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        )
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        restoreWorkspace()

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_files -> showTab(ProjectsFragment(), "projects")
                R.id.nav_search -> showTab(SearchFragment(), "search")
                R.id.nav_build -> showTab(BuildFragment(), "build")
                R.id.nav_tools -> showTab(ToolsFragment(), "tools")
                R.id.nav_settings -> showTab(SettingsFragment(), "settings")
                else -> return@setOnItemSelectedListener false
            }
            true
        }
        if (s == null) {
            showTab(ProjectsFragment(), "projects")
            b.bottomNav.selectedItemId = R.id.nav_files
        }
    }

    fun showTab(fragment: Fragment, tag: String) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
        b.bottomNav.visibility = View.VISIBLE
    }

    private fun openEditor() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, EditorFragment(), "editor")
            .addToBackStack("editor")
            .commit()
        b.bottomNav.visibility = View.GONE
    }

    fun openFileInEditor(file: File) {
        if (file.length() > 2_000_000) return toast("File too large")
        pendingFile = file
        pendingTitle = file.name
        currentFile = file
        editorContent = runCatching { file.readText() }.getOrElse { "Cannot open as text" }
        openTabs[file.name] = editorContent
        dirty = false
        loadingEditor = false
        openEditor()
    }

    fun openSampleFile(title: String, content: String) {
        pendingFile = null
        pendingTitle = title
        currentFile = null
        editorContent = content
        openTabs[title] = content
        dirty = false
        openEditor()
    }

    fun applyTheme(mode: String) {
        prefs.edit().putString("theme", mode).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (mode == "light") AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        )
    }

    // ---------- legacy workspace logic (all preserved) ----------

    private fun restoreWorkspace() {
        prefs.getString("root", null)?.let(::File)?.takeIf { it.isDirectory }?.let {
            projectRoot = it
            refreshExplorer()
        }
    }

    fun launchImporter() {
        importer.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    fun launchToolchainImporter() {
        toolchainImporter.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    fun importProject(uri: Uri) {        runCatching {
            val root = File(filesDir, "workspace/project-" + System.currentTimeMillis()).apply { mkdirs() }
            var count = 0
            contentResolver.openInputStream(uri)!!.use { input ->
                ZipInputStream(input).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        val name = e.name
                        val junk = name.startsWith("__MACOSX/") || name == "__MACOSX" || name.endsWith(".DS_Store")
                        if (!junk) {
                            val out = File(root, name).canonicalFile
                            require(out.path.startsWith(root.canonicalPath + File.separator) || out == root) { "Unsafe ZIP path" }
                            if (e.isDirectory) out.mkdirs()
                            else { out.parentFile?.mkdirs(); out.outputStream().use { z.copyTo(it) }; count++ }
                        }
                        z.closeEntry()
                        e = z.nextEntry
                    }
                }
            }
            require(count > 0) { "ZIP contained no files" }
            val actual = detectProjectRoot(root)
            selectRoot(actual)
            val hasSettings = File(actual, "settings.gradle").isFile || File(actual, "settings.gradle.kts").isFile
            var hasWrapper = File(actual, "gradlew").isFile
            if (hasSettings && !hasWrapper) hasWrapper = generateGradleWrapper(actual)
            showImportSummary(actual, count, hasSettings, hasWrapper)
        }.onFailure { toast("Import failed: " + it.message) }
    }

    private fun showImportSummary(actual: File, count: Int, hasSettings: Boolean, hasWrapper: Boolean) {
        val tc = ToolchainManager(filesDir).inspect(actual)
        val msg = StringBuilder()
            .append("Files imported: ").append(count).append("\n")
            .append("Project root: ").append(actual.name).append("\n")
            .append("Gradle project: ").append(if (hasSettings) "yes" else "NO — settings.gradle not found").append("\n")
            .append("Gradle wrapper: ")
            .append(if (hasWrapper) "found" else if (hasSettings) "generated" else "n/a").append("\n")
            .append("Toolchain: ").append(if (tc.ready) "ready" else "NOT imported").toString()
        val dlg = AlertDialog.Builder(this).setTitle("Project imported")
            .setMessage(msg)
            .setPositiveButton("Open Build") { _, _ -> openBuildTab() }
            .setNegativeButton("Close", null)
        if (!tc.ready) dlg.setNeutralButton("Import toolchain") { _, _ -> promptToolchainImport() }
        dlg.show()
    }

    fun openBuildTab() {
        showTab(BuildFragment(), "build")
        b.bottomNav.selectedItemId = R.id.nav_build
    }

    fun toolchainSummary(): Pair<Boolean, String> {
        val c = ToolchainManager(filesDir).inspect(projectRoot)
        return c.ready to c.report
    }

    fun promptToolchainImport() {
        AlertDialog.Builder(this).setTitle("Import Toolchain ZIP")
            .setMessage("The toolchain ZIP provides the on-device build tools (import once, reused by every project):\n\n• jdk/ — JDK 17 (jdk/bin/java)\n• android-sdk/ — platforms/android-35/android.jar + build-tools/35.0.0/aapt2\n• gradle/ — Gradle distribution (optional, recommended)\n\nAbout 1 GB unpacked.")
            .setPositiveButton("Select ZIP") { _, _ -> launchToolchainImporter() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun detectProjectRoot(root: File): File {
        if (File(root, "settings.gradle").isFile || File(root, "settings.gradle.kts").isFile) return root
        val candidates = root.walkTopDown().maxDepth(4)
            .filter { it.isDirectory && it.name != "__MACOSX" && (File(it, "settings.gradle").isFile || File(it, "settings.gradle.kts").isFile) }
            .sortedBy { it.relativeTo(root).path.length }
            .toList()
        return candidates.firstOrNull() ?: root
    }

    fun selectRoot(r: File) {
        projectRoot = r
        prefs.edit().putString("root", r.path).apply()
        refreshExplorer()
    }

    fun refreshExplorer() {
        val r = projectRoot ?: return
        visibleFiles = r.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(r).path }.toList()
        explorerListener?.invoke()
    }

    fun requestOpenFile(f: File) {
        if (!dirty) return openFileInEditor(f)
        AlertDialog.Builder(this).setTitle("Unsaved changes")
            .setMessage("Save changes to " + (currentFile?.name ?: "current file") + " before opening " + f.name + "?")
            .setPositiveButton("Save") { _, _ -> saveCurrent(); openFileInEditor(f) }
            .setNegativeButton("Discard") { _, _ -> dirty = false; openFileInEditor(f) }
            .setNeutralButton("Cancel", null).show()
    }

    fun openFile(f: File) {
        if (f.length() > 2_000_000) return toast("File too large")
        loadingEditor = true
        currentFile = f
        pendingFile = f
        pendingTitle = f.name
        editorContent = runCatching { f.readText() }.getOrElse { "Cannot open as text" }
        openTabs[f.name] = editorContent
        dirty = false
        loadingEditor = false
        editorSyncListener?.invoke()
    }

    fun saveCurrent() {
        val f = currentFile ?: return toast("No file open")
        runCatching { f.writeText(editorContent) }
            .onSuccess { dirty = false; editorSyncListener?.invoke(); toast("Saved " + f.name) }
            .onFailure { toast("Save failed: " + it.message) }
    }

    fun newProject() {
        val input = EditText(this).apply { hint = "MyAwesomeApp" }
        AlertDialog.Builder(this).setTitle("New Android Project").setView(input)
            .setPositiveButton("Create") { _, _ -> createTemplate(input.text.toString().ifBlank { "MyAwesomeApp" }) }
            .setNegativeButton("Cancel", null).show()
    }

    fun createTemplate(name: String) {
        runCatching {
            val safe = name.replace(Regex("[^A-Za-z0-9_]"), "").ifBlank { "MyAwesomeApp" }
            val r = File(filesDir, "workspace/$safe").apply { mkdirs() }
            File(r, "settings.gradle.kts").writeText("pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\ndependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\nrootProject.name=\"$safe\"\ninclude(\":app\")")
            File(r, "build.gradle.kts").writeText("plugins { id(\"com.android.application\") version \"8.8.2\" apply false; id(\"org.jetbrains.kotlin.android\") version \"2.1.10\" apply false }")
            val app = File(r, "app").apply { mkdirs() }
            File(app, "build.gradle.kts").writeText("plugins { id(\"com.android.application\"); id(\"org.jetbrains.kotlin.android\") }\nandroid { namespace=\"com.example.app\"; compileSdk=35; defaultConfig { applicationId=\"com.example.app\"; minSdk=26; targetSdk=35; versionCode=1; versionName=\"1.0\" } }")
            val src = File(app, "src/main/java/com/example/app").apply { mkdirs() }
            File(src, "MainActivity.kt").writeText("package com.example.app\nimport android.app.Activity\nclass MainActivity:Activity()")
            generateGradleWrapper(r)
            selectRoot(r)
            toast("Project created")
        }.onFailure { toast("Create failed: " + it.message) }
    }

    fun exportZip() {
        val r = projectRoot ?: return toast("No project open")
        val out = File(cacheDir, r.name + ".zip")
        ZipOutputStream(out.outputStream()).use { z ->
            r.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(r).invariantSeparatorsPath))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        share(out, "application/zip", "Export project")
    }

    private fun share(f: File, type: String, title: String) {
        val u = FileProvider.getUriForFile(this, "$packageName.files", f)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            this.type = type; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, title))
    }

    fun latestApk(): File? {
        val r = projectRoot ?: return null
        val outDir = File(r, "app/build/outputs/apk")
        val fresh = outDir.takeIf { it.isDirectory }?.walkTopDown()
            ?.filter { it.isFile && it.extension.equals("apk", true) }
            ?.maxByOrNull { it.lastModified() }
        if (fresh != null) return fresh
        return r.walkTopDown().filter { it.isFile && it.extension.equals("apk", true) }
            ?.maxByOrNull { it.lastModified() }
    }

    fun installLatestApk() {
        val apk = latestApk() ?: return toast("No generated APK found")
        if (!packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val u = FileProvider.getUriForFile(this, "$packageName.files", apk)
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(u, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun importToolchain(uri: Uri) {
        showLog("Toolchain import", "Importing and validating toolchain package…")
        Thread {
            val result = ToolchainManager(filesDir).install(contentResolver, uri)
            runOnUiThread { showLog(if (result.success) "Toolchain ready" else "Toolchain import failed", result.report) }
        }.start()
    }

    fun buildAndRun() {
        val r = projectRoot ?: return toast("No project open")
        if (!File(r, "settings.gradle").isFile && !File(r, "settings.gradle.kts").isFile)
            return showLog("BUILD BLOCKED", "No settings.gradle/settings.gradle.kts found at project root. Re-import a complete Android/Gradle project.")
        val tc = ToolchainManager(filesDir)
        val check = tc.inspect(r)
        if (!check.ready) return showLog("Toolchain diagnostics", check.report + "\n\nImport a toolchain ZIP first: Build tab → Import Toolchain ZIP.")
        val env = tc.environment().toMutableMap()
        val aapt2 = tc.aapt2Path()
        // Resolve Gradle: project wrapper → toolchain Gradle → generate a bootstrap wrapper.
        val invoke: String
        val wrapper = File(r, "gradlew")
        val tcGradle = tc.gradleBin()
        if (wrapper.isFile) {
            if (tcGradle != null) env["DEVXYZ_GRADLE_BIN"] = tcGradle.absolutePath
            invoke = "chmod +x ./gradlew && ./gradlew"
        } else if (tcGradle != null) {
            invoke = "\"" + tcGradle.absolutePath + "\""
        } else {
            if (!generateGradleWrapper(r))
                return showLog("BUILD BLOCKED", "No Gradle wrapper in the project and no Gradle in the toolchain.\n\nFix: import a toolchain ZIP that contains gradle/, or add a gradlew wrapper to the project.")
            showLog("Gradle wrapper", "No gradlew found — generated a bootstrap wrapper.\nFirst build downloads Gradle 8.10.2 (~130 MB, needs internet).")
            invoke = "chmod +x ./gradlew && ./gradlew"
        }
        buildStartMs = System.currentTimeMillis()
        runCommand("$invoke --no-daemon -Pandroid.aapt2FromMavenOverride=\"$aapt2\" assembleDebug --stacktrace", r, env) { code, out ->
            lastBuildCode = code
            lastBuildLog = out
            if (code == 0) {
                refreshExplorer()
                lastBuildApk = latestApk()
            }
            buildListener?.invoke()
            if (code == 0) {
                val apk = lastBuildApk
                if (apk != null) showBuildSuccess(apk, out)
                else showLog("BUILD FINISHED", "Gradle succeeded but no APK was found.\n\n$out")
            } else showLog("BUILD FAILED", out)
        }
    }

    /** Writes a bootstrap `gradlew` (+ wrapper properties) into a project that lacks one. */
    fun generateGradleWrapper(r: File): Boolean = runCatching {
        File(r, "gradle/wrapper").apply { mkdirs() }
        File(r, "gradle/wrapper/gradle-wrapper.properties").writeText(
            "distributionBase=GRADLE_USER_HOME\n" +
                    "distributionPath=wrapper/dists\n" +
                    "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.10.2-bin.zip\n" +
                    "networkTimeout=10000\n" +
                    "validateDistributionUrl=true\n" +
                    "zipStoreBase=GRADLE_USER_HOME\n" +
                    "zipStorePath=wrapper/dists\n"
        )
        File(r, "gradlew").apply {
            writeText(GRADLEW_SCRIPT)
            setExecutable(true)
        }
        true
    }.getOrDefault(false)

    private fun showBuildSuccess(apk: File, log: String) {
        AlertDialog.Builder(this).setTitle("BUILD SUCCESSFUL")
            .setMessage("Generated APK:\n" + apk.relativeTo(projectRoot!!).path + "\n\nSize: " + apk.length() + " bytes")
            .setPositiveButton("Install APK") { _, _ -> installLatestApk() }
            .setNeutralButton("Build log") { _, _ -> showLog("Build log", log) }
            .setNegativeButton("Close", null).show()
    }

    fun terminal() {
        val input = EditText(this).apply { hint = "pwd, ls, find . -name '*.kt'" }
        AlertDialog.Builder(this).setTitle("Terminal").setView(input)
            .setPositiveButton("Run") { _, _ ->
                val r = projectRoot ?: filesDir
                runCommand(input.text.toString(), r) { code, out -> showLog("Terminal · exit $code", out) }
            }
            .setNegativeButton("Cancel", null).show()
    }

    fun gitMenu() {
        AlertDialog.Builder(this).setTitle("Git")
            .setItems(arrayOf("Status", "Clone repository", "Commit", "Push")) { _, w ->
                when (w) {
                    0 -> gitStatus()
                    1 -> gitClone()
                    2 -> gitCommit()
                    else -> gitPush()
                }
            }.show()
    }

    fun gitStatus() {
        val r = projectRoot ?: return toast("No project open")
        runCommand("git status --short --branch", r) { code, out ->
            showLog("Git · exit $code", if (out.isBlank()) "No output. Git may not be available in this Android runtime." else out)
        }
    }

    private fun gitClone() {
        val input = EditText(this).apply { hint = "https://github.com/user/repo.git" }
        AlertDialog.Builder(this).setTitle("Clone repository").setView(input)
            .setPositiveButton("Clone") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isEmpty()) return@setPositiveButton
                val dest = projectRoot?.parentFile ?: filesDir
                runCommand("git clone \"$url\"", dest) { code, out ->
                    showLog("Git clone · exit $code", out)
                    if (code == 0) refreshExplorer()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun gitCommit() {
        val r = projectRoot ?: return toast("No project open")
        val input = EditText(this).apply { hint = "Commit message" }
        AlertDialog.Builder(this).setTitle("Commit changes").setView(input)
            .setPositiveButton("Commit") { _, _ ->
                val msg = input.text.toString().ifBlank { "Update" }.replace("\"", "'")
                runCommand("git add -A && git commit -m \"$msg\"", r) { code, out -> showLog("Git commit · exit $code", out) }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun gitPush() {
        val r = projectRoot ?: return toast("No project open")
        runCommand("git push", r) { code, out -> showLog("Git push · exit $code", out) }
    }

    fun runCommand(cmd: String, dir: File, env: Map<String, String> = emptyMap(), done: (Int, String) -> Unit) {
        Thread {
            val result = runCatching {
                val pb = ProcessBuilder("/system/bin/sh", "-c", cmd).directory(dir).redirectErrorStream(true)
                pb.environment().putAll(env)
                val p = pb.start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor() to out
            }.getOrElse { -1 to ("Execution error: " + it.message) }
            runOnUiThread { done(result.first, result.second) }
        }.start()
    }

    fun showLog(title: String, text: String) {
        val tv = TextView(this).apply {
            setText(text.ifBlank { "(no output)" }); setTextIsSelectable(true)
            setPadding(28, 20, 28, 20); typeface = android.graphics.Typeface.MONOSPACE
        }
        AlertDialog.Builder(this).setTitle(title)
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("OK", null).show()
    }

    fun showTools() {
        AlertDialog.Builder(this).setTitle("Built-in Tools")
            .setItems(arrayOf("New Project", "Import ZIP", "Export / Backup ZIP", "Terminal", "Git Status", "Install Latest APK", "Import Toolchain ZIP", "Toolchain Diagnostics", "Project Settings")) { _, w ->
                when (w) {
                    0 -> newProject()
                    1 -> importer.launch(arrayOf("application/zip", "application/octet-stream"))
                    2 -> exportZip()
                    3 -> terminal()
                    4 -> gitStatus()
                    5 -> installLatestApk()
                    6 -> promptToolchainImport()
                    7 -> {
                        val r = projectRoot
                        showLog("Toolchain Diagnostics", ToolchainManager(filesDir).inspect(r).report)
                    }
                    else -> showLog("Project Settings", "Workspace: " + (projectRoot?.path ?: "None") + "\nJDK target: 17\nAndroid SDK target: 35")
                }
            }.show()
    }

    fun showSearch() {
        val input = EditText(this).apply { hint = "Search text…" }
        AlertDialog.Builder(this).setTitle("Search project").setView(input)
            .setPositiveButton("Search") { _, _ ->
                val hits = searchProject(input.text.toString())
                showLog("Search results", if (hits.isEmpty()) "No matches" else hits.joinToString("\n"))
            }.setNegativeButton("Cancel", null).show()
    }

    fun searchProject(q: String): List<File> {
        val r = projectRoot ?: return emptyList()
        if (q.isBlank()) return emptyList()
        return r.walkTopDown().filter { it.isFile && it.length() < 1_000_000 }
            .filter { runCatching { it.readText().contains(q, true) }.getOrDefault(false) }
            .take(50).toList()
    }

    // ---------- ToolsFragment handlers (all real) ----------

    fun openProjectFlow() {
        AlertDialog.Builder(this).setTitle("Open Project")
            .setMessage("DevxyzIDE works on an imported copy of your project. Import a ZIP to open it here.")
            .setPositiveButton("Import ZIP") { _, _ -> importer.launch(arrayOf("application/zip", "application/octet-stream")) }
            .setNegativeButton("Cancel", null).show()
    }

    fun projectSettingsDialog() {
        showLog("Project Settings",
            "Workspace: " + (projectRoot?.path ?: "None") +
                    "\nJDK target: 17\nAndroid SDK target: 35\nGradle: " + prefs.getString("gradle_version", "8.8"))
    }

    fun newFileDialog() {
        val r = projectRoot ?: return toast("Import or create a project first")
        val input = EditText(this).apply { hint = "FileName.java" }
        AlertDialog.Builder(this).setTitle("New file").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifBlank { return@setPositiveButton }
                val f = File(r, name)
                if (f.exists()) return@setPositiveButton toast("File exists")
                runCatching {
                    f.parentFile?.mkdirs(); f.createNewFile()
                    refreshExplorer(); openFileInEditor(f)
                }.onFailure { toast("Create failed: " + it.message) }
            }.setNegativeButton("Cancel", null).show()
    }

    fun pickApkToSign() {
        apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
    }

    private fun copyUriToCache(uri: Uri, name: String): File? = runCatching {
        val out = File(cacheDir, name)
        contentResolver.openInputStream(uri)!!.use { ins -> out.outputStream().use { ins.copyTo(it) } }
        out
    }.getOrNull()

    private fun signApkUri(uri: Uri) {
        val apk = copyUriToCache(uri, "sign-target.apk") ?: return toast("Cannot read APK")
        val signer = File(filesDir, "toolchain/android-sdk/build-tools/35.0.0/apksigner")
        if (!signer.isFile) {
            showLog("APK Signer", "apksigner was not found in the imported toolchain.\nImport a toolchain ZIP first (Tools → Import Toolchain).")
            return
        }
        val ks = File(filesDir, "debug.keystore")
        showLog("APK Signer", "Signing " + apk.name + " with debug key…")
        Thread {
            val env = ToolchainManager(filesDir).environment()
            if (!ks.isFile) {
                runCatching {
                    ProcessBuilder("/system/bin/sh", "-c",
                        "keytool -genkeypair -keystore '${ks.path}' -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 -storepass android -keypass android -dname 'CN=Android Debug'")
                        .apply { environment().putAll(env) }.start().waitFor()
                }
            }
            runCommand("'${signer.path}' sign --ks '${ks.path}' --ks-pass:android --key-pass:android --out '${apk.path}.signed' '${apk.path}'",
                filesDir, env) { code, out ->
                showLog(if (code == 0) "APK Signed" else "Sign failed",
                    if (code == 0) "Signed APK: ${apk.path}.signed" else out.ifBlank { "exit=$code" })
            }
        }.start()
    }

    fun aaptInfo() {
        val aapt = File(filesDir, "toolchain/android-sdk/build-tools/35.0.0/aapt2")
        if (!aapt.isFile) {
            showLog("AAPT / AAB", "aapt2 was not found in the imported toolchain.\nImport a toolchain ZIP first (Tools → Import Toolchain).")
            return
        }
        runCommand("'${aapt.path}' version", filesDir) { code, out ->
            showLog("AAPT2 version", out.ifBlank { "exit=$code" })
        }
    }

    fun dbViewer() {
        val r = projectRoot ?: return toast("No project open")
        val dbs = r.walkTopDown().filter {
            it.isFile && it.extension.lowercase() in listOf("db", "sqlite", "sqlite3", "db3")
        }.toList()
        if (dbs.isEmpty()) return showLog("Database Viewer", "No .db / .sqlite files found in the project.")
        AlertDialog.Builder(this).setTitle("Database Viewer")
            .setItems(dbs.map { it.relativeTo(r).path }.toTypedArray()) { _, w ->
                val db = dbs[w]
                try {
                    val sdb = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY)
                    val c = sdb.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", null)
                    val tables = mutableListOf<String>()
                    while (c.moveToNext()) tables += c.getString(0)
                    c.close(); sdb.close()
                    showLog("Tables in " + db.name, if (tables.isEmpty()) "(no tables)" else tables.joinToString("\n"))
                } catch (e: Exception) {
                    showLog("Database Viewer", "Cannot open " + db.name + ": " + e.message)
                }
            }.show()
    }

    fun layoutPreview() {
        val r = projectRoot ?: return toast("No project open")
        val layouts = r.walkTopDown().filter {
            it.isFile && it.extension.equals("xml", true) && it.path.contains("/res/layout/")
        }.toList()
        if (layouts.isEmpty()) return showLog("Layout Preview", "No layout XML files found under res/layout.")
        AlertDialog.Builder(this).setTitle("Layout Preview")
            .setItems(layouts.map { it.relativeTo(r).path }.toTypedArray()) { _, w ->
                val web = WebView(this).apply { loadDataWithBaseURL(null, xmlToHtml(layouts[w]), "text/html", "UTF-8", null) }
                AlertDialog.Builder(this).setTitle(layouts[w].name).setView(web)
                    .setPositiveButton("Close", null).show()
            }.show()
    }

    private fun xmlToHtml(file: File): String {
        val sb = StringBuilder("<html><body style='background:#050B12;color:#F4F8FC;font-family:sans-serif;padding:16px;margin:0'>")
        try {
            FileInputStream(file).use { fis ->
                val p = Xml.newPullParser()
                p.setInput(fis, "UTF-8")
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG) {
                        var label = p.name
                        for (i in 0 until p.attributeCount) {
                            val an = p.getAttributeName(i)
                            if (an == "text" || an.endsWith(":text")) label = p.getAttributeValue(i)
                        }
                        val safe = label.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        when (p.name) {
                            "TextView" -> sb.append("<p style='margin:8px 0'>").append(safe).append("</p>")
                            "Button" -> sb.append("<div style='background:#1E9FFF;color:#fff;padding:12px;border-radius:10px;text-align:center;margin:8px 0;font-weight:bold'>").append(safe).append("</div>")
                            "EditText" -> sb.append("<div style='border:1px solid #16364B;border-radius:10px;padding:12px;color:#8FA6B8;margin:8px 0'>").append(safe).append("</div>")
                            "ImageView" -> sb.append("<div style='background:#101E2B;border-radius:10px;height:90px;line-height:90px;text-align:center;color:#8FA6B8;margin:8px 0'>Image</div>")
                            "CheckBox", "Switch" -> sb.append("<p style='margin:8px 0'><input type='checkbox'> ").append(safe).append("</p>")
                        }
                    }
                    ev = p.next()
                }
            }
        } catch (e: Exception) {
            sb.append("<p>Preview error: ").append((e.message ?: "").replace("<", "&lt;")).append("</p>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    fun resourceManager() {
        val r = projectRoot ?: return toast("No project open")
        val resDir = r.walkTopDown().firstOrNull { it.isDirectory && it.name == "res" }
            ?: return showLog("Resource Manager", "No res/ directory found in the project.")
        val files = resDir.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(resDir).invariantSeparatorsPath to it }
            .sortedBy { it.first }.toList()
        if (files.isEmpty()) return showLog("Resource Manager", "res/ is empty.")
        AlertDialog.Builder(this).setTitle("Resource Manager")
            .setItems(files.map { it.first }.toTypedArray()) { _, w -> openFileInEditor(files[w].second) }
            .show()
    }

    fun colorPicker() {
        val presets = listOf("#12B8FF", "#1E9FFF", "#22C55E", "#F5A623", "#F87171", "#F4F8FC", "#8FA6B8", "#050B12", "#7C3AED", "#EC4899")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 24) }
        val grid = GridLayout(this).apply { columnCount = 5 }
        val preview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { topMargin = 24 }
            background = ColorDrawable(Color.parseColor("#12B8FF"))
        }
        val hex = EditText(this).apply { hint = "#12B8FF"; setText("#12B8FF") }
        var current = "#12B8FF"
        fun pick(c: String) {
            current = c
            hex.setText(c)
            preview.background = ColorDrawable(runCatching { Color.parseColor(c) }.getOrDefault(Color.BLACK))
        }
        presets.forEach { c ->
            val sw = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = 120; height = 120; setMargins(8, 8, 8, 8) }
                background = ColorDrawable(Color.parseColor(c))
                setOnClickListener { pick(c) }
            }
            grid.addView(sw)
        }
        root.addView(grid); root.addView(hex); root.addView(preview)
        AlertDialog.Builder(this).setTitle("Color Picker").setView(root)
            .setPositiveButton("Copy HEX") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("color", current))
                toast("Copied $current")
            }
            .setNegativeButton("Close", null).show()
    }

    fun openFolderView(dir: File) {
        val kids = dir.listFiles()?.sortedWith(compareBy({ it.isFile }, { it.name })) ?: emptyList()
        val rows = kids.map { (if (it.isDirectory) "[dir] " else "") + it.name + (if (it.isFile) "  (" + it.length() + " B)" else "") }
        AlertDialog.Builder(this).setTitle("Folder: " + dir.name)
            .setItems(rows.toTypedArray()) { _, _ -> }
            .setPositiveButton("Close", null).show()
    }

    // ---------- back ----------

    private fun doPopBack() {
        supportFragmentManager.popBackStack()
        b.bottomNav.visibility = View.VISIBLE
    }

    override fun onBackPressed() {
        val ed = supportFragmentManager.findFragmentByTag("editor")
        if (supportFragmentManager.backStackEntryCount > 0 && ed != null) {
            if (dirty) {
                AlertDialog.Builder(this).setTitle("Unsaved changes")
                    .setMessage("Save before leaving the editor?")
                    .setPositiveButton("Save") { _, _ -> saveCurrent(); doPopBack() }
                    .setNegativeButton("Discard") { _, _ -> dirty = false; doPopBack() }
                    .setNeutralButton("Cancel", null).show()
            } else doPopBack()
            return
        }
        if (dirty) {
            AlertDialog.Builder(this).setTitle("Unsaved changes")
                .setMessage("Save before leaving DevxyzIDE?")
                .setPositiveButton("Save") { _, _ -> saveCurrent(); super.onBackPressed() }
                .setNegativeButton("Discard") { _, _ -> dirty = false; super.onBackPressed() }
                .setNeutralButton("Cancel", null).show()
        } else super.onBackPressed()
    }

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        /** Bootstrap Gradle launcher written into projects that lack a wrapper. */
        private val GRADLEW_SCRIPT = """
            #!/bin/sh
            # Generated by DevxyzIDE - Gradle launcher for this project.
            # 1) Uses the imported toolchain Gradle when DEVXYZ_GRADLE_BIN is set.
            # 2) Otherwise downloads Gradle 8.10.2 on first run (needs internet + curl/wget + unzip).
            set -e
            if [ -n "${'$'}DEVXYZ_GRADLE_BIN" ] && [ -x "${'$'}DEVXYZ_GRADLE_BIN" ]; then
              exec "${'$'}DEVXYZ_GRADLE_BIN" "${'$'}@"
            fi
            VER="8.10.2"
            GUH="${'$'}{GRADLE_USER_HOME:-${'$'}HOME/.gradle}"
            DEST="${'$'}GUH/wrapper/dists/gradle-${'$'}VER-bin"
            BIN="${'$'}DEST/gradle-${'$'}VER/bin/gradle"
            if [ ! -x "${'$'}BIN" ]; then
              echo "DevxyzIDE: downloading Gradle ${'$'}VER (first build only)..."
              URL="https://services.gradle.org/distributions/gradle-${'$'}VER-bin.zip"
              mkdir -p "${'$'}DEST"
              TMP="${'$'}DEST/gradle-${'$'}VER-bin.zip"
              if command -v curl >/dev/null 2>&1; then
                curl -L --fail -o "${'$'}TMP" "${'$'}URL"
              elif command -v wget >/dev/null 2>&1; then
                wget -O "${'$'}TMP" "${'$'}URL"
              else
                echo "DevxyzIDE: no curl/wget on this device. Import a toolchain ZIP containing gradle/ instead." >&2
                exit 1
              fi
              if ! command -v unzip >/dev/null 2>&1; then
                echo "DevxyzIDE: no unzip on this device. Import a toolchain ZIP containing gradle/ instead." >&2
                exit 1
              fi
              unzip -q -o "${'$'}TMP" -d "${'$'}DEST" && rm -f "${'$'}TMP"
            fi
            exec "${'$'}BIN" "${'$'}@"
            """.trimIndent()
    }
}
