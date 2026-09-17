package com.jepongdevxyz.idebuild.core.capability;

public final class Capability {
    private final String id;
    private final String label;
    private final CapabilityStatus status;
    private final String userMessage;

    public Capability(String id, String label, CapabilityStatus status, String userMessage) {
        if (id == null || id.length() == 0) throw new IllegalArgumentException("id required");
        if (label == null || label.length() == 0) throw new IllegalArgumentException("label required");
        if (status == null) throw new IllegalArgumentException("status required");
        this.id = id;
        this.label = label;
        this.status = status;
        this.userMessage = userMessage == null ? "" : userMessage;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public CapabilityStatus getStatus() { return status; }
    public String getUserMessage() { return userMessage; }
    public boolean isEnabled() { return status.isActionable(); }
}