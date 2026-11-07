package com.iitsm.backend.domain.sla;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.PriorityLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A permanent record that an incident missed a deadline. At most one per incident and kind. */
@Entity
@Table(name = "sla_violations")
public class SlaViolation {

    public enum Kind {
        RESPONSE,
        RESOLUTION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    /** Copied from the incident at detection time, so later priority changes don't rewrite the record. */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority_level", nullable = false, length = 2)
    private PriorityLevel priorityLevel;

    @Column(nullable = false)
    private Instant deadline;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /** Required by JPA; not for application code. */
    protected SlaViolation() {
    }

    public SlaViolation(Incident incident, Kind kind, Instant deadline, Instant detectedAt) {
        this.incident = Validate.requireNonNull(incident, "Incident");
        this.kind = Validate.requireNonNull(kind, "Kind");
        this.priorityLevel = Validate.requireNonNull(incident.getPriority(), "Incident priority").getLevel();
        this.deadline = Validate.requireNonNull(deadline, "Deadline");
        this.detectedAt = Validate.requireNonNull(detectedAt, "Detected at");
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public Kind getKind() {
        return kind;
    }

    public PriorityLevel getPriorityLevel() {
        return priorityLevel;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }
}
