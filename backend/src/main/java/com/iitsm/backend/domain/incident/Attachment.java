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
import java.util.Set;

/**
 * A photo or screenshot attached to an incident (for example, a picture of an error message taken with the phone).
 *
 * <p>Deliberately <em>not</em> part of the {@link Incident} aggregate: images are large, and loading an incident
 * (which happens on every action) must not drag their bytes along. It references the incident instead, and the
 * repository lists attachments without reading {@link #content}.
 */
@Entity
@Table(name = "incident_attachments")
public class Attachment {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_PER_INCIDENT = 10;
    public static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA; not for application code. */
    protected Attachment() {
    }

    public Attachment(Incident incident, User uploadedBy, String fileName, String contentType, byte[] content,
                      Instant createdAt) {
        this.incident = Validate.requireNonNull(incident, "Incident");
        this.uploadedBy = Validate.requireNonNull(uploadedBy, "Uploader");
        this.fileName = Validate.requireText(fileName, "File name", 200);
        this.contentType = Validate.requireNonNull(contentType, "Content type");
        this.content = Validate.requireNonNull(content, "Content");
        this.sizeBytes = content.length;
        this.createdAt = Validate.requireNonNull(createdAt, "Upload time");
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public byte[] getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
