package com.jepongdevxyz.idebuild.core.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Small dependency-free lexer used by the mobile editor for bounded syntax
 * highlighting. It intentionally provides lexical coloring only; it is not a
 * compiler or semantic language server.
 */
public final class SyntaxLanguageService {
    private static final Set<String> JAVA_KEYWORDS = words(
            "abstract","assert","boolean","break","byte","case","catch","char","class","const","continue",
            "default","do","double","else","enum","extends","final","finally","float","for","goto","if",
            "implements","import","instanceof","int","interface","long","native","new","package","private",
            "protected","public","return","short","static","strictfp","super","switch","synchronized","this",
            "throw","throws","transient","try","void","volatile","while","true","false","null");

    private static final Set<String> KOTLIN_KEYWORDS = words(
            "as","break","class","continue","do","else","false","for","fun","if","in","interface","is",
            "null","object","package","return","super","this","throw","true","try","typealias","typeof",
            "val","var","when","while","by","catch","constructor","delegate","dynamic","field","file",
            "finally","get","import","init","param","property","receiver","set","setparam","where","actual",
            "abstract","annotation","companion","const","crossinline","data","enum","expect","external",
            "final","infix","inline","inner","internal","lateinit","noinline","open","operator","out",
            "override","private","protected","public","reified","sealed","suspend","tailrec","vararg");

    private static final Set<String> SCRIPT_KEYWORDS = words(
            "break","case","catch","class","const","continue","debugger","default","delete","do","else",
            "export","extends","false","finally","for","function","if","import","in","instanceof","let",
            "new","null","return","super","switch","this","throw","true","try","typeof","var","void",
            "while","with","yield","async","await","static");

    private static final Set<String> JSON_KEYWORDS = words("true","false","null");

    private SyntaxLanguageService() { }

    public static SyntaxLanguage detect(String path) {
        if (path == null) return SyntaxLanguage.PLAIN_TEXT;
        String lower = path.toLowerCase(Locale.US);
        if (lower.endsWith(".gradle.kts") || lower.endsWith(".gradle") || lower.endsWith(".kts")) return SyntaxLanguage.GRADLE;
        if (lower.endsWith(".java")) return SyntaxLanguage.JAVA;
        if (lower.endsWith(".kt")) return SyntaxLanguage.KOTLIN;
        if (lower.endsWith(".xml")) return SyntaxLanguage.XML;
        if (lower.endsWith(".json")) return SyntaxLanguage.JSON;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return SyntaxLanguage.HTML;
        if (lower.endsWith(".css")) return SyntaxLanguage.CSS;
        if (lower.endsWith(".js")) return SyntaxLanguage.JAVASCRIPT;
        if (lower.endsWith(".ts")) return SyntaxLanguage.TYPESCRIPT;
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return SyntaxLanguage.MARKDOWN;
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return SyntaxLanguage.YAML;
        if (lower.endsWith(".properties")) return SyntaxLanguage.PROPERTIES;
        return SyntaxLanguage.PLAIN_TEXT;
    }

    public static List<SyntaxSpan> scan(String text,
                                        SyntaxLanguage language,
                                        int requestedStart,
                                        int requestedEnd,
                                        int maxChars,
                                        int maxSpans) {
        ArrayList<SyntaxSpan> spans = new ArrayList<SyntaxSpan>();
        if (text == null || text.length() == 0 || language == null || maxChars <= 0 || maxSpans <= 0) return spans;

        int start = clamp(requestedStart, 0, text.length());
        int end = clamp(requestedEnd, start, text.length());
        long bounded = (long) start + (long) maxChars;
        int limit = bounded > Integer.MAX_VALUE ? end : Math.min(end, (int) bounded);
        if (start >= limit) return spans;

        if (language == SyntaxLanguage.XML || language == SyntaxLanguage.HTML) {
            scanXml(text, start, limit, maxSpans, spans);
        } else if (language == SyntaxLanguage.JAVA) {
            scanCode(text, start, limit, maxSpans, JAVA_KEYWORDS, spans);
        } else if (language == SyntaxLanguage.KOTLIN || language == SyntaxLanguage.GRADLE) {
            scanCode(text, start, limit, maxSpans, KOTLIN_KEYWORDS, spans);
        } else if (language == SyntaxLanguage.JAVASCRIPT || language == SyntaxLanguage.TYPESCRIPT) {
            scanCode(text, start, limit, maxSpans, SCRIPT_KEYWORDS, spans);
        } else if (language == SyntaxLanguage.JSON) {
            scanCode(text, start, limit, maxSpans, JSON_KEYWORDS, spans);
        }
        return spans;
    }

    private static void scanCode(String text,
                                 int start,
                                 int limit,
                                 int maxSpans,
                                 Set<String> keywords,
                                 List<SyntaxSpan> spans) {
        int i = start;
        while (i < limit && spans.size() < maxSpans) {
            char c = text.charAt(i);

            if (c == '/' && i + 1 < limit && text.charAt(i + 1) == '/') {
                int end = i + 2;
                while (end < limit && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
                add(spans, i, end, SyntaxSpan.COMMENT, maxSpans);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < limit && text.charAt(i + 1) == '*') {
                int end = i + 2;
                while (end + 1 < limit && !(text.charAt(end) == '*' && text.charAt(end + 1) == '/')) end++;
                if (end + 1 < limit) end += 2; else end = limit;
                add(spans, i, end, SyntaxSpan.COMMENT, maxSpans);
                i = end;
                continue;
            }
            if (c == '"' || c == '\'') {
                int end = scanQuoted(text, i, limit, c);
                add(spans, i, end, SyntaxSpan.STRING, maxSpans);
                i = end;
                continue;
            }
            if (Character.isDigit(c)) {
                int end = i + 1;
                while (end < limit) {
                    char n = text.charAt(end);
                    if (!(Character.isDigit(n) || n == '.' || n == '_' || Character.isLetter(n))) break;
                    end++;
                }
                add(spans, i, end, SyntaxSpan.NUMBER, maxSpans);
                i = end;
                continue;
            }
            if (isIdentifierStart(c)) {
                int end = i + 1;
                while (end < limit && isIdentifierPart(text.charAt(end))) end++;
                String word = text.substring(i, end);
                if (keywords.contains(word)) add(spans, i, end, SyntaxSpan.KEYWORD, maxSpans);
                i = end;
                continue;
            }
            i++;
        }
    }

    private static void scanXml(String text,
                                int start,
                                int limit,
                                int maxSpans,
                                List<SyntaxSpan> spans) {
        int i = start;
        while (i < limit && spans.size() < maxSpans) {
            if (startsWith(text, i, "<!--", limit)) {
                int end = i + 4;
                while (end + 2 < limit && !startsWith(text, end, "-->", limit)) end++;
                if (end + 2 < limit) end += 3; else end = limit;
                add(spans, i, end, SyntaxSpan.COMMENT, maxSpans);
                i = end;
                continue;
            }
            if (text.charAt(i) != '<') {
                i++;
                continue;
            }

            int cursor = i + 1;
            if (cursor < limit && (text.charAt(cursor) == '/' || text.charAt(cursor) == '?' || text.charAt(cursor) == '!')) cursor++;
            while (cursor < limit && Character.isWhitespace(text.charAt(cursor))) cursor++;
            int tagStart = cursor;
            while (cursor < limit && isXmlNameChar(text.charAt(cursor))) cursor++;
            if (cursor > tagStart) add(spans, tagStart, cursor, SyntaxSpan.TAG, maxSpans);

            while (cursor < limit && text.charAt(cursor) != '>' && spans.size() < maxSpans) {
                char c = text.charAt(cursor);
                if (c == '"' || c == '\'') {
                    int quotedEnd = scanQuoted(text, cursor, limit, c);
                    add(spans, cursor, quotedEnd, SyntaxSpan.STRING, maxSpans);
                    cursor = quotedEnd;
                    continue;
                }
                if (Character.isWhitespace(c) || c == '/' || c == '?') {
                    cursor++;
                    continue;
                }
                if (isXmlNameChar(c)) {
                    int attrStart = cursor;
                    while (cursor < limit && isXmlNameChar(text.charAt(cursor))) cursor++;
                    int afterName = cursor;
                    while (cursor < limit && Character.isWhitespace(text.charAt(cursor))) cursor++;
                    if (cursor < limit && text.charAt(cursor) == '=') {
                        add(spans, attrStart, afterName, SyntaxSpan.ATTRIBUTE, maxSpans);
                        cursor++;
                        while (cursor < limit && Character.isWhitespace(text.charAt(cursor))) cursor++;
                        if (cursor < limit && (text.charAt(cursor) == '"' || text.charAt(cursor) == '\'')) {
                            char quote = text.charAt(cursor);
                            int quotedEnd = scanQuoted(text, cursor, limit, quote);
                            add(spans, cursor, quotedEnd, SyntaxSpan.STRING, maxSpans);
                            cursor = quotedEnd;
                        }
                    }
                    continue;
                }
                cursor++;
            }
            i = cursor < limit ? cursor + 1 : limit;
        }
    }

    private static int scanQuoted(String text, int start, int limit, char quote) {
        int i = start + 1;
        boolean escaped = false;
        while (i < limit) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == quote) {
                return i + 1;
            }
            i++;
        }
        return limit;
    }

    private static void add(List<SyntaxSpan> spans, int start, int end, String kind, int maxSpans) {
        if (spans.size() >= maxSpans || end <= start) return;
        spans.add(new SyntaxSpan(start, end, kind));
    }

    private static boolean startsWith(String text, int offset, String value, int limit) {
        if (offset < 0 || offset + value.length() > limit) return false;
        for (int i = 0; i < value.length(); i++) if (text.charAt(offset + i) != value.charAt(i)) return false;
        return true;
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static boolean isXmlNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '.';
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static Set<String> words(String... values) {
        HashSet<String> result = new HashSet<String>();
        for (String value : values) result.add(value);
        return result;
    }
}
