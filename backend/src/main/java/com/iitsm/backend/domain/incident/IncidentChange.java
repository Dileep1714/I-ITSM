package com.iitsm.backend.domain.incident;

import java.time.Instant;

/**
 * A fact recorded by {@link Incident} when something happens to it ("assigned to Ravi Kumar").
 *
 * <p>The incident only <em>records</em> changes; it does not know who listens. The service layer collects them
 * with {@link Incident#pullChanges()} after each operation and publishes them as events. The audit trail,
 * notifications and SLA violation records are all observers of those events (Observer pattern). The domain
 * stays free of infrastructure, and a new reaction to incident changes never requires touching {@code Incident}.
 *
 * @param visibleToReporter false for staff-only facts (internal notes, SLA warnings), so the employee's view of
 *                          the history and their notifications never reveal them
 */
public record IncidentChange(IncidentEventType type, IncidentStatus previousStatus, IncidentStatus newStatus,
                             String description, boolean visibleToReporter, Instant occurredAt) {
}
