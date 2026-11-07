package com.iitsm.backend.domain.user;

/**
 * A technician's tier in the escalation chain: Technician → Team Lead → System Admin → IT Manager.
 *
 * <p><b>Design decision:</b> these are levels of one {@link Technician} class, not subclasses. A promotion is a
 * change of data, not a change of what kind of object someone is. Modelling it with inheritance would force an
 * object to change its class, a classic misuse of inheritance.
 */
public enum SupportLevel {
    L1_TECHNICIAN("Technician"),
    L2_TEAM_LEAD("Team Lead"),
    L3_SYSTEM_ADMIN("System Administrator"),
    L4_IT_MANAGER("IT Manager");

    private final String displayName;

    SupportLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** The next level up in the escalation chain, or {@code null} at the top. */
    public SupportLevel next() {
        SupportLevel[] levels = values();
        return ordinal() + 1 < levels.length ? levels[ordinal() + 1] : null;
    }

    public boolean isAtLeast(SupportLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
