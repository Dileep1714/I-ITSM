package com.iitsm.backend.domain.priority;

import com.iitsm.backend.common.exception.InvalidInputException;

/**
 * Tunable numbers for {@link WeightedScoreCalculator}. Kept out of the code so they can be adjusted (and justified
 * in the report) without changing the algorithm.
 *
 * @param p1Threshold         minimum score for P1
 * @param p2Threshold         minimum score for P2
 * @param p3Threshold         minimum score for P3 (anything lower is P4)
 * @param smallGroupMaxUsers  up to this many affected users, the user factor is 1.0
 * @param mediumGroupMaxUsers up to this many, {@code mediumGroupFactor} applies; above it, {@code largeGroupFactor}
 */
public record WeightedScoringConfig(double p1Threshold, double p2Threshold, double p3Threshold,
                                    int smallGroupMaxUsers, int mediumGroupMaxUsers,
                                    double mediumGroupFactor, double largeGroupFactor) {

    /**
     * Defaults. With factors 1–4, the base score Impact × Urgency × Criticality ranges from 1 to 64.
     * <ul>
     *   <li><b>36</b> = for example HIGH × HIGH × MISSION_CRITICAL: a serious outage of a key service → P1</li>
     *   <li><b>18</b> = for example HIGH × HIGH × MEDIUM: significant, but not business-stopping → P2</li>
     *   <li><b>8</b> = MEDIUM × MEDIUM × MEDIUM: the "everything average" case → P3</li>
     * </ul>
     * Many affected users multiply the score (×1.25 above 10 users, ×1.5 above 100), so wide-impact incidents rise.
     */
    public static final WeightedScoringConfig DEFAULT = new WeightedScoringConfig(36, 18, 8, 10, 100, 1.25, 1.5);

    public WeightedScoringConfig {
        if (!(p1Threshold > p2Threshold && p2Threshold > p3Threshold && p3Threshold > 0)) {
            throw new InvalidInputException("Thresholds must satisfy P1 > P2 > P3 > 0");
        }
        if (!(smallGroupMaxUsers >= 1 && mediumGroupMaxUsers > smallGroupMaxUsers)) {
            throw new InvalidInputException("User group limits must satisfy 1 ≤ small < medium");
        }
        if (!(mediumGroupFactor >= 1.0 && largeGroupFactor >= mediumGroupFactor)) {
            throw new InvalidInputException("User factors must satisfy 1.0 ≤ medium ≤ large");
        }
    }
}
