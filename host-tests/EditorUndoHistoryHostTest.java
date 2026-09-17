import com.jepongdevxyz.idebuild.core.editor.EditorUndoHistory;

public final class EditorUndoHistoryHostTest {
    private static int passed;

    public static void main(String[] args) {
        undoRedoRoundTrip();
        newEditClearsRedo();
        oversizedSnapshotsAreIgnored();
        historyIsBounded();
        System.out.println("EDITOR UNDO HISTORY HOST TESTS PASSED: " + passed + "/4");
    }

    private static void undoRedoRoundTrip() {
        EditorUndoHistory history = new EditorUndoHistory(8, 1000);
        history.reset("one");
        history.record("two");
        history.record("three");
        assertEquals("two", history.undo("three"));
        assertEquals("one", history.undo("two"));
        assertEquals("two", history.redo("one"));
        passed++;
    }

    private static void newEditClearsRedo() {
        EditorUndoHistory history = new EditorUndoHistory(8, 1000);
        history.reset("a");
        history.record("ab");
        assertEquals("a", history.undo("ab"));
        history.record("ax");
        if (history.canRedo()) throw new AssertionError("Redo must clear after a new edit");
        passed++;
    }

    private static void oversizedSnapshotsAreIgnored() {
        EditorUndoHistory history = new EditorUndoHistory(8, 4);
        history.reset("ok");
        history.record("12345");
        if (history.canUndo()) throw new AssertionError("Oversized snapshot should not enter history");
        passed++;
    }

    private static void historyIsBounded() {
        EditorUndoHistory history = new EditorUndoHistory(2, 1000);
        history.reset("0");
        history.record("1");
        history.record("2");
        history.record("3");
        assertEquals("2", history.undo("3"));
        assertEquals("1", history.undo("2"));
        if (history.canUndo()) throw new AssertionError("History exceeded max entries");
        passed++;
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }
}
