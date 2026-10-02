package com.jepongdevxyz.idebuild

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jepongdevxyz.idebuild.databinding.ItemTreeNodeBinding
import java.io.File

data class TreeNode(
    val name: String,
    val isDir: Boolean,
    val file: File?,
    val children: MutableList<TreeNode> = mutableListOf(),
    var expanded: Boolean = false,
    val depth: Int = 0,
    var selected: Boolean = false
)

class ProjectTreeAdapter(
    private var roots: List<TreeNode>,
    private val onDirToggle: (TreeNode) -> Unit,
    private val onFileTap: (TreeNode) -> Unit
) : RecyclerView.Adapter<ProjectTreeAdapter.VH>() {

    private val flat = mutableListOf<TreeNode>()

    init { rebuild() }

    fun setRoots(r: List<TreeNode>) {
        roots = r
        rebuild()
    }

    private fun rebuild() {
        flat.clear()
        roots.forEach { walk(it) }
        notifyDataSetChanged()
    }

    private fun walk(n: TreeNode) {
        flat += n
        if (n.isDir && n.expanded) n.children.forEach { walk(it) }
    }

    fun refresh() = rebuild()

    inner class VH(val b: ItemTreeNodeBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, v: Int): VH =
        VH(ItemTreeNodeBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun getItemCount(): Int = flat.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val n = flat[pos]
        val b = h.b
        val density = b.root.resources.displayMetrics.density
        b.nodeIndent.layoutParams.width = (n.depth * 18 * density).toInt()
        b.nodeIndent.requestLayout()
        b.nodeName.text = n.name
        if (n.isDir) {
            b.nodeIcon.setImageResource(if (n.expanded) R.drawable.ic_folder_open else R.drawable.ic_folder)
            b.nodeChevron.visibility = View.VISIBLE
            b.nodeChevron.setImageResource(
                if (n.expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right
            )
        } else {
            val ext = n.name.substringAfterLast('.', "").lowercase()
            b.nodeIcon.setImageResource(
                if (ext in listOf("java", "kt", "xml", "gradle", "kts")) R.drawable.ic_file_code else R.drawable.ic_file
            )
            b.nodeChevron.visibility = View.INVISIBLE
        }
        b.nodeRow.setBackgroundColor(
            if (n.selected) b.root.context.getColor(R.color.row_selected)
            else b.root.context.getColor(android.R.color.transparent)
        )
        b.root.setOnClickListener {
            if (n.isDir) {
                n.expanded = !n.expanded
                onDirToggle(n)
                refresh()
            } else {
                flat.forEach { it.selected = false }
                n.selected = true
                notifyDataSetChanged()
                onFileTap(n)
            }
        }
    }
}
