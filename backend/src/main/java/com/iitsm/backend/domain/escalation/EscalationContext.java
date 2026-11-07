package com.iitsm.backend.domain.escalation;

import com.iitsm.backend.domain.assignment.AssignmentCandidate;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.user.SupportLevel;

import java.util.Optional;

/**
 * What an escalation handler needs from the outside world: who is available at a given support level.
 * Implemented by the service layer (it needs the database); the handlers themselves stay pure domain logic.
 */
@FunctionalInterface
public interface EscalationContext {

    Optional<AssignmentCandidate> bestAvailableAt(SupportLevel level, Incident incident);
}
