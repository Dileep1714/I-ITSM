package com.iitsm.backend.domain.prediction;

import java.time.Duration;
import java.util.List;

/**
 * A prediction of whether an open incident will miss its resolution deadline.
 *
 * @param probability       chance (0 to 1) that the resolution deadline will be missed
 * @param risk              {@code probability} bucketed for display and alerting
 * @param expectedRemaining most likely remaining working time until resolution
 * @param sampleSize        how many past incidents the estimate rests on (pseudo-samples from the SLA target excluded)
 * @param basis             which past incidents were compared, e.g. "Database P2 incidents"
 * @param reasons           the factors behind the number, in plain words, most important first
 */
public record BreachForecast(double probability, RiskLevel risk, Duration expectedRemaining, int sampleSize,
                             String basis, List<String> reasons) {

    public BreachForecast {
        if (probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Probability must be between 0 and 1");
        }
        reasons = List.copyOf(reasons);
    }

    /** True when the history behind the estimate is thin, so it leans on the SLA target. */
    public boolean lowConfidence() {
        return sampleSize < HistoricalBreachPredictor.MIN_SAMPLES;
    }
}
