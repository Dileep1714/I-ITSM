package com.iitsm.backend.domain.sla;

/** Where an incident stands against its SLA right now. */
public enum SlaStatus {
    /** Not classified yet, so no SLA applies. */
    NOT_STARTED,
    ON_TRACK,
    /** Past the warning threshold (e.g. 75 % of the time used) but not breached. */
    AT_RISK,
    /** Waiting for the reporter: the resolution clock is stopped. */
    PAUSED,
    BREACHED,
    /** Resolved within all targets. */
    MET
}
