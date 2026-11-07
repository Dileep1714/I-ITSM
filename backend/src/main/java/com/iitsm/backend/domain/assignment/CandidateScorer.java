package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.domain.incident.Incident;

/**
 * Scores one candidate for one incident on a 0.0–1.0 scale.
 *
 * <p>Separate from {@link AssignmentStrategy} (interface segregation): {@link SmartCompositeStrategy} combines
 * several <em>scorers</em>, while round-robin is a strategy that does not score at all.
 */
public interface CandidateScorer {

    String displayName();

    Evaluation evaluate(Incident incident, AssignmentCandidate candidate);
}
