package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.common.strategy.NamedStrategy;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.user.Technician;

import java.util.List;
import java.util.Optional;

/**
 * <b>Strategy pattern:</b> a way of choosing a technician for an incident.
 *
 * <p>Strategies <b>rank</b> rather than just pick, so the UI can show the top recommendations with their reasons
 * and a dispatcher can choose someone else. Technicians who cannot take the incident (unavailable, at full
 * capacity, or already assigned to it) never appear in the ranking.
 */
public interface AssignmentStrategy extends NamedStrategy {

    /** Eligible candidates, best first. Empty if nobody can take the incident. */
    List<TechnicianScore> rank(Incident incident, List<AssignmentCandidate> candidates);

    default Optional<TechnicianScore> recommend(Incident incident, List<AssignmentCandidate> candidates) {
        return rank(incident, candidates).stream().findFirst();
    }

    /** Called after an assignment was made, for strategies that keep state (e.g. round-robin). */
    default void onAssigned(Technician technician) {
    }
}
