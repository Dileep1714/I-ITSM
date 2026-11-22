package com.iitsm.backend.domain.prediction;

import java.time.Duration;

/**
 * The open incident being forecast, reduced to what the predictor needs.
 *
 * @param categoryId     its category, or {@code null}
 * @param categoryName   for the explanation, e.g. "Database"
 * @param priority       "P1".."P4", or {@code null}
 * @param technicianId   current assignee, or {@code null}
 * @param technicianName for the explanation
 * @param elapsed        working time already spent (pauses excluded)
 * @param target         the resolution target from its SLA
 * @param queueAhead     open incidents in the assignee's queue that come before this one
 */
public record PredictionContext(Long categoryId, String categoryName, String priority, Long technicianId,
                                String technicianName, Duration elapsed, Duration target, int queueAhead) {
}
