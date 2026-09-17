import com.jepongdevxyz.idebuild.core.editor.TextSearchService;

import java.util.List;

public final class TextSearchServiceHostTest {
    private static int passed;

    public static void main(String[] args) {
        findsMatchesWithLineAndColumn();
        supportsCaseInsensitiveSearchAndLimits();
        replacesWithoutRegexSemantics();
        findsNextWithOptionalWrap();
        System.out.println("TEXT SEARCH SERVICE HOST TESTS PASSED: " + passed + "/4");
    }

    private static void findsMatchesWithLineAndColumn() {
        String text = "alpha beta\nalpha gamma\n";
        List<TextSearchService.Match> matches = TextSearchService.findAll(text, "alpha", true, 20);
        assertEquals(2, matches.size());
        assertEquals(0, matches.get(0).getOffset());
        assertEquals(1, matches.get(0).getLineNumber());
        assertEquals(1, matches.get(0).getColumnNumber());
        assertEquals(11, matches.get(1).getOffset());
        assertEquals(2, matches.get(1).getLineNumber());
        assertEquals("alpha gamma", matches.get(1).getLineText());
        passed++;
    }

    private static void supportsCaseInsensitiveSearchAndLimits() {
        List<TextSearchService.Match> matches = TextSearchService.findAll("One one ONE one", "one", false, 2);
        assertEquals(2, matches.size());
        assertEquals(0, matches.get(0).getOffset());
        assertEquals(4, matches.get(1).getOffset());
        passed++;
    }

    private static void replacesWithoutRegexSemantics() {
        TextSearchService.ReplaceResult result = TextSearchService.replaceAll(
                "a.b A.B a.b", "a.b", "$value\\1", false, 10);
        assertEquals(3, result.getReplacementCount());
        assertEquals("$value\\1 $value\\1 $value\\1", result.getText());
        passed++;
    }

    private static void findsNextWithOptionalWrap() {
        String text = "first second first";
        TextSearchService.Match next = TextSearchService.findNext(text, "first", 2, true, false);
        assertEquals(13, next.getOffset());
        assertNull(TextSearchService.findNext(text, "first", 18, true, false));
        TextSearchService.Match wrapped = TextSearchService.findNext(text, "first", 18, true, true);
        assertEquals(0, wrapped.getOffset());
        passed++;
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private static void assertNull(Object value) {
        if (value != null) throw new AssertionError("Expected null but was " + value);
    }
}
