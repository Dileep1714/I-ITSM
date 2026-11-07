package com.iitsm.backend.domain.sla;

import com.iitsm.backend.common.Validate;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Tracks one incident's progress against its SLA: deadlines, response and resolution times, and pauses.
 *
 * <p><b>Composition:</b> embedded in {@code Incident}; it has no life of its own.
 *
 * <p><b>Design decisions</b>
 * <ul>
 *   <li><b>Snapshot of targets.</b> When the SLA starts, the policy's targets are copied here. Editing a policy
 *       later does not silently move the deadlines of incidents already in progress.</li>
 *   <li><b>The clock starts when the incident was reported,</b> not when it was classified: the user has been
 *       waiting since they reported it.</li>
 *   <li><b>Pausing.</b> While the technician waits for information from the reporter, the resolution clock stops.
 *       The response target is unaffected (a response has already happened by then).</li>
 *   <li><b>Time is passed in</b> ({@code Instant now}) rather than read from the system clock. That makes every
 *       rule testable to the second.</li>
 * </ul>
 */
@Embeddable
public class SlaTracker {

    @Column(name = "sla_policy_id")
    private Long policyId;

    @Column(name = "sla_response_minutes")
    private Long responseMinutes;

    @Column(name = "sla_resolution_minutes")
    private Long resolutionMinutes;

    @Column(name = "sla_warning_threshold")
    private Double warningThreshold;

    @Column(name = "sla_started_at")
    private Instant startedAt;

    @Column(name = "sla_responded_at")
    private Instant respondedAt;

    @Column(name = "sla_resolved_at")
    private Instant resolvedAt;

    @Column(name = "sla_paused_since")
    private Instant pausedSince;

    @Column(name = "sla_paused_seconds", nullable = false)
    private long pausedSeconds;

    // Remember which signals were already raised, so each warning/breach is reported exactly once.
    @Column(name = "sla_response_warned", nullable = false)
    private boolean responseWarned;

    @Column(name = "sla_resolution_warned", nullable = false)
    private boolean resolutionWarned;

    @Column(name = "sla_response_breach_recorded", nullable = false)
    private boolean responseBreachRecorded;

    @Column(name = "sla_resolution_breach_recorded", nullable = false)
    private boolean resolutionBreachRecorded;

    public SlaTracker() {
    }

    /**
     * Checks the deadlines and returns the thresholds crossed <em>since the last check</em>. Calling it every
     * minute (the SLA monitor) and after every user action is safe: a warning or breach is returned only once.
     */
    public Set<SlaSignal> evaluate(Instant now) {
        Set<SlaSignal> signals = EnumSet.noneOf(SlaSignal.class);
        if (!isStarted()) {
            return signals;
        }
        // Response target
        if (isResponseBreached(now)) {
            if (!responseBreachRecorded) {
                responseBreachRecorded = true;
                responseWarned = true; // a breach supersedes a pending warning
                signals.add(SlaSignal.RESPONSE_BREACHED);
            }
        } else if (respondedAt == null && !responseWarned && responseElapsedFraction(now) >= getWarningThreshold()) {
            responseWarned = true;
            signals.add(SlaSignal.RESPONSE_AT_RISK);
        }
        // Resolution target
        if (isResolutionBreached(now)) {
            if (!resolutionBreachRecorded) {
                resolutionBreachRecorded = true;
                resolutionWarned = true;
                signals.add(SlaSignal.RESOLUTION_BREACHED);
            }
        } else if (resolvedAt == null && !isPaused() && !resolutionWarned
                && resolutionElapsedFraction(now) >= getWarningThreshold()) {
            resolutionWarned = true;
            signals.add(SlaSignal.RESOLUTION_AT_RISK);
        }
        return signals;
    }

    // ----- lifecycle ---------------------------------------------------------------------------------------------

    /** Starts tracking against {@code policy}, with the clock running from {@code clockStart}. */
    public void start(SlaPolicy policy, Instant clockStart) {
        this.startedAt = Validate.requireNonNull(clockStart, "SLA start time");
        applyTargets(policy);
    }

    /** New targets after a priority change. The clock start, response and pauses are kept. */
    public void retarget(SlaPolicy policy) {
        applyTargets(policy);
    }

    private void applyTargets(SlaPolicy policy) {
        Validate.requireNonNull(policy, "SLA policy");
        this.policyId = policy.getId();
        this.responseMinutes = policy.getResponseTarget().toMinutes();
        this.resolutionMinutes = policy.getResolutionTarget().toMinutes();
        this.warningThreshold = policy.getWarningThreshold();
    }

    /** The first acknowledgement counts; later ones (e.g. after reassignment) do not reset it. */
    public void recordResponse(Instant at) {
        if (respondedAt == null) {
            respondedAt = Validate.requireNonNull(at, "Response time");
        }
    }

    public void pause(Instant at) {
        if (pausedSince == null) {
            pausedSince = Validate.requireNonNull(at, "Pause time");
        }
    }

    public void resume(Instant at) {
        if (pausedSince != null) {
            pausedSeconds += Duration.between(pausedSince, Validate.requireNonNull(at, "Resume time")).getSeconds();
            pausedSince = null;
        }
    }

    public void recordResolution(Instant at) {
        resume(at);
        resolvedAt = Validate.requireNonNull(at, "Resolution time");
    }

    /** The reporter rejected the resolution: the resolution clock runs again. */
    public void reopen() {
        resolvedAt = null;
    }

    // ----- queries -----------------------------------------------------------------------------------------------

    public boolean isStarted() {
        return startedAt != null;
    }

    public boolean isPaused() {
        return pausedSince != null;
    }

    /** Response deadline, or {@code null} before the SLA starts. */
    public Instant responseDue() {
        return isStarted() ? startedAt.plus(Duration.ofMinutes(responseMinutes)) : null;
    }

    /** Resolution deadline as of {@code now}: the target plus all time spent paused. {@code null} before start. */
    public Instant resolutionDue(Instant now) {
        return isStarted() ? startedAt.plus(Duration.ofMinutes(resolutionMinutes)).plus(pausedTotal(now)) : null;
    }

    public Duration pausedTotal(Instant now) {
        long seconds = pausedSeconds;
        if (pausedSince != null) {
            seconds += Duration.between(pausedSince, now).getSeconds();
        }
        return Duration.ofSeconds(seconds);
    }

    public boolean isResponseBreached(Instant now) {
        if (!isStarted()) {
            return false;
        }
        Instant measuredAt = respondedAt != null ? respondedAt : now;
        return measuredAt.isAfter(responseDue());
    }

    public boolean isResolutionBreached(Instant now) {
        if (!isStarted()) {
            return false;
        }
        Instant measuredAt = resolvedAt != null ? resolvedAt : now;
        return measuredAt.isAfter(resolutionDue(measuredAt));
    }

    /** Share of the resolution target used so far, excluding paused time. 0.0 = just started, 1.0 = deadline. */
    public double resolutionElapsedFraction(Instant now) {
        if (!isStarted()) {
            return 0.0;
        }
        Instant end = resolvedAt != null ? resolvedAt : now;
        long activeSeconds = Duration.between(startedAt, end).getSeconds() - pausedTotal(end).getSeconds();
        return Math.max(0.0, (double) activeSeconds / Duration.ofMinutes(resolutionMinutes).getSeconds());
    }

    /** Share of the response target used, while no response has been made yet. */
    public double responseElapsedFraction(Instant now) {
        if (!isStarted()) {
            return 0.0;
        }
        Instant end = respondedAt != null ? respondedAt : now;
        return Math.max(0.0, (double) Duration.between(startedAt, end).getSeconds()
                / Duration.ofMinutes(responseMinutes).getSeconds());
    }

    public SlaStatus status(Instant now) {
        if (!isStarted()) {
            return SlaStatus.NOT_STARTED;
        }
        if (isResponseBreached(now) || isResolutionBreached(now)) {
            return SlaStatus.BREACHED;
        }
        if (resolvedAt != null) {
            return SlaStatus.MET;
        }
        if (isPaused()) {
            return SlaStatus.PAUSED;
        }
        boolean responseAtRisk = respondedAt == null && responseElapsedFraction(now) >= getWarningThreshold();
        boolean resolutionAtRisk = resolutionElapsedFraction(now) >= getWarningThreshold();
        return responseAtRisk || resolutionAtRisk ? SlaStatus.AT_RISK : SlaStatus.ON_TRACK;
    }

    // ----- read access -------------------------------------------------------------------------------------------

    public Long getPolicyId() {
        return policyId;
    }

    public double getWarningThreshold() {
        return warningThreshold != null ? warningThreshold : SlaPolicy.DEFAULT_WARNING_THRESHOLD;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
