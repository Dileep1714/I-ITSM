package com.iitsm.backend.domain.incident;

import com.iitsm.backend.domain.user.Technician;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Category, priority and SLA are known; waiting for a technician. The SLA clock is already running. */
final class ClassifiedState extends AbstractIncidentState {

    private static final Set<IncidentOperation> ALLOWED = Set.copyOf(
            EnumSet.of(IncidentOperation.ASSIGN, IncidentOperation.CANCEL));

    @Override
    public IncidentStatus status() {
        return IncidentStatus.CLASSIFIED;
    }

    @Override
    public Set<IncidentOperation> allowedOperations() {
        return ALLOWED;
    }

    @Override
    public void assign(Incident incident, Technician technician, int technicianActiveIncidents, Instant now) {
        incident.applyAssignment(technician, technicianActiveIncidents, now);
    }

    @Override
    public void cancel(Incident incident, String reason, Incident original, Instant now) {
        incident.applyCancellation(reason, original, now);
    }
}
