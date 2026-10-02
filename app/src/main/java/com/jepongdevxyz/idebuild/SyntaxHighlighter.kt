package com.jepongdevxyz.idebuild

import android.graphics.Color
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

/**
 * Lightweight real syntax highlighting for the code editor.
 * Supports Java/Kotlin, XML and Gradle; applied as spans so the
 * underlying text is never modified.
 */
object SyntaxHighlighter {

    private val KW_COLOR = Color.parseColor("#5EB7FF")
    private val STR_COLOR = Color.parseColor("#7EE787")
    private val COM_COLOR = Color.parseColor("#6B7F90")
    private val NUM_COLOR = Color.parseColor("#F5A623")
    private val ANN_COLOR = Color.parseColor("#F5A623")

    private val JAVA_KT_KEYWORDS = Pattern.compile(
        "\\b(package|import|public|private|protected|class|interface|object|extends|implements|" +
                "fun|val|var|return|if|else|for|while|do|switch|case|break|continue|new|this|super|" +
                "static|final|void|int|long|boolean|String|override|lateinit|companion|data|sealed|" +
                "try|catch|finally|throw|throws|true|false|null|in|is|as|when|by|const|inner|open|" +
                "abstract|enum|typeof|sizeof|using|namespace|include|plugins|android|dependencies)\\b"
    )
    private val XML_TAG = Pattern.compile("</?[A-Za-z0-9_.]+|/?>")
    private val XML_ATTR = Pattern.compile("[A-Za-z0-9_.-:]+(?==)")
    private val STRING = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'")
    private val COMMENT = Pattern.compile("//[^\n]*|/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->")
    private val NUMBER = Pattern.compile("\\b\\d[\\d_]*(\\.\\d+)?\\b")
    private val ANNOTATION = Pattern.compile("@[A-Za-z_][A-Za-z0-9_]*")

    fun highlight(sp: Spannable, fileName: String) {
        // Clear old spans
        sp.getSpans(0, sp.length, ForegroundColorSpan::class.java).forEach { sp.removeSpan(it) }
        if (sp.isEmpty()) return
        val ext = fileName.substringAfterLast('.', "").lowercase()

        // Comments first (so keywords inside comments are not recolored)
        match(COMMENT, sp) { s, e -> sp.setSpan(ForegroundColorSpan(COM_COLOR), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        match(STRING, sp) { s, e -> sp.setSpan(ForegroundColorSpan(STR_COLOR), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        match(NUMBER, sp) { s, e -> sp.setSpan(ForegroundColorSpan(NUM_COLOR), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        match(ANNOTATION, sp) { s, e -> sp.setSpan(ForegroundColorSpan(ANN_COLOR), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }

        if (ext == "xml") {
            match(XML_TAG, sp) { s, e -> sp.setSpan(ForegroundColorSpan(KW_COLOR), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
            match(XML_ATTR, sp) { s, e -> sp.setSpan(ForegroundColorSpan(ANN_COLOR), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        } else {
            matchSkipping(JAVA_KT_KEYWORDS, sp) { s, e ->
                sp.setSpan(ForegroundColorSpan(KW_COLOR), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun match(p: Pattern, sp: Spannable, apply: (Int, Int) -> Unit) {
        val m = p.matcher(sp)
        while (m.find()) apply(m.start(), m.end())
    }

    /** Applies keyword spans only where no color span exists yet (keeps comments/strings intact). */
    private fun matchSkipping(p: Pattern, sp: Spannable, apply: (Int, Int) -> Unit) {
        val m = p.matcher(sp)
        while (m.find()) {
            val existing = sp.getSpans(m.start(), m.end(), ForegroundColorSpan::class.java)
            if (existing.isEmpty()) apply(m.start(), m.end())
        }
    }
}
