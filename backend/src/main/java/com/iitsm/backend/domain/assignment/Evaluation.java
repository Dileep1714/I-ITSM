package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.common.exception.InvalidInputException;

/**
 * One scorer's verdict on one candidate: a score from 0.0 (worst) to 1.0 (best) and the reason in plain words,
 * e.g. {@code "Network skill 5/5"}.
 */
public record Evaluation(double score, String reason) {

    public Evaluation {
        if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
            throw new InvalidInputException("Evaluation score must be between 0 and 1 (was " + score + ")");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("Evaluation reason is required");
        }
    }
}
