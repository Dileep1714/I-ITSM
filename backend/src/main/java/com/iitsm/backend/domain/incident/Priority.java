package com.iitsm.backend.domain.incident;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.common.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/**
 * The result of priority calculation: a level (P1–P4), the numeric score it came from, and a human-readable
 * explanation such as {@code "Impact HIGH(3) × Urgency HIGH(3) × Criticality MISSION_CRITICAL(4) = 36 → P1"}.
 *
 * <p><b>Immutable value object:</b> equal when all fields are equal; never modified after creation. A new
 * priority replaces the old one. Storing the rationale makes every decision transparent to users and auditors.
 */
@Embeddable
public class Priority {

    public static final int MAX_RATIONALE_LENGTH = 300;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_level", length = 2)
    private PriorityLevel level;

    @Column(name = "priority_score")
    private double score;

    @Column(name = "priority_rationale", length = MAX_RATIONALE_LENGTH)
    private String rationale;

    /** Required by JPA; not for application code. */
    protected Priority() {
    }

    public Priority(PriorityLevel level, double score, String rationale) {
        this.level = Validate.requireNonNull(level, "Priority level");
        if (score < 0 || Double.isNaN(score)) {
            throw new InvalidInputException("Priority score must be zero or positive");
        }
        this.score = score;
        this.rationale = Validate.requireText(rationale, "Priority rationale", MAX_RATIONALE_LENGTH);
    }

    public PriorityLevel getLevel() {
        return level;
    }

    public double getScore() {
        return score;
    }

    public String getRationale() {
        return rationale;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Priority that
                && level == that.level
                && Double.compare(score, that.score) == 0
                && Objects.equals(rationale, that.rationale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, score, rationale);
    }

    @Override
    public String toString() {
        return level + " (" + level.label() + ", score " + score + ")";
    }
}
