package com.jepongdevxyz.idebuild

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jepongdevxyz.idebuild.databinding.FragmentSearchBinding
import java.io.File

/** Project-wide text search with real results; tap a result to open it. */
class SearchFragment : Fragment() {

    private var _b: FragmentSearchBinding? = null
    private val b get() = _b!!

    private fun act() = requireActivity() as MainActivity

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSearchBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        b.searchResults.layoutManager = LinearLayoutManager(requireContext())
        val go = {
            val q = b.searchInput.text.toString()
            val root = act().projectRoot
            if (root == null) {
                b.searchHint.text = "Import a project first to search its files."
                b.searchResults.adapter = null
            } else {
                val hits = act().searchProject(q)
                b.searchHint.text =
                    if (q.isBlank()) "Type something to search."
                    else if (hits.isEmpty()) "No matches for \"$q\"."
                    else {
                        val plural = if (hits.size == 1) "" else "es"
                        "${hits.size} match$plural for \"$q\"."
                    }
                b.searchResults.adapter = ResultAdapter(hits, root) { f -> act().requestOpenFile(f) }
            }
        }
        b.btnDoSearch.setOnClickListener { go() }
        b.searchInput.setOnEditorActionListener { _, _, _ -> go(); true }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    private class ResultAdapter(
        private val files: List<File>,
        private val root: File,
        private val onTap: (File) -> Unit
    ) : RecyclerView.Adapter<ResultAdapter.VH>() {
        inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val tv = TextView(p.context).apply {
                setPadding(32, 28, 32, 28)
                textSize = 14f
                setTextColor(context.getColor(R.color.text))
            }
            return VH(tv)
        }
        override fun getItemCount() = files.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val f = files[pos]
            h.tv.text = f.relativeTo(root).path
            h.tv.setOnClickListener { onTap(f) }
        }
    }
}
