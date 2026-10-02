package com.jepongdevxyz.idebuild

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.jepongdevxyz.idebuild.databinding.FragmentEditorBinding
import java.io.File

/**
 * Code Editor — pixel-perfect per the reference poster:
 * back + filename + search/overflow app bar, file tabs with blue
 * underline, line-number gutter, syntax highlighting, bottom
 * Code/Terminal/Log/Problems bar and the big blue Play button.
 */
class EditorFragment : Fragment() {

    private var _b: FragmentEditorBinding? = null
    private val b get() = _b!!
    private val handler = Handler(Looper.getMainLooper())
    private val tabContents = mutableMapOf<String, String>()
    private var currentTab = ""
    private var altTab = ""

    private val highlightRun = Runnable {
        if (_b == null) return@Runnable
        SyntaxHighlighter.highlight(b.editor.text, currentTab)
    }

    private fun act() = requireActivity() as MainActivity

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentEditorBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        currentTab = act().pendingTitle.ifBlank { "MainActivity.java" }
        altTab = if (currentTab.endsWith(".xml", true)) "MainActivity.java" else "activity_main.xml"
        tabContents[currentTab] = act().editorContent
        tabContents[altTab] = loadAlternate()

        applyPrefs()
        // Tab labels follow the actual open file + its alternate.
        (b.tabJava as ViewGroup).getChildAt(0).let { (it as android.widget.TextView).text = currentTab }
        (b.tabXml as ViewGroup).getChildAt(0).let { (it as android.widget.TextView).text = altTab }
        selectTab(currentTab, initial = true)

        b.editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, bf: Int, c: Int) {
                if (!act().loadingEditor) {
                    tabContents[currentTab] = s.toString()
                    if (act().pendingTitle == currentTab) {
                        act().editorContent = s.toString()
                        act().dirty = true
                    } else {
                        act().dirty = true
                    }
                }
                updateLineNumbers()
            }
            override fun afterTextChanged(e: Editable?) {
                handler.removeCallbacks(highlightRun)
                handler.postDelayed(highlightRun, 350)
            }
        })

        b.btnBack.setOnClickListener { act().onBackPressed() }
        b.btnEditorSearch.setOnClickListener { findInFile() }
        b.btnEditorOverflow.setOnClickListener { editorMenu() }
        b.tabJava.setOnClickListener { selectTab(tabLabel(b.tabJava)) }
        b.tabXml.setOnClickListener { selectTab(tabLabel(b.tabXml)) }
        b.tabCode.setOnClickListener { /* already in code view */ }
        b.tabTerminal.setOnClickListener { act().terminal() }
        b.tabLog.setOnClickListener {
            act().showLog("Build log", act().lastBuildLog.ifBlank { "No build has run yet." })
        }
        b.tabProblems.setOnClickListener { showProblems() }
        b.fabPlay.setOnClickListener { act().buildAndRun() }
    }

    override fun onResume() {
        super.onResume()
        applyPrefs()
        act().editorSyncListener = {
            if (_b != null) {
                act().loadingEditor = true
                b.editor.setText(tabContents[currentTab] ?: "")
                b.editorTitle.text = currentTab + if (act().dirty) " •" else ""
                act().loadingEditor = false
            }
        }
    }

    override fun onPause() {
        act().editorSyncListener = null
        super.onPause()
    }

    private fun applyPrefs() {
        val p = act().prefs
        val size = p.getFloat("font_size", 14f)
        b.editor.textSize = size
        b.lineNumbers.textSize = size
        val wrap = p.getBoolean("word_wrap", false)
        b.editor.setHorizontallyScrolling(!wrap)
        val ac = p.getBoolean("autocomplete", true)
        b.editor.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                (if (ac) 0 else android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
    }

    private fun selectTab(name: String, initial: Boolean = false) {
        if (!initial && name == currentTab) return
        // persist current text
        tabContents[currentTab] = b.editor.text.toString()
        if (act().pendingTitle == currentTab) act().editorContent = b.editor.text.toString()
        currentTab = name
        act().loadingEditor = true
        b.editor.setText(tabContents[name] ?: "")
        try { b.editor.setSelection(0) } catch (_: Exception) {}
        act().loadingEditor = false
        b.editorTitle.text = name + if (act().dirty) " •" else ""
        refreshTabStyles(name)
        updateLineNumbers()
        handler.removeCallbacks(highlightRun)
        handler.post(highlightRun)
    }

    private fun tabLabel(tab: View): String =
        ((tab as ViewGroup).getChildAt(0) as android.widget.TextView).text.toString()

    private fun refreshTabStyles(selected: String) {
        styleTab(b.tabJava, tabLabel(b.tabJava) == selected)
        styleTab(b.tabXml, tabLabel(b.tabXml) == selected)
    }

    private fun styleTab(tab: View, selected: Boolean) {
        val tabGroup = tab as ViewGroup
        val tv = tabGroup.getChildAt(0) as android.widget.TextView
        val ul = tabGroup.getChildAt(1)
        tv.setTextColor(
            requireContext().getColor(if (selected) R.color.cyan else R.color.muted)
        )
        tv.paint.isFakeBoldText = selected
        ul.setBackgroundColor(
            requireContext().getColor(if (selected) R.color.cyan else android.R.color.transparent)
        )
    }

    private fun loadAlternate(): String {
        val alt = findAlternateFile()
        return if (alt != null) runCatching { alt.readText() }.getOrDefault("") else SampleSources.contentFor(altTab)
    }

    private fun findAlternateFile(): File? {
        val r = act().projectRoot ?: return null
        return r.walkTopDown().firstOrNull { it.isFile && it.name == altTab }
    }

    private fun updateLineNumbers() {
        val count = maxOf(1, b.editor.text.toString().split('\n').size)
        val sb = StringBuilder()
        for (i in 1..count) sb.append(i).append('\n')
        if (b.lineNumbers.text.toString() != sb.toString().trimEnd()) {
            b.lineNumbers.text = sb.toString().trimEnd()
        }
    }

    private fun findInFile() {
        val input = EditText(requireContext()).apply { hint = "Find…" }
        AlertDialog.Builder(requireContext()).setTitle("Find in file").setView(input)
            .setPositiveButton("Find") { _, _ ->
                val q = input.text.toString()
                if (q.isEmpty()) return@setPositiveButton
                val text = b.editor.text.toString()
                val idx = text.indexOf(q)
                if (idx < 0) act().toast("Not found")
                else {
                    b.editor.requestFocus()
                    b.editor.setSelection(idx, idx + q.length)
                    val count = text.split(q).size - 1
                    act().toast("$count match" + if (count == 1) "" else "es")
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun editorMenu() {
        AlertDialog.Builder(requireContext()).setTitle("Editor")
            .setItems(arrayOf("Save", "Find in file", "Close file")) { _, w ->
                when (w) {
                    0 -> {
                        tabContents[currentTab] = b.editor.text.toString()
                        if (act().pendingTitle == currentTab) act().editorContent = b.editor.text.toString()
                        act().saveCurrent()
                    }
                    1 -> findInFile()
                    else -> act().onBackPressed()
                }
            }.show()
    }

    private fun showProblems() {
        val text = b.editor.text.toString()
        val issues = mutableListOf<String>()
        text.lines().forEachIndexed { i, line ->
            if (line.contains("TODO")) issues += "Line ${i + 1}: TODO — ${line.trim()}"
            if (line.contains("FIXME")) issues += "Line ${i + 1}: FIXME — ${line.trim()}"
        }
        val opens = text.count { it == '{' }
        val closes = text.count { it == '}' }
        if (opens != closes) issues += "Brace mismatch: $opens '{' vs $closes '}'"
        act().showLog("Problems", if (issues.isEmpty()) "No problems found." else issues.joinToString("\n"))
    }

    override fun onDestroyView() {
        handler.removeCallbacks(highlightRun)
        _b = null
        super.onDestroyView()
    }
}
