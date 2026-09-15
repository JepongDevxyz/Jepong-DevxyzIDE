package com.jepongdevxyz.idebuild.core.process;

/** Final process state. */
public final class ProcessResult {
    private final int exitCode;
    private final boolean cancelled;
    private final long durationMillis;

    public ProcessResult(int exitCode, boolean cancelled, long durationMillis) {
        this.exitCode = exitCode;
        this.cancelled = cancelled;
        this.durationMillis = Math.max(0L, durationMillis);
    }

    public int getExitCode() { return exitCode; }
    public boolean isCancelled() { return cancelled; }
    public long getDurationMillis() { return durationMillis; }
}
