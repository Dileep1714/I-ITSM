package com.iitsm.backend.domain.incident;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.user.Technician;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

/**
 * What fixed the incident. Immutable value object, part of the incident (composition).
 * Past resolutions feed the "suggested resolution" intelligent feature.
 */
@Embeddable
public class Resolution {

    public static final int MAX_TEXT_LENGTH = 2000;

    @Column(name = "resolution_summary", length = MAX_TEXT_LENGTH)
    private String summary;

    @Column(name = "resolution_root_cause", length = MAX_TEXT_LENGTH)
    private String rootCause;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_code", length = 30)
    private ResolutionCode code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private Technician resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Required by JPA; not for application code. */
    protected Resolution() {
    }

    Resolution(String summary, String rootCause, ResolutionCode code, Technician resolvedBy, Instant resolvedAt) {
        this.summary = Validate.requireText(summary, "Resolution summary", MAX_TEXT_LENGTH);
        this.rootCause = Validate.optionalText(rootCause, "Root cause", MAX_TEXT_LENGTH);
        this.code = Validate.requireNonNull(code, "Resolution code");
        this.resolvedBy = Validate.requireNonNull(resolvedBy, "Resolved by");
        this.resolvedAt = Validate.requireNonNull(resolvedAt, "Resolved at");
    }

    public String getSummary() {
        return summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public ResolutionCode getCode() {
        return code;
    }

    public Technician getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
