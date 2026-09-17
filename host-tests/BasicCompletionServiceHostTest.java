import com.jepongdevxyz.idebuild.core.editor.BasicCompletionService;
import com.jepongdevxyz.idebuild.core.editor.SyntaxLanguage;

import java.util.List;

public final class BasicCompletionServiceHostTest {
    private static int passed;

    public static void main(String[] args) {
        javaCompletionUsesPrefixAndDocumentSymbols();
        kotlinCompletionIncludesKeywords();
        xmlCompletionIncludesAndroidAttributes();
        completionIsBoundedAndDeduplicated();
        System.out.println("BASIC COMPLETION SERVICE HOST TESTS PASSED: " + passed + "/4");
    }

    private static void javaCompletionUsesPrefixAndDocumentSymbols() {
        String text = "public class Demo {\n" +
                "  private String messageText;\n" +
                "  void renderMessage() {\n" +
                "    mes\n" +
                "  }\n" +
                "}";
        int cursor = text.indexOf("mes\n") + 3;
        List<String> values = BasicCompletionService.complete(text, cursor, SyntaxLanguage.JAVA, 20);
        assertContains(values, "messageText");
        passed++;
    }

    private static void kotlinCompletionIncludesKeywords() {
        String text = "fun main() {\n  re\n}";
        int cursor = text.indexOf("re\n") + 2;
        List<String> values = BasicCompletionService.complete(text, cursor, SyntaxLanguage.KOTLIN, 20);
        assertContains(values, "return");
        passed++;
    }

    private static void xmlCompletionIncludesAndroidAttributes() {
        String text = "<TextView android:lay />";
        int cursor = text.indexOf("lay") + 3;
        List<String> values = BasicCompletionService.complete(text, cursor, SyntaxLanguage.XML, 20);
        assertContains(values, "android:layout_width");
        assertContains(values, "android:layout_height");
        passed++;
    }

    private static void completionIsBoundedAndDeduplicated() {
        String text = "class Demo { int alpha; int alphabet; int alpha; void x(){ al } }";
        int cursor = text.indexOf("al }") + 2;
        List<String> values = BasicCompletionService.complete(text, cursor, SyntaxLanguage.JAVA, 2);
        if (values.size() > 2) throw new AssertionError("Expected at most 2 results but got " + values.size());
        int alphaCount = 0;
        for (String value : values) if ("alpha".equals(value)) alphaCount++;
        if (alphaCount > 1) throw new AssertionError("Duplicate completion returned");
        passed++;
    }

    private static void assertContains(List<String> values, String expected) {
        if (!values.contains(expected)) throw new AssertionError("Expected completion " + expected + " in " + values);
    }
}
