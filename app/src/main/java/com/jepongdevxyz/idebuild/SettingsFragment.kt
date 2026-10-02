package com.jepongdevxyz.idebuild

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.jepongdevxyz.idebuild.databinding.FragmentSettingsBinding

/**
 * Settings — pixel-perfect per the reference poster:
 * theme sun/moon toggles, Editor / Build / App sections.
 * Every control is real and persisted in SharedPreferences.
 */
class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    private fun act() = requireActivity() as MainActivity
    private fun prefs() = act().prefs

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val p = prefs()

        // --- Editor: font size ---
        val size = p.getFloat("font_size", 14f).toInt().coerceIn(10, 24)
        b.seekFontsize.progress = size - 10
        b.tvFontsizeValue.text = size.toString()
        b.seekFontsize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, pr: Int, fromUser: Boolean) {
                val v = pr + 10
                b.tvFontsizeValue.text = v.toString()
                if (fromUser) p.edit().putFloat("font_size", v.toFloat()).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // --- Spinners ---
        setupSpinner(b.spinnerTheme, R.array.theme_options, "theme", "Dark (Default)") { v ->
            act().applyTheme(if (v.startsWith("Light")) "light" else "dark")
        }
        setupSpinner(b.spinnerGradle, R.array.gradle_versions, "gradle_version", "8.8") {}
        setupSpinner(b.spinnerJdk, R.array.jdk_options, "jdk", "Embedded (17)") {}
        setupSpinner(b.spinnerSdk, R.array.sdk_options, "sdk", "Built-in") {}
        setupSpinner(b.spinnerLanguage, R.array.language_options, "language", "English") {}

        // --- Switches ---
        b.switchWordwrap.isChecked = p.getBoolean("word_wrap", false)
        b.switchWordwrap.setOnCheckedChangeListener { _, on ->
            p.edit().putBoolean("word_wrap", on).apply()
        }
        b.switchAutocomplete.isChecked = p.getBoolean("autocomplete", true)
        b.switchAutocomplete.setOnCheckedChangeListener { _, on ->
            p.edit().putBoolean("autocomplete", on).apply()
        }
        b.switchBackup.isChecked = p.getBoolean("backup", false)
        b.switchBackup.setOnCheckedChangeListener { _, on ->
            p.edit().putBoolean("backup", on).apply()
            if (on) act().exportZip() // real backup the moment it is enabled
        }

        // --- Sun / moon theme toggles ---
        b.btnSun.setOnClickListener { act().applyTheme("light") }
        b.btnMoon.setOnClickListener { act().applyTheme("dark") }
    }

    private fun setupSpinner(
        spinner: Spinner,
        arrayRes: Int,
        prefKey: String,
        defaultLabel: String,
        onPick: (String) -> Unit
    ) {
        val items = resources.getStringArray(arrayRes).toList()
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, items)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinner.adapter = adapter
        val saved = prefs().getString(prefKey, defaultLabel) ?: defaultLabel
        val startAt = items.indexOfFirst { it == saved || it.startsWith(saved) }.coerceAtLeast(0)
        var initialized = false
        spinner.setSelection(startAt, false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!initialized) { initialized = true; return }
                val value = items[pos]
                prefs().edit().putString(prefKey, value).apply()
                onPick(value)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
