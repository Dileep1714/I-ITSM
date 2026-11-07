package com.iitsm.backend.domain.incident;

import java.util.Set;

/** Final state for invalid reports and duplicates. Every operation is rejected. */
final class CancelledState extends AbstractIncidentState {

    @Override
    public IncidentStatus status() {
        return IncidentStatus.CANCELLED;
    }

    @Override
    public Set<IncidentOperation> allowedOperations() {
        return Set.of();
    }
}
