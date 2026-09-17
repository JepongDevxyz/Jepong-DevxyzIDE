package com.jepongdevxyz.idebuild.core.signing;

/** Immutable result of APK sign + verification. */
public final class ApkSigningResult {
    private final boolean success;
    private final int signExitCode;
    private final int verifyExitCode;
    private final String signStdout;
    private final String signStderr;
    private final String verifyStdout;
    private final String verifyStderr;

    public ApkSigningResult(boolean success,
                            int signExitCode,
                            int verifyExitCode,
                            String signStdout,
                            String signStderr,
                            String verifyStdout,
                            String verifyStderr) {
        this.success = success;
        this.signExitCode = signExitCode;
        this.verifyExitCode = verifyExitCode;
        this.signStdout = signStdout == null ? "" : signStdout;
        this.signStderr = signStderr == null ? "" : signStderr;
        this.verifyStdout = verifyStdout == null ? "" : verifyStdout;
        this.verifyStderr = verifyStderr == null ? "" : verifyStderr;
    }

    public boolean isSuccess() { return success; }
    public int getSignExitCode() { return signExitCode; }
    public int getVerifyExitCode() { return verifyExitCode; }
    public String getSignStdout() { return signStdout; }
    public String getSignStderr() { return signStderr; }
    public String getVerifyStdout() { return verifyStdout; }
    public String getVerifyStderr() { return verifyStderr; }
}
