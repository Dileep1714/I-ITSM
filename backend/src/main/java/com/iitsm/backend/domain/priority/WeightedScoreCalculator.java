package com.iitsm.backend.domain.priority;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.incident.Priority;
import com.iitsm.backend.domain.incident.PriorityLevel;

import java.util.Locale;

/**
 * Default priority algorithm:
 *
 * <pre>
 *   score = Impact (1–4) × Urgency (1–4) × Criticality (1–4) × userFactor
 *   userFactor = 1.0 (≤ 10 users) | 1.25 (≤ 100) | 1.5 (> 100)
 *   score ≥ 36 → P1,  ≥ 18 → P2,  ≥ 8 → P3,  otherwise P4
 * </pre>
 *
 * <p>Multiplying rather than adding means one low factor pulls the score down strongly: a total outage
 * (EXTENSIVE impact) of a LOW-criticality test service with LOW urgency scores 4 × 1 × 1 = 4 → P4, which
 * matches intuition better than an additive sum.
 */
public class WeightedScoreCalculator implements PriorityCalculator {

    public static final String KEY = "WEIGHTED_SCORE";

    private final WeightedScoringConfig config;

    public WeightedScoreCalculator() {
        this(WeightedScoringConfig.DEFAULT);
    }

    public WeightedScoreCalculator(WeightedScoringConfig config) {
        this.config = Validate.requireNonNull(config, "Scoring configuration");
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Weighted score";
    }

    @Override
    public String description() {
        return "Impact × Urgency × Business criticality, multiplied up when many users are affected.";
    }

    @Override
    public Priority calculate(PriorityFactors factors) {
        Validate.requireNonNull(factors, "Priority factors");
        int base = factors.impact().weight() * factors.urgency().weight() * factors.criticality().weight();
        double userFactor = userFactor(factors.affectedUsers());
        double score = base * userFactor;
        PriorityLevel level = levelFor(score);

        String rationale = String.format(Locale.ROOT,
                "Impact %s(%d) × Urgency %s(%d) × Criticality %s(%d) × %d users (×%.2f) = %.1f → %s %s",
                factors.impact(), factors.impact().weight(),
                factors.urgency(), factors.urgency().weight(),
                factors.criticality(), factors.criticality().weight(),
                factors.affectedUsers(), userFactor, score, level, level.label());
        return new Priority(level, score, rationale);
    }

    double userFactor(int affectedUsers) {
        if (affectedUsers <= config.smallGroupMaxUsers()) {
            return 1.0;
        }
        return affectedUsers <= config.mediumGroupMaxUsers() ? config.mediumGroupFactor() : config.largeGroupFactor();
    }

    PriorityLevel levelFor(double score) {
        if (score >= config.p1Threshold()) {
            return PriorityLevel.P1;
        }
        if (score >= config.p2Threshold()) {
            return PriorityLevel.P2;
        }
        return score >= config.p3Threshold() ? PriorityLevel.P3 : PriorityLevel.P4;
    }
}
