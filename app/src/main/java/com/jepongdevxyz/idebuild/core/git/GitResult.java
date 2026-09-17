package com.jepongdevxyz.idebuild.core.git;

/** Immutable result of one real Git subprocess invocation. */
public final class GitResult {
    private final int exitCode;
    private final boolean cancelled;
    private final long durationMillis;
    private final String stdout;
    private final String stderr;

    public GitResult(int exitCode,
                     boolean cancelled,
                     long durationMillis,
                     String stdout,
                     String stderr) {
        this.exitCode = exitCode;
        this.cancelled = cancelled;
        this.durationMillis = durationMillis;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
    }

    public int getExitCode() { return exitCode; }
    public boolean isCancelled() { return cancelled; }
    public long getDurationMillis() { return durationMillis; }
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public boolean isSuccess() { return !cancelled && exitCode == 0; }
}
