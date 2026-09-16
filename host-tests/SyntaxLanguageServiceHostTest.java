import com.jepongdevxyz.idebuild.core.editor.SyntaxLanguage;
import com.jepongdevxyz.idebuild.core.editor.SyntaxLanguageService;
import com.jepongdevxyz.idebuild.core.editor.SyntaxSpan;

import java.util.List;

public final class SyntaxLanguageServiceHostTest {
    private static int passed;

    public static void main(String[] args) {
        detectsSupportedLanguages();
        highlightsJavaAndKotlinBasics();
        highlightsXmlBasicsWithinBounds();
        System.out.println("SYNTAX LANGUAGE SERVICE HOST TESTS PASSED: " + passed + "/3");
    }

    private static void detectsSupportedLanguages() {
        assertEquals(SyntaxLanguage.JAVA, SyntaxLanguageService.detect("app/src/Main.java"));
        assertEquals(SyntaxLanguage.KOTLIN, SyntaxLanguageService.detect("app/src/MainActivity.kt"));
        assertEquals(SyntaxLanguage.XML, SyntaxLanguageService.detect("res/layout/main.xml"));
        assertEquals(SyntaxLanguage.GRADLE, SyntaxLanguageService.detect("build.gradle.kts"));
        assertEquals(SyntaxLanguage.JSON, SyntaxLanguageService.detect("config.json"));
        assertEquals(SyntaxLanguage.JAVASCRIPT, SyntaxLanguageService.detect("app.js"));
        assertEquals(SyntaxLanguage.MARKDOWN, SyntaxLanguageService.detect("README.md"));
        assertEquals(SyntaxLanguage.PLAIN_TEXT, SyntaxLanguageService.detect("LICENSE"));
        passed++;
    }

    private static void highlightsJavaAndKotlinBasics() {
        String java = "public class Main { // note\n String s = \"hello\"; int n = 42; }";
        List<SyntaxSpan> javaSpans = SyntaxLanguageService.scan(java, SyntaxLanguage.JAVA, 0, java.length(), 2000, 100);
        assertHas(javaSpans, SyntaxSpan.KEYWORD, "public", java);
        assertHas(javaSpans, SyntaxSpan.KEYWORD, "class", java);
        assertHas(javaSpans, SyntaxSpan.COMMENT, "// note", java);
        assertHas(javaSpans, SyntaxSpan.STRING, "\"hello\"", java);
        assertHas(javaSpans, SyntaxSpan.NUMBER, "42", java);

        String kotlin = "fun main() { val answer = 42 // ok\n println(\"hi\") }";
        List<SyntaxSpan> kotlinSpans = SyntaxLanguageService.scan(kotlin, SyntaxLanguage.KOTLIN, 0, kotlin.length(), 2000, 100);
        assertHas(kotlinSpans, SyntaxSpan.KEYWORD, "fun", kotlin);
        assertHas(kotlinSpans, SyntaxSpan.KEYWORD, "val", kotlin);
        assertHas(kotlinSpans, SyntaxSpan.STRING, "\"hi\"", kotlin);
        passed++;
    }

    private static void highlightsXmlBasicsWithinBounds() {
        String xml = "<LinearLayout android:layout_width=\"match_parent\"><!-- note --><TextView /></LinearLayout>";
        List<SyntaxSpan> spans = SyntaxLanguageService.scan(xml, SyntaxLanguage.XML, 0, xml.length(), 60, 5);
        assertTrue(spans.size() <= 5);
        for (SyntaxSpan span : spans) {
            assertTrue(span.getStart() >= 0);
            assertTrue(span.getEnd() <= 60);
            assertTrue(span.getEnd() > span.getStart());
        }
        assertHasKind(spans, SyntaxSpan.TAG);
        assertHasKind(spans, SyntaxSpan.ATTRIBUTE);
        assertHasKind(spans, SyntaxSpan.STRING);
        passed++;
    }

    private static void assertHas(List<SyntaxSpan> spans, String kind, String expected, String source) {
        for (SyntaxSpan span : spans) {
            if (kind.equals(span.getKind()) && expected.equals(source.substring(span.getStart(), span.getEnd()))) return;
        }
        throw new AssertionError("Missing " + kind + " span for " + expected + " in " + spans);
    }

    private static void assertHasKind(List<SyntaxSpan> spans, String kind) {
        for (SyntaxSpan span : spans) if (kind.equals(span.getKind())) return;
        throw new AssertionError("Missing span kind " + kind + " in " + spans);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
}
