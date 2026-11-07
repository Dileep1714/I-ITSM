package com.iitsm.backend.domain.incident;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.user.User;
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
 * A comment on an incident: investigation notes from technicians, or extra information from the reporter.
 *
 * <p><b>Composition:</b> created only through {@link Incident#addNote}, and deleted with the incident.
 * <b>Internal</b> notes are visible to support staff only, never to the employee who reported the incident.
 */
@Entity
@Table(name = "incident_notes")
public class InvestigationNote {

    public static final int MAX_TEXT_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = MAX_TEXT_LENGTH)
    private String text;

    @Column(name = "internal_only", nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA; not for application code. */
    protected InvestigationNote() {
    }

    InvestigationNote(User author, String text, boolean internal, Instant createdAt) {
        this.author = Validate.requireNonNull(author, "Note author");
        this.text = Validate.requireText(text, "Note text", MAX_TEXT_LENGTH);
        this.internal = internal;
        this.createdAt = Validate.requireNonNull(createdAt, "Note time");
    }

    public Long getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public boolean isInternal() {
        return internal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
