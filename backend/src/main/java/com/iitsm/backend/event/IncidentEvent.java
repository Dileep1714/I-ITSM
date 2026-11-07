package com.iitsm.backend.event;

import com.iitsm.backend.domain.incident.IncidentChange;
import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.incident.IncidentStatus;

import java.time.Instant;

/**
 * Something happened to an incident: published to every interested observer (audit, notifications, SLA
 * violations, live dashboard).
 *
 * <p>Carries ids and copied values rather than entities, so a listener cannot accidentally change the incident,
 * and the event still makes sense if it is handled later or elsewhere.
 *
 * @param actorId {@code null} when the system acted (SLA monitor, auto-verification)
 */
public record IncidentEvent(Long incidentId, String incidentNumber, IncidentEventType type,
                            IncidentStatus previousStatus, IncidentStatus newStatus, String description,
                            boolean visibleToReporter, Long actorId, String actorName, Instant occurredAt) {

    public static IncidentEvent of(Long incidentId, String incidentNumber, IncidentChange change,
                                   Long actorId, String actorName) {
        return new IncidentEvent(incidentId, incidentNumber, change.type(), change.previousStatus(),
                change.newStatus(), change.description(), change.visibleToReporter(), actorId, actorName,
                change.occurredAt());
    }

    public boolean isSystemAction() {
        return actorId == null;
    }
}
