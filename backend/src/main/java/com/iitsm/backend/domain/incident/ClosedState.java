package com.iitsm.backend.domain.incident;

import java.util.Set;

/** Final state. Every operation is rejected (inherited from {@link AbstractIncidentState}). */
final class ClosedState extends AbstractIncidentState {

    @Override
    public IncidentStatus status() {
        return IncidentStatus.CLOSED;
    }

    @Override
    public Set<IncidentOperation> allowedOperations() {
        return Set.of();
    }
}
