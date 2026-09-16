package com.jepongdevxyz.idebuild.core.database;

import java.util.Locale;

/** Conservative policy used by the database viewer's default read-only query console. */
public final class SqlQueryGuard {
    private SqlQueryGuard() { }

    public static boolean isReadOnly(String sql) {
        if (sql == null) return false;
        String trimmed = sql.trim();
        if (trimmed.length() == 0) return false;
        if (containsStatementSeparator(trimmed)) return false;
        String normalized = stripLeadingWhitespace(trimmed).toUpperCase(Locale.US);
        // Deliberately reject leading comments so a hidden mutation cannot masquerade as a read query.
        if (normalized.startsWith("--") || normalized.startsWith("/*")) return false;
        return normalized.startsWith("SELECT ")
                || normalized.equals("SELECT")
                || normalized.startsWith("PRAGMA ")
                || normalized.startsWith("PRAGMA(")
                || normalized.startsWith("WITH ")
                || normalized.startsWith("WITH\n")
                || normalized.startsWith("EXPLAIN ");
    }

    private static boolean containsStatementSeparator(String sql) {
        boolean single = false;
        boolean dual = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !dual && !escaped(sql, i)) single = !single;
            else if (c == '"' && !single && !escaped(sql, i)) dual = !dual;
            else if (c == ';' && !single && !dual) {
                String tail = sql.substring(i + 1).trim();
                if (tail.length() > 0) return true;
            }
        }
        return false;
    }

    private static boolean escaped(String value, int index) {
        int backslashes = 0;
        for (int i = index - 1; i >= 0 && value.charAt(i) == '\\'; i--) backslashes++;
        return (backslashes & 1) == 1;
    }

    private static String stripLeadingWhitespace(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        return value.substring(index);
    }
}
