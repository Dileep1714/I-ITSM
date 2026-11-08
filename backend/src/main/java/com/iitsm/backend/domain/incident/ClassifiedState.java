package com.iitsm.backend.domain.incident;

import com.iitsm.backend.domain.user.Technician;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Category, priority and SLA are known; waiting for a technician. The SLA clock is already running. */
final class ClassifiedState extends AbstractIncidentState {

    private static final Set<IncidentOperation> ALLOWED = Set.copyOf(
            EnumSet.of(IncidentOperation.MARK_DUPLICATE, IncidentOperation.ASSIGN, IncidentOperation.TAKE,
                    IncidentOperation.CANCEL));

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

    /** Only here: once someone owns the incident, moving it is a reassignment (team lead and above). */
    @Override
    public void take(Incident incident, Technician by, int technicianActiveIncidents, Instant now) {
        incident.applyAssignment(by, technicianActiveIncidents, now);
    }

    @Override
    public void cancel(Incident incident, String reason, Incident original, Instant now) {
        incident.applyCancellation(reason, original, now);
    }

    /** Duplicates are often noticed only once someone starts looking, so this is allowed until resolution. */
    @Override
    public void markDuplicate(Incident incident, Incident original, Instant now) {
        incident.applyCancellation("Duplicate of " + original.getNumber(), original, now);
    }
}
