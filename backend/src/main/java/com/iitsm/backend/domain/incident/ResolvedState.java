package com.iitsm.backend.domain.incident;

import com.iitsm.backend.domain.user.Employee;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** A fix has been provided. The reporter accepts or rejects it; with no answer it is auto-verified after 72 h. */
final class ResolvedState extends AbstractIncidentState {

    private static final Set<IncidentOperation> ALLOWED = Set.copyOf(EnumSet.of(
            IncidentOperation.ACCEPT_RESOLUTION, IncidentOperation.REJECT_RESOLUTION, IncidentOperation.AUTO_VERIFY));

    @Override
    public IncidentStatus status() {
        return IncidentStatus.RESOLVED;
    }

    @Override
    public Set<IncidentOperation> allowedOperations() {
        return ALLOWED;
    }

    @Override
    public void acceptResolution(Incident incident, Employee by, int rating, String comment, Instant now) {
        incident.applyAcceptance(by, rating, comment, now);
    }

    @Override
    public void rejectResolution(Incident incident, Employee by, String reason, Instant now) {
        incident.applyRejection(by, reason, now);
    }

    @Override
    public void autoVerify(Incident incident, Instant now) {
        incident.applyAutoVerification(now);
    }
}
