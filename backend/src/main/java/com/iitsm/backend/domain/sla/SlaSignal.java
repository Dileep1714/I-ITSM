package com.iitsm.backend.domain.sla;

/** A threshold an incident has just crossed. Each signal is raised at most once per incident. */
public enum SlaSignal {
    RESPONSE_AT_RISK(false, "Response"),
    RESOLUTION_AT_RISK(false, "Resolution"),
    RESPONSE_BREACHED(true, "Response"),
    RESOLUTION_BREACHED(true, "Resolution");

    private final boolean breach;
    private final String target;

    SlaSignal(boolean breach, String target) {
        this.breach = breach;
        this.target = target;
    }

    public boolean isBreach() {
        return breach;
    }

    /** "Response" or "Resolution". */
    public String target() {
        return target;
    }
}
