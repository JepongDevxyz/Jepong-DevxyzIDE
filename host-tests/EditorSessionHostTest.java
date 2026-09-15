import com.jepongdevxyz.idebuild.core.ProjectPath;
import com.jepongdevxyz.idebuild.core.editor.EditorDocument;
import com.jepongdevxyz.idebuild.core.editor.EditorSession;

public final class EditorSessionHostTest {
    private static int passed;

    public static void main(String[] args) {
        dirtyStateTracksSavedContent();
        sessionDeduplicatesAndSwitchesTabs();
        dirtyDocumentsRequireExplicitDiscard();
        cursorAndScrollStateArePreserved();
        System.out.println("EDITOR SESSION HOST TESTS PASSED: " + passed + "/4");
    }

    private static void dirtyStateTracksSavedContent() {
        ProjectPath path = ProjectPath.of("local-project", "app/src/Main.java");
        EditorDocument document = new EditorDocument(path, "class Main {}\n");
        assertFalse(document.isDirty());
        document.setText("class Main { int x; }\n");
        assertTrue(document.isDirty());
        document.markSaved();
        assertFalse(document.isDirty());
        document.setText("class Main { int x; }\n");
        assertFalse(document.isDirty());
        passed++;
    }

    private static void sessionDeduplicatesAndSwitchesTabs() {
        EditorSession session = new EditorSession();
        ProjectPath first = ProjectPath.of("local-project", "one.java");
        ProjectPath second = ProjectPath.of("local-project", "two.xml");

        EditorDocument firstDocument = session.open(first, "one");
        EditorDocument secondDocument = session.open(second, "two");
        EditorDocument duplicate = session.open(first, "ignored replacement");

        assertSame(firstDocument, duplicate);
        assertEquals(2, session.size());
        assertSame(firstDocument, session.getActive());
        session.switchTo(second);
        assertSame(secondDocument, session.getActive());
        passed++;
    }

    private static void dirtyDocumentsRequireExplicitDiscard() {
        EditorSession session = new EditorSession();
        ProjectPath first = ProjectPath.of("local-project", "one.java");
        ProjectPath second = ProjectPath.of("local-project", "two.java");
        EditorDocument firstDocument = session.open(first, "one");
        session.open(second, "two");
        firstDocument.setText("changed");

        assertFalse(session.close(first, false));
        assertEquals(2, session.size());
        assertTrue(session.close(first, true));
        assertEquals(1, session.size());
        assertEquals("two.java", session.getActive().getPath().getRelativePath());
        passed++;
    }

    private static void cursorAndScrollStateArePreserved() {
        ProjectPath path = ProjectPath.of("local-project", "notes.txt");
        EditorDocument document = new EditorDocument(path, "abcdef");
        document.setCursorOffset(4);
        document.setSelectionStart(2);
        document.setSelectionEnd(5);
        document.setScrollY(120);

        assertEquals(4, document.getCursorOffset());
        assertEquals(2, document.getSelectionStart());
        assertEquals(5, document.getSelectionEnd());
        assertEquals(120, document.getScrollY());
        document.setCursorOffset(999);
        assertEquals(6, document.getCursorOffset());
        passed++;
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) throw new AssertionError("Expected same object");
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
}
