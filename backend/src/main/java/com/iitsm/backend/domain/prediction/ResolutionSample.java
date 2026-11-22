package com.iitsm.backend.domain.prediction;

import java.time.Duration;

/**
 * One past incident as training data: how long it took to resolve (working time, pauses excluded) and the traits
 * the predictor matches on. Plain values, so the predictor needs no persistence.
 *
 * @param activeDuration time from the SLA start to resolution, minus time spent waiting for the reporter
 * @param categoryId     category, or {@code null} if it was never classified
 * @param priority       priority level name ("P1".."P4"), or {@code null}
 * @param technicianId   who resolved it, or {@code null}
 */
public record ResolutionSample(Duration activeDuration, Long categoryId, String priority, Long technicianId) {
}
