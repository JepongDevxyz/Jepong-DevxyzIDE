package com.jepongdevxyz.idebuild.core.capability;

public enum CapabilityStatus {
    AVAILABLE(true),
    NEEDS_INSTALL(false),
    EXPERIMENTAL(true),
    UNSUPPORTED(false),
    UNAVAILABLE(false);

    private final boolean actionable;

    CapabilityStatus(boolean actionable) {
        this.actionable = actionable;
    }

    public boolean isActionable() {
        return actionable;
    }
}