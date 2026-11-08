package com.iitsm.backend.domain.incident;

import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Technician;

import java.time.Instant;
import java.util.Set;

/**
 * <b>State pattern:</b> the behaviour of an incident in one lifecycle status.
 *
 * <p>Each operation either performs the transition (by calling back into {@link Incident}) or throws
 * {@link com.iitsm.backend.common.exception.InvalidStateTransitionException}.
 *
 * <p>Package-private, like all implementations: only {@link Incident} uses states, so the lifecycle cannot be
 * bypassed from outside this package.
 */
interface IncidentState {

    IncidentStatus status();

    /** The operations this state allows. Kept consistent with the overrides (verified by a unit test). */
    Set<IncidentOperation> allowedOperations();

    void classify(Incident incident, Category category, Priority priority, SlaPolicy slaPolicy, Instant now);

    void assign(Incident incident, Technician technician, int technicianActiveIncidents, Instant now);

    /** A technician takes an unassigned incident for themselves. */
    void take(Incident incident, Technician by, int technicianActiveIncidents, Instant now);

    void acknowledge(Incident incident, Technician by, Instant now);

    void startWork(Incident incident, Technician by, Instant now);

    void requestInfo(Incident incident, Technician by, String question, Instant now);

    void provideInfo(Incident incident, Employee by, String answer, Instant now);

    void resolve(Incident incident, Technician by, String summary, String rootCause, ResolutionCode code,
                 Instant now);

    void acceptResolution(Incident incident, Employee by, int rating, String comment, Instant now);

    void rejectResolution(Incident incident, Employee by, String reason, Instant now);

    void autoVerify(Incident incident, Instant now);

    void close(Incident incident, Instant now);

    void cancel(Incident incident, String reason, Incident original, Instant now);

    /** Cancels the incident as a duplicate of {@code original}; possible until the incident is resolved. */
    void markDuplicate(Incident incident, Incident original, Instant now);
}
