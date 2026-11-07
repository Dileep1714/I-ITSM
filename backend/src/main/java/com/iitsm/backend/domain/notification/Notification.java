package com.iitsm.backend.domain.notification;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.user.User;
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

/** An in-app notification in a user's inbox (the bell icon). */
@Entity
@Table(name = "notifications")
public class Notification {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_MESSAGE_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "incident_number", length = 20)
    private String incidentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IncidentEventType type;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    /** Required by JPA; not for application code. */
    protected Notification() {
    }

    public Notification(User recipient, Long incidentId, String incidentNumber, IncidentEventType type,
                        String title, String message, Instant createdAt) {
        this.recipient = Validate.requireNonNull(recipient, "Recipient");
        this.incidentId = incidentId;
        this.incidentNumber = incidentNumber;
        this.type = Validate.requireNonNull(type, "Notification type");
        this.title = clip(Validate.requireText(title, "Title", Integer.MAX_VALUE), MAX_TITLE_LENGTH);
        this.message = clip(Validate.requireText(message, "Message", Integer.MAX_VALUE), MAX_MESSAGE_LENGTH);
        this.createdAt = Validate.requireNonNull(createdAt, "Created at");
    }

    /** Idempotent: marking an already-read notification keeps the original read time. */
    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = Validate.requireNonNull(at, "Read at");
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public IncidentEventType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
