package com.jepongdevxyz.idebuild;

import android.graphics.Color;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

import com.jepongdevxyz.idebuild.core.editor.SyntaxLanguage;
import com.jepongdevxyz.idebuild.core.editor.SyntaxLanguageService;
import com.jepongdevxyz.idebuild.core.editor.SyntaxSpan;

import java.util.List;

/**
 * Bounded Android-side syntax styling adapter for the dependency-free lexer.
 * Only spans created by this class are removed during a refresh, so selection,
 * IME/composing, and other framework spans are left alone.
 */
public final class EditorSyntaxStyler {
    static final int MAX_SYNTAX_CHARS = 65536;
    private static final int MAX_SYNTAX_SPANS = 4000;

    private EditorSyntaxStyler() { }

    public static void apply(EditText editor, String sourceHint) {
        if (editor == null) return;
        Editable editable = editor.getText();
        if (editable == null) return;

        int length = editable.length();
        if (length == 0) {
            removeOwnedSpans(editable, 0, 0);
            return;
        }

        int cursor = editor.getSelectionStart();
        if (cursor < 0) cursor = 0;
        if (cursor > length) cursor = length;

        int half = MAX_SYNTAX_CHARS / 2;
        int start = Math.max(0, cursor - half);
        int end = Math.min(length, start + MAX_SYNTAX_CHARS);
        if (end - start < MAX_SYNTAX_CHARS && end == length) {
            start = Math.max(0, end - MAX_SYNTAX_CHARS);
        }

        removeOwnedSpans(editable, start, end);

        CharSequence local = editable.subSequence(start, end);
        String localText = local.toString();
        SyntaxLanguage language = chooseLanguage(sourceHint, localText);
        List<SyntaxSpan> spans = SyntaxLanguageService.scan(
                localText,
                language,
                0,
                localText.length(),
                MAX_SYNTAX_CHARS,
                MAX_SYNTAX_SPANS);

        for (SyntaxSpan span : spans) {
            int absoluteStart = start + span.getStart();
            int absoluteEnd = start + span.getEnd();
            if (absoluteStart < 0 || absoluteEnd > editable.length() || absoluteEnd <= absoluteStart) continue;
            editable.setSpan(
                    new SyntaxColorSpan(colorFor(span.getKind())),
                    absoluteStart,
                    absoluteEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void removeOwnedSpans(Editable editable, int start, int end) {
        int safeEnd = Math.max(start, end);
        SyntaxColorSpan[] existing = editable.getSpans(start, safeEnd, SyntaxColorSpan.class);
        for (SyntaxColorSpan span : existing) editable.removeSpan(span);
    }

    private static SyntaxLanguage chooseLanguage(String sourceHint, String text) {
        if (sourceHint != null && sourceHint.trim().length() > 0) {
            SyntaxLanguage detected = SyntaxLanguageService.detect(sourceHint);
            if (detected != SyntaxLanguage.PLAIN_TEXT) return detected;
        }

        String sample = text == null ? "" : text.trim();
        if (sample.startsWith("<")) return SyntaxLanguage.XML;
        if (sample.startsWith("{") || sample.startsWith("[")) return SyntaxLanguage.JSON;
        if (containsAny(sample, "fun ", " val ", " var ", "data class ", "object ")) return SyntaxLanguage.KOTLIN;
        if (containsAny(sample, "plugins {", "android {", "dependencies {")) return SyntaxLanguage.GRADLE;
        if (containsAny(sample, "function ", "const ", "let ", "=>")) return SyntaxLanguage.JAVASCRIPT;
        return SyntaxLanguage.JAVA;
    }

    private static boolean containsAny(String text, String a, String b, String c, String d, String e) {
        return text.indexOf(a) >= 0 || text.indexOf(b) >= 0 || text.indexOf(c) >= 0 || text.indexOf(d) >= 0 || text.indexOf(e) >= 0;
    }

    private static boolean containsAny(String text, String a, String b, String c) {
        return text.indexOf(a) >= 0 || text.indexOf(b) >= 0 || text.indexOf(c) >= 0;
    }

    private static boolean containsAny(String text, String a, String b, String c, String d) {
        return text.indexOf(a) >= 0 || text.indexOf(b) >= 0 || text.indexOf(c) >= 0 || text.indexOf(d) >= 0;
    }

    private static int colorFor(String kind) {
        if (SyntaxSpan.COMMENT.equals(kind)) return Color.rgb(106, 153, 85);
        if (SyntaxSpan.STRING.equals(kind)) return Color.rgb(206, 145, 120);
        if (SyntaxSpan.NUMBER.equals(kind)) return Color.rgb(181, 206, 168);
        if (SyntaxSpan.TAG.equals(kind)) return Color.rgb(86, 156, 214);
        if (SyntaxSpan.ATTRIBUTE.equals(kind)) return Color.rgb(156, 220, 254);
        return Color.rgb(197, 134, 192);
    }

    /** Marker span type so a refresh never removes spans owned by Android/IME. */
    static final class SyntaxColorSpan extends ForegroundColorSpan {
        SyntaxColorSpan(int color) { super(color); }
    }
}
