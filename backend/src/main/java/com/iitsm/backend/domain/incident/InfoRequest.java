package com.iitsm.backend.domain.incident;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.common.exception.BusinessRuleException;
import com.iitsm.backend.domain.user.Technician;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A technician's question to the reporter ("Which browser are you using?") and the reporter's answer.
 *
 * <p>Modelled as an object rather than a plain note so the system knows when it is waiting for the reporter.
 * That is what puts the incident ON_HOLD and pauses the SLA clock.
 */
@Entity
@Table(name = "incident_info_requests")
public class InfoRequest {

    public static final int MAX_QUESTION_LENGTH = 1000;
    public static final int MAX_ANSWER_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asked_by_id", nullable = false)
    private Technician askedBy;

    @Column(nullable = false, length = MAX_QUESTION_LENGTH)
    private String question;

    @Column(name = "asked_at", nullable = false)
    private Instant askedAt;

    @Column(length = MAX_ANSWER_LENGTH)
    private String answer;

    @Column(name = "answered_at")
    private Instant answeredAt;

    /** Required by JPA; not for application code. */
    protected InfoRequest() {
    }

    InfoRequest(Technician askedBy, String question, Instant askedAt) {
        this.askedBy = Validate.requireNonNull(askedBy, "Asked by");
        this.question = Validate.requireText(question, "Question", MAX_QUESTION_LENGTH);
        this.askedAt = Validate.requireNonNull(askedAt, "Asked at");
    }

    void answer(String answerText, Instant at) {
        if (!isOpen()) {
            throw new BusinessRuleException("INFO_REQUEST_ANSWERED", "This information request was already answered");
        }
        this.answer = Validate.requireText(answerText, "Answer", MAX_ANSWER_LENGTH);
        this.answeredAt = Validate.requireNonNull(at, "Answered at");
    }

    public boolean isOpen() {
        return answeredAt == null;
    }

    public Long getId() {
        return id;
    }

    public Technician getAskedBy() {
        return askedBy;
    }

    public String getQuestion() {
        return question;
    }

    public Instant getAskedAt() {
        return askedAt;
    }

    public String getAnswer() {
        return answer;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
