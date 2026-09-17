package com.jepongdevxyz.idebuild.core.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight dependency-free completion source for the on-device editor.
 * Suggestions come from language keywords, common Android/XML attributes,
 * and identifiers already present in the current document.
 *
 * This is intentionally lexical completion only. It does not claim semantic
 * type inference, SDK symbol resolution, or desktop-IDE language-server parity.
 */
public final class BasicCompletionService {
    private static final String[] JAVA_KEYWORDS = {
            "abstract","assert","boolean","break","byte","case","catch","char","class","continue",
            "default","do","double","else","enum","extends","final","finally","float","for","if",
            "implements","import","instanceof","int","interface","long","native","new","package","private",
            "protected","public","return","short","static","strictfp","super","switch","synchronized","this",
            "throw","throws","transient","try","void","volatile","while","true","false","null"
    };

    private static final String[] KOTLIN_KEYWORDS = {
            "as","break","class","continue","do","else","false","for","fun","if","in","interface","is",
            "null","object","package","return","super","this","throw","true","try","typealias","val","var",
            "when","while","by","catch","constructor","finally","get","import","init","set","where","actual",
            "abstract","annotation","companion","const","data","enum","expect","external","final","infix",
            "inline","inner","internal","lateinit","open","operator","out","override","private","protected",
            "public","reified","sealed","suspend","tailrec","vararg"
    };

    private static final String[] XML_ATTRIBUTES = {
            "android:id","android:layout_width","android:layout_height","android:layout_margin",
            "android:layout_marginStart","android:layout_marginEnd","android:layout_marginTop","android:layout_marginBottom",
            "android:padding","android:paddingStart","android:paddingEnd","android:paddingTop","android:paddingBottom",
            "android:text","android:textColor","android:textSize","android:background","android:gravity",
            "android:orientation","android:visibility","android:src","android:contentDescription",
            "android:clickable","android:enabled","android:inputType","android:hint","android:minWidth","android:minHeight"
    };

    private BasicCompletionService() { }

    public static List<String> complete(String text, int cursor, SyntaxLanguage language, int maxResults) {
        if (text == null || language == null || maxResults <= 0) return Collections.emptyList();
        int safeCursor = Math.max(0, Math.min(cursor, text.length()));
        String prefix = readPrefix(text, safeCursor);
        if (prefix.length() == 0) return Collections.emptyList();

        LinkedHashSet<String> candidates = new LinkedHashSet<String>();
        addLanguageCandidates(candidates, language);
        addDocumentIdentifiers(candidates, text);

        ArrayList<String> matches = new ArrayList<String>();
        String lowerPrefix = prefix.toLowerCase(Locale.US);
        for (String candidate : candidates) {
            if (candidate.length() <= prefix.length()) continue;
            if (candidate.toLowerCase(Locale.US).startsWith(lowerPrefix)) matches.add(candidate);
        }

        Collections.sort(matches, new Comparator<String>() {
            @Override public int compare(String left, String right) {
                int byLength = left.length() - right.length();
                if (byLength != 0) return byLength;
                return left.compareToIgnoreCase(right);
            }
        });

        if (matches.size() > maxResults) return new ArrayList<String>(matches.subList(0, maxResults));
        return matches;
    }

    public static String currentPrefix(String text, int cursor) {
        if (text == null) return "";
        int safeCursor = Math.max(0, Math.min(cursor, text.length()));
        return readPrefix(text, safeCursor);
    }

    private static void addLanguageCandidates(Set<String> output, SyntaxLanguage language) {
        if (language == SyntaxLanguage.JAVA) {
            addAll(output, JAVA_KEYWORDS);
        } else if (language == SyntaxLanguage.KOTLIN || language == SyntaxLanguage.GRADLE) {
            addAll(output, KOTLIN_KEYWORDS);
        } else if (language == SyntaxLanguage.XML) {
            addAll(output, XML_ATTRIBUTES);
        }
    }

    private static void addDocumentIdentifiers(Set<String> output, String text) {
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (isIdentifierStart(c)) {
                int start = i++;
                while (i < text.length() && isIdentifierPart(text.charAt(i))) i++;
                if (i - start >= 2) output.add(text.substring(start, i));
            } else {
                i++;
            }
        }
    }

    private static String readPrefix(String text, int cursor) {
        int start = cursor;
        while (start > 0 && isCompletionChar(text.charAt(start - 1))) start--;
        return text.substring(start, cursor);
    }

    private static boolean isCompletionChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == ':';
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static void addAll(Set<String> output, String[] values) {
        for (String value : values) output.add(value);
    }
}
