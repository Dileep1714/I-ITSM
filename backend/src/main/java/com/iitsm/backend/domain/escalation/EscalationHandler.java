package com.iitsm.backend.domain.escalation;

import com.iitsm.backend.domain.assignment.AssignmentCandidate;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.user.SupportLevel;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * <b>Chain of Responsibility:</b> one link in the escalation chain Technician → Team Lead → System Admin →
 * IT Manager.
 *
 * <p>Each handler looks at an incident and either escalates it to <em>its</em> level or passes it to the next
 * handler. A handler only acts when the incident sits exactly one level below it, so escalation always climbs one
 * step at a time, as the spec describes. Adding a level means adding one handler to the chain.
 *
 * <p>{@link #handle} is a template method: subclasses supply only their level, their condition and the reason.
 */
public abstract class EscalationHandler {

    /** Escalation is about people not responding; incidents waiting for the user or already resolved are skipped. */
    private static final Set<IncidentStatus> ESCALATABLE = EnumSet.of(
            IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED,
            IncidentStatus.IN_PROGRESS);

    private EscalationHandler next;

    /** Links the next handler and returns it, so a chain reads {@code a.then(b).then(c)}. */
    public EscalationHandler then(EscalationHandler nextHandler) {
        this.next = nextHandler;
        return nextHandler;
    }

    /** @return true if this handler or a later one escalated the incident */
    public final boolean handle(Incident incident, EscalationContext context, Instant now) {
        if (appliesTo(incident, now)) {
            Optional<AssignmentCandidate> target = context.bestAvailableAt(targetLevel(), incident);
            incident.escalate(targetLevel(),
                    target.map(AssignmentCandidate::technician).orElse(null),
                    target.map(AssignmentCandidate::activeIncidents).orElse(0),
                    reason(incident, now), now);
            return true;
        }
        return next != null && next.handle(incident, context, now);
    }

    private boolean appliesTo(Incident incident, Instant now) {
        return ESCALATABLE.contains(incident.getStatus())
                && incident.getPriority() != null
                && incident.effectiveEscalationLevel().next() == targetLevel()
                && shouldEscalate(incident, now);
    }

    /** The level this handler escalates to. */
    protected abstract SupportLevel targetLevel();

    /** The condition that makes escalation to this level necessary. */
    protected abstract boolean shouldEscalate(Incident incident, Instant now);

    /** Plain-language reason, recorded in the audit trail and sent in notifications. */
    protected abstract String reason(Incident incident, Instant now);

    protected static boolean isCritical(Incident incident) {
        PriorityLevel level = incident.getPriority().getLevel();
        return level == PriorityLevel.P1 || level == PriorityLevel.P2;
    }

    protected static boolean unacknowledged(Incident incident) {
        return incident.getSla().getRespondedAt() == null;
    }
}
