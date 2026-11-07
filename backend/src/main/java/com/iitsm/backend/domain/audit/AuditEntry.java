package com.iitsm.backend.domain.audit;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.incident.IncidentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One line of an incident's history: "10:07 – Assigned to Technician Ravi Kumar (by System)".
 *
 * <p><b>Immutable and append-only:</b> no setters, no update methods, and a database trigger rejects updates.
 * An audit trail that can be edited is not an audit trail.
 *
 * <p>Stores ids and copies of names rather than entity references, so history reads quickly and stays correct
 * even if a user is later renamed or deleted.
 */
@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final String SYSTEM_ACTOR = "System";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false, updatable = false)
    private Long incidentId;

    @Column(name = "incident_number", nullable = false, updatable = false, length = 20)
    private String incidentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private IncidentEventType action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false, length = 20)
    private IncidentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", updatable = false, length = 20)
    private IncidentStatus newStatus;

    @Column(nullable = false, updatable = false, length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "actor_name", nullable = false, updatable = false, length = 120)
    private String actorName;

    @Column(name = "visible_to_reporter", nullable = false, updatable = false)
    private boolean visibleToReporter;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** Required by JPA; not for application code. */
    protected AuditEntry() {
    }

    /**
     * @param actorId   {@code null} when the system acted (e.g. the SLA monitor)
     * @param actorName copied at the time of the action
     */
    public AuditEntry(Long incidentId, String incidentNumber, IncidentEventType action,
                      IncidentStatus previousStatus, IncidentStatus newStatus, String description,
                      Long actorId, String actorName, boolean visibleToReporter, Instant occurredAt) {
        this.incidentId = Validate.requireNonNull(incidentId, "Incident id");
        this.incidentNumber = Validate.requireText(incidentNumber, "Incident number", 20);
        this.action = Validate.requireNonNull(action, "Action");
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.description = truncate(Validate.requireText(description, "Description", Integer.MAX_VALUE));
        this.actorId = actorId;
        this.actorName = actorName != null ? actorName : SYSTEM_ACTOR;
        this.visibleToReporter = visibleToReporter;
        this.occurredAt = Validate.requireNonNull(occurredAt, "Occurred at");
    }

    /** Free text (e.g. a long answer) may exceed the column; the audit keeps the start rather than failing. */
    private static String truncate(String text) {
        return text.length() <= MAX_DESCRIPTION_LENGTH ? text : text.substring(0, MAX_DESCRIPTION_LENGTH - 1) + "…";
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public IncidentEventType getAction() {
        return action;
    }

    public IncidentStatus getPreviousStatus() {
        return previousStatus;
    }

    public IncidentStatus getNewStatus() {
        return newStatus;
    }

    public String getDescription() {
        return description;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public boolean isVisibleToReporter() {
        return visibleToReporter;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
