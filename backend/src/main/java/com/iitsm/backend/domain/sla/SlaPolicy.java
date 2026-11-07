package com.iitsm.backend.domain.sla;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.common.exception.InvalidInputException;
import com.iitsm.backend.domain.catalog.ItService;
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

import java.time.Duration;

/**
 * Response and resolution targets for one priority level, optionally for one specific service.
 *
 * <p><b>Specific beats general:</b> a P1 on the Payment service can have tighter targets than a P1 elsewhere.
 * When both a service-specific and a generic policy exist, the service-specific one applies.
 */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    public static final double DEFAULT_WARNING_THRESHOLD = 0.75;
    public static final double MIN_WARNING_THRESHOLD = 0.50;
    public static final double MAX_WARNING_THRESHOLD = 0.95;
    static final long MAX_RESPONSE_MINUTES = Duration.ofDays(7).toMinutes();
    static final long MAX_RESOLUTION_MINUTES = Duration.ofDays(30).toMinutes();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private PriorityLevel priority;

    /** {@code null} means "applies to every service". */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ItService service;

    @Column(name = "response_minutes", nullable = false)
    private long responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private long resolutionMinutes;

    /** Fraction of the time elapsed at which a warning is raised, e.g. 0.75. */
    @Column(name = "warning_threshold", nullable = false)
    private double warningThreshold;

    /** Required by JPA; not for application code. */
    protected SlaPolicy() {
    }

    public SlaPolicy(PriorityLevel priority, ItService service, Duration responseTarget, Duration resolutionTarget,
                     double warningThreshold) {
        this.priority = Validate.requireNonNull(priority, "SLA priority");
        this.service = service;
        updateTargets(responseTarget, resolutionTarget);
        changeWarningThreshold(warningThreshold);
    }

    /** A generic policy for all services with the default 75 % warning threshold. */
    public static SlaPolicy standard(PriorityLevel priority, Duration responseTarget, Duration resolutionTarget) {
        return new SlaPolicy(priority, null, responseTarget, resolutionTarget, DEFAULT_WARNING_THRESHOLD);
    }

    // ----- behaviour ---------------------------------------------------------------------------------------------

    /**
     * Changes the targets. Incidents that already started their SLA keep the targets they started with
     * (see {@link SlaTracker}): changing a policy never moves existing deadlines retroactively.
     */
    public void updateTargets(Duration responseTarget, Duration resolutionTarget) {
        long response = toMinutes(responseTarget, "Response target", MAX_RESPONSE_MINUTES);
        long resolution = toMinutes(resolutionTarget, "Resolution target", MAX_RESOLUTION_MINUTES);
        if (resolution < response) {
            throw new InvalidInputException("Resolution target cannot be shorter than the response target");
        }
        this.responseMinutes = response;
        this.resolutionMinutes = resolution;
    }

    public void changeWarningThreshold(double threshold) {
        if (Double.isNaN(threshold) || threshold < MIN_WARNING_THRESHOLD || threshold > MAX_WARNING_THRESHOLD) {
            throw new InvalidInputException("Warning threshold must be between "
                    + MIN_WARNING_THRESHOLD + " and " + MAX_WARNING_THRESHOLD);
        }
        this.warningThreshold = threshold;
    }

    /** True if this policy covers incidents of {@code level} on {@code incidentService}. */
    public boolean appliesTo(PriorityLevel level, ItService incidentService) {
        return priority == level && (service == null || service.equals(incidentService));
    }

    public boolean isServiceSpecific() {
        return service != null;
    }

    private static long toMinutes(Duration duration, String field, long max) {
        Validate.requireNonNull(duration, field);
        long minutes = duration.toMinutes();
        if (minutes < 1 || minutes > max) {
            throw new InvalidInputException(field + " must be between 1 minute and " + max + " minutes");
        }
        return minutes;
    }

    // ----- read access -------------------------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public PriorityLevel getPriority() {
        return priority;
    }

    public ItService getService() {
        return service;
    }

    public Duration getResponseTarget() {
        return Duration.ofMinutes(responseMinutes);
    }

    public Duration getResolutionTarget() {
        return Duration.ofMinutes(resolutionMinutes);
    }

    public double getWarningThreshold() {
        return warningThreshold;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof SlaPolicy that && getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return SlaPolicy.class.hashCode();
    }

    @Override
    public String toString() {
        return "SlaPolicy{" + priority + (service == null ? ", all services" : ", " + service.getName())
                + ", response=" + responseMinutes + "m, resolution=" + resolutionMinutes + "m}";
    }
}
