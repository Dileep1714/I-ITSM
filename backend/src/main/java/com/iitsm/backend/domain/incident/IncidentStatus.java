package com.iitsm.backend.domain.incident;

/**
 * The stored lifecycle position of an incident. The <em>behaviour</em> of each status lives in the matching
 * {@link IncidentState} class; this enum is only what is saved in the database.
 */
public enum IncidentStatus {
    NEW,
    CLASSIFIED,
    ASSIGNED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    ON_HOLD,
    RESOLVED,
    VERIFIED,
    CLOSED,
    CANCELLED;

    /** Open incidents still need work and count towards technician workload and SLA monitoring. */
    public boolean isOpen() {
        return this != VERIFIED && this != CLOSED && this != CANCELLED;
    }

    /** A technician is actively responsible for the incident in these states. */
    public boolean countsTowardsWorkload() {
        return this == ASSIGNED || this == ACKNOWLEDGED || this == IN_PROGRESS || this == ON_HOLD;
    }
}
