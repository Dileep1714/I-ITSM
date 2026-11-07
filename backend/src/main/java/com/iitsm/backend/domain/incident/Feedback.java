package com.iitsm.backend.domain.incident;

import com.iitsm.backend.common.Validate;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

/**
 * The reporter's verdict on a resolution: accepted with a 1–5 rating, or rejected with a reason.
 * Immutable value object created through the two factory methods, so an "accepted" feedback always has a rating.
 */
@Embeddable
public class Feedback {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_COMMENT_LENGTH = 1000;

    @Column(name = "feedback_accepted")
    private Boolean accepted;

    @Column(name = "feedback_rating")
    private Integer rating;

    @Column(name = "feedback_comment", length = MAX_COMMENT_LENGTH)
    private String comment;

    @Column(name = "feedback_given_at")
    private Instant givenAt;

    /** Required by JPA; not for application code. */
    protected Feedback() {
    }

    private Feedback(boolean accepted, Integer rating, String comment, Instant givenAt) {
        this.accepted = accepted;
        this.rating = rating;
        this.comment = comment;
        this.givenAt = Validate.requireNonNull(givenAt, "Feedback time");
    }

    static Feedback accepted(int rating, String comment, Instant at) {
        Validate.requireRange(rating, MIN_RATING, MAX_RATING, "Rating");
        return new Feedback(true, rating, Validate.optionalText(comment, "Feedback comment", MAX_COMMENT_LENGTH), at);
    }

    static Feedback rejected(String reason, Instant at) {
        return new Feedback(false, null, Validate.requireText(reason, "Rejection reason", MAX_COMMENT_LENGTH), at);
    }

    public boolean isAccepted() {
        return Boolean.TRUE.equals(accepted);
    }

    /** 1–5 for accepted feedback, {@code null} for a rejection. */
    public Integer getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getGivenAt() {
        return givenAt;
    }
}
