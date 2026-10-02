package com.jepongdevxyz.idebuild

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.jepongdevxyz.idebuild.databinding.FragmentProjectsBinding
import java.io.File

/**
 * Project Explorer — pixel-perfect per the reference poster:
 * "DevxyzIDE" app bar with search + new-file actions, "Projects"
 * label row, expandable file tree, blue "+" FAB.
 */
class ProjectsFragment : Fragment() {

    private var _b: FragmentProjectsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: ProjectTreeAdapter

    private fun act() = requireActivity() as MainActivity

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentProjectsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        adapter = ProjectTreeAdapter(buildTree(), onDirToggle = {}, onFileTap = { node ->
            val f = node.file
            if (f != null) act().requestOpenFile(f)
            else act().openSampleFile(node.name, SampleSources.contentFor(node.name))
        })
        b.treeList.layoutManager = LinearLayoutManager(requireContext())
        b.treeList.adapter = adapter

        b.btnSearch.setOnClickListener { act().showSearch() }
        b.btnNewDoc.setOnClickListener { act().newFileDialog() }
        b.btnOverflow.setOnClickListener { overflowMenu() }
        b.fabAdd.setOnClickListener { act().newProject() }
    }

    override fun onResume() {
        super.onResume()
        act().explorerListener = { refreshTree() }
        refreshTree()
    }

    override fun onPause() {
        if (act().explorerListener != null) act().explorerListener = null
        super.onPause()
    }

    private fun refreshTree() {
        if (_b == null) return
        adapter.setRoots(buildTree())
    }

    private fun buildTree(): List<TreeNode> {
        val root = act().projectRoot
        return if (root != null && root.isDirectory) buildRealTree(root) else buildSampleTree()
    }

    private fun buildRealTree(root: File): List<TreeNode> {
        fun walk(dir: File, depth: Int): TreeNode {
            val node = TreeNode(dir.name, true, dir, depth = depth, expanded = depth < 2)
            val kids = dir.listFiles()
                ?.sortedWith(compareBy({ it.isFile }, { it.name.lowercase() }))
                ?: emptyList()
            kids.take(500).forEach { k ->
                if (k.isDirectory) node.children += walk(k, depth + 1)
                else node.children += TreeNode(k.name, false, k, depth = depth + 1)
            }
            return node
        }
        return listOf(walk(root, 0))
    }

    private fun buildSampleTree(): List<TreeNode> {
        fun dir(name: String, depth: Int, vararg kids: TreeNode) =
            TreeNode(name, true, null, kids.toMutableList(), expanded = true, depth = depth)
        fun file(name: String, depth: Int, selected: Boolean = false) =
            TreeNode(name, false, null, depth = depth, selected = selected)
        val pkg = dir("com.example.app", 5, file("MainActivity.java", 6, selected = true))
        val java = dir("java", 4, pkg)
        val main = dir("main", 3, java, file("activity_main.xml", 4), dir("res", 4))
        val src = dir("src", 2, main)
        val app = dir("app", 1, src, file("AndroidManifest.xml", 2), file("build.gradle", 2))
        val root = dir(
            "MyAwesomeApp", 0, app,
            file("gradle.properties", 1), file("settings.gradle", 1),
            dir("gradle", 1), dir("build", 1)
        )
        return listOf(root)
    }

    private fun overflowMenu() {
        AlertDialog.Builder(requireContext()).setTitle("Projects")
            .setItems(arrayOf("Import ZIP", "New Project", "Export / Backup ZIP", "Refresh")) { _, w ->
                when (w) {
                    0 -> act().launchImporter()
                    1 -> act().newProject()
                    2 -> act().exportZip()
                    else -> act().refreshExplorer()
                }
            }.show()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}

/** Sample file contents shown when no real project is imported yet. */
object SampleSources {
    fun contentFor(name: String): String = when {
        name.endsWith(".java") || name.endsWith(".kt") -> SAMPLE_JAVA
        name.endsWith(".xml") -> SAMPLE_XML
        name.contains("gradle", true) -> SAMPLE_GRADLE
        else -> "// $name\n// Import a project ZIP to edit real files.\n"
    }

    const val SAMPLE_JAVA = """package com.example.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toast.makeText(this, "Hello DevxyzIDE!",
            Toast.LENGTH_SHORT).show();
    }
}
"""

    const val SAMPLE_XML = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello DevxyzIDE!" />

    <Button
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Tap me" />

</LinearLayout>
"""

    const val SAMPLE_GRADLE = """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
"""
}
