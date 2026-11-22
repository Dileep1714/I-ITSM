package com.iitsm.backend.domain.incident;

/** Everything noteworthy that can happen to an incident. Each one becomes an audit entry and may notify people. */
public enum IncidentEventType {
    CREATED,
    CLASSIFIED,
    PRIORITY_CHANGED,
    DETAILS_UPDATED,
    ASSIGNED,
    REASSIGNED,
    ACKNOWLEDGED,
    WORK_STARTED,
    INFO_REQUESTED,
    INFO_PROVIDED,
    NOTE_ADDED,
    RESOLVED,
    RESOLUTION_ACCEPTED,
    RESOLUTION_REJECTED,
    AUTO_VERIFIED,
    CLOSED,
    CANCELLED,
    SLA_WARNING,
    /** The forecast says the resolution deadline will probably be missed (raised before it happens). */
    SLA_BREACH_PREDICTED,
    SLA_BREACHED,
    ESCALATED
}
