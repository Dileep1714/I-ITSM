package com.iitsm.backend.domain.incident;

import com.iitsm.backend.domain.user.Technician;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** The technician is investigating. Can ask the reporter for information, resolve, or hand over. */
final class InProgressState extends AbstractIncidentState {

    private static final Set<IncidentOperation> ALLOWED = Set.copyOf(EnumSet.of(IncidentOperation.MARK_DUPLICATE, 
            IncidentOperation.REQUEST_INFO, IncidentOperation.RESOLVE, IncidentOperation.ASSIGN));

    @Override
    public IncidentStatus status() {
        return IncidentStatus.IN_PROGRESS;
    }

    @Override
    public Set<IncidentOperation> allowedOperations() {
        return ALLOWED;
    }

    @Override
    public void requestInfo(Incident incident, Technician by, String question, Instant now) {
        incident.applyInfoRequest(by, question, now);
    }

    @Override
    public void resolve(Incident incident, Technician by, String summary, String rootCause, ResolutionCode code,
                        Instant now) {
        incident.applyResolution(by, summary, rootCause, code, now);
    }

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
