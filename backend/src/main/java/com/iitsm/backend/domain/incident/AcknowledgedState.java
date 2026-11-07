package com.iitsm.backend.domain.incident;

import com.iitsm.backend.domain.user.Technician;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** The technician has accepted the incident (the SLA response target is met or missed at this moment). */
final class AcknowledgedState extends AbstractIncidentState {

    private static final Set<IncidentOperation> ALLOWED = Set.copyOf(
            EnumSet.of(IncidentOperation.START_WORK, IncidentOperation.ASSIGN));

    @Override
    public IncidentStatus status() {
        return IncidentStatus.ACKNOWLEDGED;
    }

    @Override
    public Set<IncidentOperation> allowedOperations() {
        return ALLOWED;
    }

    @Override
    public void startWork(Incident incident, Technician by, Instant now) {
        incident.applyStartWork(by, now);
    }

    @Override
    public void assign(Incident incident, Technician technician, int technicianActiveIncidents, Instant now) {
        incident.applyAssignment(technician, technicianActiveIncidents, now);
    }
}
