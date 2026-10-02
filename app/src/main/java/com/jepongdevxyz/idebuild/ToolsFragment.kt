package com.jepongdevxyz.idebuild

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.fragment.app.Fragment
import com.jepongdevxyz.idebuild.databinding.FragmentToolsBinding
import com.jepongdevxyz.idebuild.databinding.ItemToolBinding

/**
 * Built-in Tools — pixel-perfect per the reference poster:
 * Project Tools / Utilities / Extras grids, every tile wired
 * to a real handler in MainActivity.
 */
class ToolsFragment : Fragment() {

    private var _b: FragmentToolsBinding? = null
    private val b get() = _b!!

    private fun act() = requireActivity() as MainActivity

    private data class Tool(val label: String, val icon: Int, val action: () -> Unit)

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentToolsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val a = act()
        fill(b.toolsProjectGrid, listOf(
            Tool("New Project", R.drawable.ic_plus) { a.newProject() },
            Tool("Open Project", R.drawable.ic_folder) { a.openProjectFlow() },
            Tool("Import Project", R.drawable.ic_download) { a.launchImporter() },
            Tool("Project Settings", R.drawable.ic_settings) { a.projectSettingsDialog() }
        ))
        fill(b.toolsUtilGrid, listOf(
            Tool("Terminal", R.drawable.ic_terminal) { a.terminal() },
            Tool("Git", R.drawable.ic_git) { a.gitMenu() },
            Tool("APK Signer", R.drawable.ic_key) { a.pickApkToSign() },
            Tool("AAPT/AAB", R.drawable.ic_box) { a.aaptInfo() }
        ))
        fill(b.toolsExtrasGrid, listOf(
            Tool("Database Viewer", R.drawable.ic_database) { a.dbViewer() },
            Tool("Layout Preview", R.drawable.ic_layout) { a.layoutPreview() },
            Tool("Resource Manager", R.drawable.ic_shapes) { a.resourceManager() },
            Tool("Color Picker", R.drawable.ic_palette) { a.colorPicker() }
        ))
    }

    private fun fill(grid: GridLayout, tools: List<Tool>) {
        grid.removeAllViews()
        tools.forEach { t ->
            val ib = ItemToolBinding.inflate(layoutInflater, grid, false)
            ib.toolIcon.setImageResource(t.icon)
            ib.toolLabel.text = t.label
            ib.root.setOnClickListener { t.action() }
            grid.addView(ib.root)
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
