package com.iitsm.backend.domain.incident;

import com.iitsm.backend.domain.user.Technician;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** A technician has been chosen but has not yet accepted. Escalation targets incidents stuck here. */
final class AssignedState extends AbstractIncidentState {

    private static final Set<IncidentOperation> ALLOWED = Set.copyOf(
            EnumSet.of(IncidentOperation.MARK_DUPLICATE, IncidentOperation.ACKNOWLEDGE, IncidentOperation.ASSIGN));

    @Override
    public IncidentStatus status() {
        return IncidentStatus.ASSIGNED;
    }

    @Override
    public Set<IncidentOperation> allowedOperations() {
        return ALLOWED;
    }

    @Override
    public void acknowledge(Incident incident, Technician by, Instant now) {
        incident.applyAcknowledgement(by, now);
    }

    /** Reassignment, e.g. escalation to a team lead. */
    @Override
    public void assign(Incident incident, Technician technician, int technicianActiveIncidents, Instant now) {
        incident.applyAssignment(technician, technicianActiveIncidents, now);
    }

    /** Duplicates are often noticed only once someone starts looking, so this is allowed until resolution. */
    @Override
    public void markDuplicate(Incident incident, Incident original, Instant now) {
        incident.applyCancellation("Duplicate of " + original.getNumber(), original, now);
    }
}
