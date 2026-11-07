package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.incident.Incident;

import java.util.Comparator;
import java.util.List;

/**
 * Base class for strategies that score each candidate independently.
 *
 * <p><b>Template Method pattern:</b> {@link #rank} is {@code final} and fixes the algorithm's skeleton:
 * filter out ineligible candidates → score each one → sort → break ties. Subclasses fill in only the steps that
 * differ: {@link #evaluate} (always) and {@link #isEligible} (optionally). No strategy can forget the
 * availability check or sort differently from the others.
 */
public abstract class ScoringAssignmentStrategy implements AssignmentStrategy, CandidateScorer {

    /** Highest score first; on a tie, the less busy technician; then by name so results are deterministic. */
    private static final Comparator<TechnicianScore> BEST_FIRST =
            Comparator.comparingDouble(TechnicianScore::score).reversed()
                    .thenComparingInt(score -> score.candidate().activeIncidents())
                    .thenComparing(score -> score.technician().getFullName());

    @Override
    public final List<TechnicianScore> rank(Incident incident, List<AssignmentCandidate> candidates) {
        Validate.requireNonNull(incident, "Incident");
        Validate.requireNonNull(candidates, "Candidates");
        return candidates.stream()
                .filter(candidate -> isEligible(incident, candidate))
                .map(candidate -> {
                    Evaluation evaluation = evaluate(incident, candidate);
                    return new TechnicianScore(candidate, evaluation.score(), List.of(evaluation.reason()));
                })
                .sorted(BEST_FIRST)
                .toList();
    }

    /**
     * Hook: who may be considered at all. The default excludes technicians who are unavailable, at capacity,
     * deactivated, or already assigned to this incident. Subclasses may narrow it further but should call
     * {@code super.isEligible} first.
     */
    protected boolean isEligible(Incident incident, AssignmentCandidate candidate) {
        return candidate.technician().isAssignable(candidate.activeIncidents())
                && !candidate.technician().equals(incident.getAssignee());
    }

    /** Hook: how good a match this candidate is, 0.0–1.0, with a reason. */
    @Override
    public abstract Evaluation evaluate(Incident incident, AssignmentCandidate candidate);
}
