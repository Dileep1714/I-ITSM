package com.iitsm.backend.domain.incident;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** The fix is confirmed. Only closing remains. */
final class VerifiedState extends AbstractIncidentState {

    private static final Set<IncidentOperation> ALLOWED = Set.copyOf(EnumSet.of(IncidentOperation.CLOSE));

    @Override
    public IncidentStatus status() {
        return IncidentStatus.VERIFIED;
    }

    @Override
    public Set<IncidentOperation> allowedOperations() {
        return ALLOWED;
    }

    @Override
    public void close(Incident incident, Instant now) {
        incident.applyClose(now);
    }
}
