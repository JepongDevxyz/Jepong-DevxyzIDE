import com.jepongdevxyz.idebuild.core.database.SqlQueryGuard;

public final class SqlQueryGuardHostTest {
    private static int passed;

    public static void main(String[] args) {
        acceptsReadOnlyQueries();
        rejectsMutatingQueries();
        rejectsMultipleStatementsAndCommentsBeforeMutation();
        System.out.println("SQL QUERY GUARD HOST TESTS PASSED: " + passed + "/3");
    }

    private static void acceptsReadOnlyQueries() {
        assertTrue(SqlQueryGuard.isReadOnly("SELECT * FROM users LIMIT 10"));
        assertTrue(SqlQueryGuard.isReadOnly("  pragma table_info(users)"));
        assertTrue(SqlQueryGuard.isReadOnly("WITH recent AS (SELECT * FROM logs) SELECT * FROM recent"));
        assertTrue(SqlQueryGuard.isReadOnly("EXPLAIN QUERY PLAN SELECT * FROM users"));
        passed++;
    }

    private static void rejectsMutatingQueries() {
        assertFalse(SqlQueryGuard.isReadOnly("DELETE FROM users"));
        assertFalse(SqlQueryGuard.isReadOnly("UPDATE users SET name='x'"));
        assertFalse(SqlQueryGuard.isReadOnly("INSERT INTO users(name) VALUES('x')"));
        assertFalse(SqlQueryGuard.isReadOnly("DROP TABLE users"));
        assertFalse(SqlQueryGuard.isReadOnly("VACUUM"));
        passed++;
    }

    private static void rejectsMultipleStatementsAndCommentsBeforeMutation() {
        assertFalse(SqlQueryGuard.isReadOnly("SELECT 1; DELETE FROM users"));
        assertFalse(SqlQueryGuard.isReadOnly("-- hello\nDELETE FROM users"));
        assertFalse(SqlQueryGuard.isReadOnly("/* comment */ UPDATE users SET name='x'"));
        passed++;
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
}
