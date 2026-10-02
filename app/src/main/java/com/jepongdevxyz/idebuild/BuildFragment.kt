package com.jepongdevxyz.idebuild

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.jepongdevxyz.idebuild.databinding.FragmentBuildBinding
import com.jepongdevxyz.idebuild.databinding.ItemTaskBinding

/**
 * Build & Run — pixel-perfect per the reference poster:
 * Gradle task list, build status, APK success card,
 * Install APK / Open Folder actions. All wired to the real
 * Gradle build in MainActivity.
 */
class BuildFragment : Fragment() {

    private var _b: FragmentBuildBinding? = null
    private val b get() = _b!!
    private val taskBindings = mutableListOf<ItemTaskBinding>()

    private fun act() = requireActivity() as MainActivity

    private val tasks = listOf(
        "> Task :app:compileDebugJavaWithJavac" to "Compiles Java/Kotlin sources with the embedded JDK 17 toolchain.",
        "> Task :app:mergeDebugResources" to "Merges res/ resources via aapt2 from the imported toolchain.",
        "> Task :app:packageDebug" to "Packages classes, resources and the manifest.",
        "> Task :app:assembleDebug" to "Assembles the final app-debug.apk."
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentBuildBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        tasks.forEach { (name, detail) ->
            val tb = ItemTaskBinding.inflate(layoutInflater, b.taskRows, true)
            tb.taskName.text = name
            tb.taskDetail.text = detail
            tb.taskHeader.setOnClickListener {
                val open = tb.taskDetail.visibility == View.VISIBLE
                tb.taskDetail.visibility = if (open) View.GONE else View.VISIBLE
                tb.taskChevron.setImageResource(
                    if (open) R.drawable.ic_chevron_right else R.drawable.ic_chevron_down
                )
            }
            taskBindings += tb
        }

        b.btnRunBuild.setOnClickListener { act().buildAndRun() }
        b.btnInstallApk.setOnClickListener { act().installLatestApk() }
        b.btnOpenFolder.setOnClickListener {
            val dir = act().lastBuildApk?.parentFile ?: act().projectRoot
            if (dir != null) act().openFolderView(dir) else act().toast("No project open")
        }

        renderBuildState()
    }

    override fun onResume() {
        super.onResume()
        act().buildListener = { if (_b != null) renderBuildState() }
        if (_b != null) renderBuildState()
    }

    override fun onPause() {
        act().buildListener = null
        super.onPause()
    }

    private fun renderBuildState() {
        val code = act().lastBuildCode
        if (code == null) {
            b.buildStatus.text = "No build has run yet"
            b.buildStatus.setTextColor(requireContext().getColor(R.color.muted))
            b.buildSummary.text = "Tap “Run Build” to compile the current project."
            b.successCard.visibility = View.GONE
            return
        }
        val secs = ((System.currentTimeMillis() - act().buildStartMs) / 1000).coerceAtLeast(1)
        if (code == 0) {
            b.buildStatus.text = "BUILD SUCCESSFUL in ${secs}s"
            b.buildStatus.setTextColor(requireContext().getColor(R.color.green))
            val taskLine = Regex("(\\d+) actionable tasks?: (\\d+) executed")
                .find(act().lastBuildLog)?.value ?: "Gradle build finished"
            b.buildSummary.text = taskLine
            val apk = act().lastBuildApk
            if (apk != null) {
                b.successCard.visibility = View.VISIBLE
                b.successPath.text = apk.absolutePath
            } else {
                b.successCard.visibility = View.GONE
                b.buildSummary.text = "Gradle succeeded but no APK was found."
            }
            // Show the real log tail under the last task row.
            if (taskBindings.isNotEmpty()) {
                taskBindings.last().taskDetail.text =
                    act().lastBuildLog.lines().takeLast(6).joinToString("\n").ifBlank { "Done." }
            }
        } else {
            b.buildStatus.text = "BUILD FAILED"
            b.buildStatus.setTextColor(requireContext().getColor(R.color.red))
            b.buildSummary.text = "See the build log for details."
            b.successCard.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
