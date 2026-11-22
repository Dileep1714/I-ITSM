package com.iitsm.backend.domain.prediction;

import java.util.List;

/**
 * <b>Strategy</b> for forecasting SLA breaches. The service depends only on this interface, so a different model
 * (for example a machine-learned one) can replace {@link HistoricalBreachPredictor} without touching callers.
 */
public interface BreachPredictor {

    /**
     * Forecasts whether the incident described by {@code context} will miss its resolution deadline.
     *
     * @param history resolved incidents to learn from (may be empty)
     */
    BreachForecast forecast(PredictionContext context, List<ResolutionSample> history);
}
