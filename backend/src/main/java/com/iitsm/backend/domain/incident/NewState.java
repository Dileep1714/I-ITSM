package com.iitsm.backend.domain.incident;

import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.sla.SlaPolicy;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Just reported. Must be classified (category, priority, SLA) before anyone can work on it. */
final class NewState extends AbstractIncidentState {

    private static final Set<IncidentOperation> ALLOWED = Set.copyOf(
            EnumSet.of(IncidentOperation.MARK_DUPLICATE, IncidentOperation.CLASSIFY, IncidentOperation.CANCEL));

    @Override
    public IncidentStatus status() {
        return IncidentStatus.NEW;
    }

    @Override
    public Set<IncidentOperation> allowedOperations() {
        return ALLOWED;
    }

    @Override
    public void classify(Incident incident, Category category, Priority priority, SlaPolicy slaPolicy, Instant now) {
        incident.applyClassification(category, priority, slaPolicy, now);
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
