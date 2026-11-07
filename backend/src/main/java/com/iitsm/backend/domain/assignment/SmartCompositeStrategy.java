package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.common.exception.InvalidInputException;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.PriorityLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Smart technician recommendation": a <b>weighted combination</b> of other scorers.
 *
 * <p><b>Composite pattern:</b> this strategy is built from other strategies and is itself a strategy, so callers
 * cannot tell it apart from a simple one. The default mix is skill 40 %, load 30 %, experience 30 %. For P1 and P2
 * incidents, experience counts 1.5× more: a critical outage should go to someone who has fixed it before.
 *
 * <p>Every part's reason is kept, so the UI can explain a recommendation in full:
 * {@code "0.82: Network skill 5/5 (×0.40), 1 of 5 slots in use (×0.30), resolved 7 Payment incidents… (×0.30)"}.
 */
public class SmartCompositeStrategy extends ScoringAssignmentStrategy {

    public static final String KEY = "SMART";
    static final double CRITICAL_EXPERIENCE_BOOST = 1.5;

    private final Map<CandidateScorer, Double> weights;
    private final CandidateScorer experienceScorer;

    /**
     * @param weights          scorers and their relative weights (normalized automatically; must be positive)
     * @param experienceScorer the scorer whose weight is boosted for P1/P2 incidents, or {@code null} for no boost
     */
    public SmartCompositeStrategy(Map<CandidateScorer, Double> weights, CandidateScorer experienceScorer) {
        Validate.requireNonNull(weights, "Weights");
        if (weights.isEmpty()) {
            throw new InvalidInputException("A composite strategy needs at least one scorer");
        }
        weights.values().forEach(weight -> {
            if (weight == null || weight <= 0 || weight.isNaN()) {
                throw new InvalidInputException("Scorer weights must be positive");
            }
        });
        this.weights = new LinkedHashMap<>(weights);
        this.experienceScorer = experienceScorer;
    }

    /** The default mix: skill 0.4, load 0.3, experience 0.3. */
    public static SmartCompositeStrategy standard() {
        CandidateScorer experience = new ExperienceBasedStrategy();
        Map<CandidateScorer, Double> weights = new LinkedHashMap<>();
        weights.put(new SkillBasedStrategy(), 0.4);
        weights.put(new LoadBasedStrategy(), 0.3);
        weights.put(experience, 0.3);
        return new SmartCompositeStrategy(weights, experience);
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Smart recommendation";
    }

    @Override
    public String description() {
        return "Balances skill, workload and experience with the affected service; experience counts more for "
                + "P1/P2 incidents.";
    }

    @Override
    public Evaluation evaluate(Incident incident, AssignmentCandidate candidate) {
        Map<CandidateScorer, Double> effective = effectiveWeights(incident);
        double totalWeight = effective.values().stream().mapToDouble(Double::doubleValue).sum();

        double score = 0.0;
        List<String> parts = new ArrayList<>();
        for (Map.Entry<CandidateScorer, Double> entry : effective.entrySet()) {
            double normalizedWeight = entry.getValue() / totalWeight;
            Evaluation part = entry.getKey().evaluate(incident, candidate);
            score += normalizedWeight * part.score();
            parts.add(String.format(Locale.ROOT, "%s (×%.2f)", part.reason(), normalizedWeight));
        }
        double bounded = Math.min(1.0, Math.max(0.0, score)); // guard against floating-point drift
        return new Evaluation(bounded, String.format(Locale.ROOT, "%.2f: %s", bounded, String.join(", ", parts)));
    }

    private Map<CandidateScorer, Double> effectiveWeights(Incident incident) {
        boolean critical = incident.getPriority() != null
                && incident.getPriority().getLevel().isHigherThan(PriorityLevel.P3);
        if (!critical || experienceScorer == null || !weights.containsKey(experienceScorer)) {
            return weights;
        }
        Map<CandidateScorer, Double> boosted = new LinkedHashMap<>(weights);
        boosted.computeIfPresent(experienceScorer, (scorer, weight) -> weight * CRITICAL_EXPERIENCE_BOOST);
        return boosted;
    }
}
