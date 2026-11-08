package com.iitsm.backend.domain.incident;

import com.iitsm.backend.domain.user.Employee;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Waiting for the reporter to answer a question. The SLA resolution clock is paused. */
final class OnHoldState extends AbstractIncidentState {

    private static final Set<IncidentOperation> ALLOWED = Set.copyOf(EnumSet.of(IncidentOperation.MARK_DUPLICATE, IncidentOperation.PROVIDE_INFO));

    @Override
    public IncidentStatus status() {
        return IncidentStatus.ON_HOLD;
    }

    @Override
    public Set<IncidentOperation> allowedOperations() {
        return ALLOWED;
    }

    @Override
    public void provideInfo(Incident incident, Employee by, String answer, Instant now) {
        incident.applyInfoProvided(by, answer, now);
    }

    /** Duplicates are often noticed only once someone starts looking, so this is allowed until resolution. */
    @Override
    public void markDuplicate(Incident incident, Incident original, Instant now) {
        incident.applyCancellation("Duplicate of " + original.getNumber(), original, now);
    }
}
