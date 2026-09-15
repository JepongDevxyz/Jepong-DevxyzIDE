package com.jepongdevxyz.idebuild.core.build;

import com.jepongdevxyz.idebuild.core.ProjectPath;

/** One navigable compiler/resource diagnostic produced by a build. */
public final class BuildProblem {
    public enum Severity { ERROR, WARNING, INFO }

    private final Severity severity;
    private final ProjectPath path;
    private final int line;
    private final int column;
    private final String message;
    private final String rawLine;

    public BuildProblem(Severity severity,
                        ProjectPath path,
                        int line,
                        int column,
                        String message,
                        String rawLine) {
        if (severity == null) throw new IllegalArgumentException("severity must not be null");
        if (path == null) throw new IllegalArgumentException("path must not be null");
        this.severity = severity;
        this.path = path;
        this.line = Math.max(1, line);
        this.column = Math.max(1, column);
        this.message = message == null ? "" : message;
        this.rawLine = rawLine == null ? "" : rawLine;
    }

    public Severity getSeverity() { return severity; }
    public ProjectPath getPath() { return path; }
    public int getLine() { return line; }
    public int getColumn() { return column; }
    public String getMessage() { return message; }
    public String getRawLine() { return rawLine; }

    public String summary() {
        return severity + " " + path.getRelativePath() + ":" + line + ":" + column + " " + message;
    }
}
