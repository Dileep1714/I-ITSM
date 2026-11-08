package com.iitsm.backend.domain.incident;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.common.exception.AuthorizationException;
import com.iitsm.backend.common.exception.BusinessRuleException;
import com.iitsm.backend.common.exception.InvalidInputException;
import com.iitsm.backend.common.exception.InvalidStateTransitionException;
import com.iitsm.backend.common.exception.TechnicianUnavailableException;
import com.iitsm.backend.common.exception.WorkloadExceededException;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.sla.SlaSignal;
import com.iitsm.backend.domain.sla.SlaStatus;
import com.iitsm.backend.domain.sla.SlaTracker;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An IT incident: the core aggregate of I-ITSM.
 *
 * <h2>How the lifecycle works (State pattern)</h2>
 * Every public lifecycle method (e.g. {@link #acknowledge}) delegates to the object for the current status
 * ({@link IncidentState}). That state either performs the transition by calling one of the package-private
 * {@code apply…} methods below, or rejects it with {@link InvalidStateTransitionException}. There is no
 * {@code switch (status)} anywhere: NEW → CLOSED is impossible because {@code NewState} simply does not
 * implement {@code close}.
 *
 * <h2>Where the rules live</h2>
 * <ul>
 *   <li><b>States</b> decide <em>whether</em> an operation is allowed right now.</li>
 *   <li><b>This class</b> decides <em>who</em> may do it and <em>what</em> changes (who is the assignee, what
 *       happens to the SLA clock…).</li>
 * </ul>
 * Services coordinate (load data, pick a technician, publish events) but hold no incident rules. That is a
 * <em>rich domain model</em>.
 *
 * <h2>Relationships</h2>
 * Association: reporter, assignee, service, category, duplicate-of. Composition: priority, SLA tracker, notes,
 * information requests, resolution, feedback.
 */
@Entity
@Table(name = "incidents")
public class Incident {

    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MIN_DESCRIPTION_LENGTH = 10;
    public static final int MAX_DESCRIPTION_LENGTH = 4000;
    public static final int MAX_AFFECTED_USERS = 1_000_000;
    public static final int MAX_REASON_LENGTH = 500;
    /** A resolution the reporter neither accepts nor rejects is accepted automatically after this long. */
    public static final Duration AUTO_VERIFY_AFTER = Duration.ofHours(72);

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^INC-\\d{4}-\\d{6}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable reference, e.g. {@code INC-2026-000142}. */
    @Column(nullable = false, unique = true, length = 20)
    private String number;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Employee reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ItService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Impact impact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Urgency urgency;

    @Column(name = "affected_users", nullable = false)
    private int affectedUsers;

    @Embedded
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Technician assignee;

    @Embedded
    private SlaTracker sla;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "incident_id", nullable = false)
    @OrderBy("createdAt ASC")
    private List<InvestigationNote> notes = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "incident_id", nullable = false)
    @OrderBy("askedAt ASC")
    private List<InfoRequest> infoRequests = new ArrayList<>();

    @Embedded
    private Resolution resolution;

    @Embedded
    private Feedback feedback;

    /** Aggregation: a cancelled duplicate points at the incident it duplicates. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private Incident duplicateOf;

    @Column(name = "cancellation_reason", length = MAX_REASON_LENGTH)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Highest support level this incident has been escalated to; {@code null} if never escalated. */
    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_level", length = 20)
    private SupportLevel escalationLevel;

    @Column(name = "last_escalated_at")
    private Instant lastEscalatedAt;

    /** Optimistic locking: if two people save the same incident at once, the second save fails safely. */
    @Version
    private long version;

    /** Changes recorded since the last {@link #pullChanges()}. Not stored: they become events and audit entries. */
    @Transient
    private final List<IncidentChange> changes = new ArrayList<>();

    /** Required by JPA; not for application code. */
    protected Incident() {
    }

    private Incident(Builder builder) {
        this.number = requireNumber(builder.number);
        this.title = Validate.requireText(builder.title, "Title", MAX_TITLE_LENGTH);
        this.description = requireDescription(builder.description);
        this.reporter = Validate.requireNonNull(builder.reporter, "Reporter");
        if (!reporter.isActive()) {
            throw new BusinessRuleException("REPORTER_INACTIVE", "A deactivated user cannot report incidents");
        }
        this.service = Validate.requireNonNull(builder.service, "Affected service");
        this.impact = Validate.requireNonNull(builder.impact, "Impact");
        this.urgency = Validate.requireNonNull(builder.urgency, "Urgency");
        this.affectedUsers = Validate.requireRange(builder.affectedUsers, 1, MAX_AFFECTED_USERS, "Affected users");
        this.createdAt = Validate.requireNonNull(builder.reportedAt, "Reported at");
        this.updatedAt = createdAt;
        this.status = IncidentStatus.NEW;
        this.sla = new SlaTracker();
        changes.add(new IncidentChange(IncidentEventType.CREATED, null, IncidentStatus.NEW,
                "Reported by " + reporter.getFullName() + " on " + service.getName() + ": " + title, true, createdAt));
    }

    /** Builder pattern: many fields, several optional; all validated together in one place on {@link Builder#build}. */
    public static Builder builder() {
        return new Builder();
    }

    // =============================================================================================================
    // Lifecycle operations: each one asks the current state object to handle it
    // =============================================================================================================

    /** Sets category, priority and SLA. The SLA clock starts from the moment the incident was reported. */
    public void classify(Category category, Priority priority, SlaPolicy slaPolicy, Instant now) {
        state().classify(this, category, priority, slaPolicy, now);
    }

    /**
     * Assigns (or reassigns) a technician.
     *
     * @param technicianActiveIncidents the technician's current number of active incidents, used for the
     *                                  workload check
     */
    public void assign(Technician technician, int technicianActiveIncidents, Instant now) {
        state().assign(this, technician, technicianActiveIncidents, now);
    }

    /** The assignee accepts the incident. This is the "response" for SLA purposes. */
    public void acknowledge(Technician by, Instant now) {
        state().acknowledge(this, by, now);
    }

    public void startWork(Technician by, Instant now) {
        state().startWork(this, by, now);
    }

    /** Asks the reporter a question. The incident goes ON_HOLD and the resolution clock pauses. */
    public void requestInfo(Technician by, String question, Instant now) {
        state().requestInfo(this, by, question, now);
    }

    /** The reporter answers the open question. Work resumes and the clock restarts. */
    public void provideInfo(Employee by, String answer, Instant now) {
        state().provideInfo(this, by, answer, now);
    }

    public void resolve(Technician by, String summary, String rootCause, ResolutionCode code, Instant now) {
        state().resolve(this, by, summary, rootCause, code, now);
    }

    /** The reporter confirms the fix works and rates it 1–5. */
    public void acceptResolution(Employee by, int rating, String comment, Instant now) {
        state().acceptResolution(this, by, rating, comment, now);
    }

    /** The reporter says the problem is not fixed. The incident reopens and goes back to the technician. */
    public void rejectResolution(Employee by, String reason, Instant now) {
        state().rejectResolution(this, by, reason, now);
    }

    /** Called by the system when the reporter has not responded within {@link #AUTO_VERIFY_AFTER}. */
    public void autoVerify(Instant now) {
        state().autoVerify(this, now);
    }

    public void close(Instant now) {
        state().close(this, now);
    }

    public void cancel(String reason, Instant now) {
        state().cancel(this, reason, null, now);
    }

    /** Cancels this incident as a duplicate of {@code original}, keeping a link to it. */
    public void markDuplicateOf(Incident original, Instant now) {
        Validate.requireNonNull(original, "Original incident");
        if (original == this || (id != null && id.equals(original.getId()))) {
            throw new InvalidInputException("An incident cannot be a duplicate of itself");
        }
        if (!original.isOpen()) {
            throw new BusinessRuleException("ORIGINAL_NOT_OPEN",
                    "Can only link to an open incident; " + original.getNumber() + " is " + original.getStatus());
        }
        state().markDuplicate(this, original, now);
    }

    // =============================================================================================================
    // Operations that are not lifecycle transitions
    // =============================================================================================================

    /** Recalculated priority, e.g. after impact or urgency changed. SLA targets follow; the clock start is kept. */
    public void changePriority(Priority newPriority, SlaPolicy slaPolicy, Instant now) {
        requireOpen("CHANGE_PRIORITY");
        if (status == IncidentStatus.NEW) {
            throw new InvalidStateTransitionException(status.name(), "CHANGE_PRIORITY (classify the incident first)");
        }
        Validate.requireNonNull(newPriority, "Priority");
        requireMatchingPolicy(newPriority, slaPolicy);
        PriorityLevel previous = priority.getLevel();
        this.priority = newPriority;
        sla().retarget(slaPolicy);
        touch(now);
        if (previous != newPriority.getLevel()) {
            record(IncidentEventType.PRIORITY_CHANGED, status, "Priority changed from " + previous + " to "
                    + newPriority.getLevel() + " (" + newPriority.getRationale() + ")", true, now);
        }
    }

    /** Impact, urgency and the number of affected users can be corrected while the incident is open. */
    public void updateAssessment(Impact newImpact, Urgency newUrgency, int newAffectedUsers, Instant now) {
        requireOpen("UPDATE_ASSESSMENT");
        this.impact = Validate.requireNonNull(newImpact, "Impact");
        this.urgency = Validate.requireNonNull(newUrgency, "Urgency");
        this.affectedUsers = Validate.requireRange(newAffectedUsers, 1, MAX_AFFECTED_USERS, "Affected users");
        touch(now);
        record(IncidentEventType.DETAILS_UPDATED, status, "Assessment updated: impact " + impact + ", urgency "
                + urgency + ", " + affectedUsers + " users affected", true, now);
    }

    /** The reporter can correct the title and description until a technician has accepted the incident. */
    public void editDetails(Employee by, String newTitle, String newDescription, Instant now) {
        requireReporter(by, "edit this incident");
        if (status != IncidentStatus.NEW && status != IncidentStatus.CLASSIFIED && status != IncidentStatus.ASSIGNED) {
            throw new InvalidStateTransitionException(status.name(), "EDIT_DETAILS");
        }
        this.title = Validate.requireText(newTitle, "Title", MAX_TITLE_LENGTH);
        this.description = requireDescription(newDescription);
        touch(now);
        record(IncidentEventType.DETAILS_UPDATED, status, "Title and description edited by the reporter", true, now);
    }

    /**
     * Adds a comment. Employees may only comment on their own incidents and never see or write internal notes.
     * Support staff can comment on any incident.
     */
    public InvestigationNote addNote(User author, String text, boolean internal, Instant now) {
        Validate.requireNonNull(author, "Note author");
        if (status == IncidentStatus.CLOSED || status == IncidentStatus.CANCELLED) {
            throw new InvalidStateTransitionException(status.name(), "ADD_NOTE");
        }
        if (author.getRole() == Role.EMPLOYEE) {
            if (!author.equals(reporter)) {
                throw new AuthorizationException("Employees can only comment on incidents they reported");
            }
            if (internal) {
                throw new AuthorizationException("Employees cannot add internal notes");
            }
        }
        InvestigationNote note = new InvestigationNote(author, text, internal, now);
        notes.add(note);
        touch(now);
        record(IncidentEventType.NOTE_ADDED, status, (internal ? "Internal note" : "Comment") + " added by "
                + author.getFullName(), !internal, now);
        return note;
    }

    // =============================================================================================================
    // State transitions, called only by the IncidentState classes (package-private on purpose)
    // =============================================================================================================

    void applyClassification(Category newCategory, Priority newPriority, SlaPolicy slaPolicy, Instant now) {
        Validate.requireNonNull(newCategory, "Category");
        Validate.requireNonNull(newPriority, "Priority");
        if (!newCategory.isActive()) {
            throw new BusinessRuleException("CATEGORY_INACTIVE",
                    "Category '" + newCategory.getName() + "' is no longer in use");
        }
        requireMatchingPolicy(newPriority, slaPolicy);
        this.category = newCategory;
        this.priority = newPriority;
        sla().start(slaPolicy, createdAt);
        transition(IncidentStatus.CLASSIFIED, now, IncidentEventType.CLASSIFIED,
                "Classified as " + newCategory.getName() + ", priority " + newPriority.getLevel() + " "
                        + newPriority.getLevel().label() + " (" + newPriority.getRationale() + ")", true);
    }

    void applyAssignment(Technician technician, int technicianActiveIncidents, Instant now) {
        Validate.requireNonNull(technician, "Technician");
        if (technician.equals(assignee)) {
            throw new BusinessRuleException("ALREADY_ASSIGNED",
                    "Incident is already assigned to " + technician.getFullName());
        }
        if (!technician.isActive() || !technician.getAvailability().acceptsNewWork()) {
            throw new TechnicianUnavailableException(technician.getFullName());
        }
        if (!technician.hasCapacity(technicianActiveIncidents)) {
            throw new WorkloadExceededException(technician.getFullName(), technician.getMaxActiveIncidents());
        }
        Technician previous = this.assignee;
        this.assignee = technician;
        if (previous == null) {
            transition(IncidentStatus.ASSIGNED, now, IncidentEventType.ASSIGNED,
                    "Assigned to " + technician.getFullName(), true);
        } else {
            transition(IncidentStatus.ASSIGNED, now, IncidentEventType.REASSIGNED,
                    "Reassigned from " + previous.getFullName() + " to " + technician.getFullName(), true);
        }
    }

    void applyAcknowledgement(Technician by, Instant now) {
        requireAssignee(by, "acknowledge");
        sla().recordResponse(now);
        transition(IncidentStatus.ACKNOWLEDGED, now, IncidentEventType.ACKNOWLEDGED,
                by.getFullName() + " acknowledged the incident", true);
    }

    void applyStartWork(Technician by, Instant now) {
        requireAssignee(by, "start work on");
        transition(IncidentStatus.IN_PROGRESS, now, IncidentEventType.WORK_STARTED,
                by.getFullName() + " started investigating", true);
    }

    void applyInfoRequest(Technician by, String question, Instant now) {
        requireAssignee(by, "request information on");
        infoRequests.add(new InfoRequest(by, question, now));
        sla().pause(now);
        transition(IncidentStatus.ON_HOLD, now, IncidentEventType.INFO_REQUESTED,
                by.getFullName() + " asked: " + question, true);
    }

    void applyInfoProvided(Employee by, String answer, Instant now) {
        requireReporter(by, "answer questions on");
        InfoRequest openRequest = getOpenInfoRequest().orElseThrow(() ->
                new BusinessRuleException("NO_OPEN_INFO_REQUEST", "There is no open question to answer"));
        openRequest.answer(answer, now);
        sla().resume(now);
        transition(IncidentStatus.IN_PROGRESS, now, IncidentEventType.INFO_PROVIDED,
                by.getFullName() + " answered: " + answer, true);
    }

    void applyResolution(Technician by, String summary, String rootCause, ResolutionCode code, Instant now) {
        requireAssignee(by, "resolve");
        this.resolution = new Resolution(summary, rootCause, code, by, now);
        sla().recordResolution(now);
        transition(IncidentStatus.RESOLVED, now, IncidentEventType.RESOLVED,
                "Resolved by " + by.getFullName() + " (" + code + "): " + summary, true);
    }

    void applyAcceptance(Employee by, int rating, String comment, Instant now) {
        requireReporter(by, "accept the resolution of");
        this.feedback = Feedback.accepted(rating, comment, now);
        transition(IncidentStatus.VERIFIED, now, IncidentEventType.RESOLUTION_ACCEPTED,
                by.getFullName() + " confirmed the fix (rating " + rating + "/5)", true);
    }

    void applyRejection(Employee by, String reason, Instant now) {
        requireReporter(by, "reject the resolution of");
        this.feedback = Feedback.rejected(reason, now);
        notes.add(new InvestigationNote(by, "Resolution rejected: " + reason, false, now));
        this.resolution = null;
        sla().reopen();
        transition(IncidentStatus.IN_PROGRESS, now, IncidentEventType.RESOLUTION_REJECTED,
                by.getFullName() + " rejected the fix and reopened the incident: " + reason, true);
    }

    void applyAutoVerification(Instant now) {
        if (!isAutoVerifiable(now)) {
            throw new BusinessRuleException("AUTO_VERIFY_TOO_EARLY",
                    "Incident can be auto-verified only after " + AUTO_VERIFY_AFTER.toHours() + " hours");
        }
        transition(IncidentStatus.VERIFIED, now, IncidentEventType.AUTO_VERIFIED,
                "Verified automatically: no response from the reporter within " + AUTO_VERIFY_AFTER.toHours()
                        + " hours", true);
    }

    void applyClose(Instant now) {
        this.closedAt = Validate.requireNonNull(now, "Closing time");
        transition(IncidentStatus.CLOSED, now, IncidentEventType.CLOSED, "Incident closed", true);
    }

    void applyCancellation(String reason, Incident original, Instant now) {
        this.cancellationReason = Validate.requireText(reason, "Cancellation reason", MAX_REASON_LENGTH);
        this.duplicateOf = original;
        this.closedAt = Validate.requireNonNull(now, "Cancellation time");
        transition(IncidentStatus.CANCELLED, now, IncidentEventType.CANCELLED, "Cancelled: " + reason, true);
    }

    // =============================================================================================================
    // SLA evaluation and escalation (driven by the SLA monitor and after every user action)
    // =============================================================================================================

    /**
     * Checks the SLA deadlines and records a warning or breach the first time each threshold is crossed.
     *
     * @return the thresholds crossed by this call (empty if none, or if they were already reported)
     */
    public Set<SlaSignal> evaluateSla(Instant now) {
        if (status == IncidentStatus.CANCELLED) {
            return Set.of();
        }
        Set<SlaSignal> signals = sla().evaluate(now);
        for (SlaSignal signal : signals) {
            String description = signal.isBreach()
                    ? signal.target() + " SLA breached (deadline " + deadlineFor(signal, now) + ")"
                    : signal.target() + " deadline approaching: " + Math.round(sla().getWarningThreshold() * 100)
                    + "% of the time used (due " + deadlineFor(signal, now) + ")";
            record(signal.isBreach() ? IncidentEventType.SLA_BREACHED : IncidentEventType.SLA_WARNING,
                    status, description, false, now);
        }
        return signals;
    }

    private Instant deadlineFor(SlaSignal signal, Instant now) {
        return signal.target().equals("Response") ? sla().responseDue() : sla().resolutionDue(now);
    }

    /**
     * Raises the incident to {@code level} and, if a technician at that level is available, hands it over to them.
     * Called by the escalation chain; never lowers the level.
     *
     * @param newAssignee technician to take over, or {@code null} to escalate (notify the level) without reassigning
     */
    public void escalate(SupportLevel level, Technician newAssignee, int newAssigneeActiveIncidents, String reason,
                         Instant now) {
        Validate.requireNonNull(level, "Escalation level");
        requireOpen("ESCALATE");
        if (!level.isAtLeast(effectiveEscalationLevel()) || level == effectiveEscalationLevel()) {
            throw new BusinessRuleException("ESCALATION_NOT_HIGHER",
                    "Incident is already at " + effectiveEscalationLevel().displayName() + " level");
        }
        this.escalationLevel = level;
        this.lastEscalatedAt = now;
        touch(now);
        record(IncidentEventType.ESCALATED, status, "Escalated to " + level.displayName() + ": " + reason
                + (newAssignee != null && !newAssignee.equals(assignee) ? "; handing over to "
                + newAssignee.getFullName() : ""), false, now);
        if (newAssignee != null && !newAssignee.equals(assignee)
                && allowedOperations().contains(IncidentOperation.ASSIGN)) {
            assign(newAssignee, newAssigneeActiveIncidents, now);
        }
    }

    /** The support level currently responsible: the escalation level, or the assignee's own level if higher. */
    public SupportLevel effectiveEscalationLevel() {
        SupportLevel fromAssignee = assignee != null ? assignee.getSupportLevel() : SupportLevel.L1_TECHNICIAN;
        if (escalationLevel == null) {
            return fromAssignee;
        }
        return escalationLevel.isAtLeast(fromAssignee) ? escalationLevel : fromAssignee;
    }

    public boolean isAutoVerifiable(Instant now) {
        return status == IncidentStatus.RESOLVED && resolution != null
                && !now.isBefore(resolution.getResolvedAt().plus(AUTO_VERIFY_AFTER));
    }

    /** Returns and clears the changes recorded since the last call. */
    public List<IncidentChange> pullChanges() {
        List<IncidentChange> pulled = List.copyOf(changes);
        changes.clear();
        return pulled;
    }

    // =============================================================================================================
    // Helpers
    // =============================================================================================================

    private IncidentState state() {
        return IncidentStates.of(status);
    }

    /** Changes the status and records the change, in one step, so no transition can go unrecorded. */
    private void transition(IncidentStatus newStatus, Instant now, IncidentEventType type, String description,
                            boolean visibleToReporter) {
        IncidentStatus previous = this.status;
        this.status = newStatus;
        touch(now);
        changes.add(new IncidentChange(type, previous, newStatus, description, visibleToReporter, now));
    }

    /** Records a change that does not move the lifecycle (status stays the same). */
    private void record(IncidentEventType type, IncidentStatus currentStatus, String description,
                        boolean visibleToReporter, Instant now) {
        changes.add(new IncidentChange(type, currentStatus, currentStatus, description, visibleToReporter, now));
    }

    private void touch(Instant now) {
        this.updatedAt = Validate.requireNonNull(now, "Time");
    }

    /** JPA leaves an embedded object {@code null} if all its columns are null; recreate it on demand. */
    private SlaTracker sla() {
        if (sla == null) {
            sla = new SlaTracker();
        }
        return sla;
    }

    private void requireOpen(String operation) {
        if (!status.isOpen()) {
            throw new InvalidStateTransitionException(status.name(), operation);
        }
    }

    private void requireMatchingPolicy(Priority newPriority, SlaPolicy slaPolicy) {
        Validate.requireNonNull(slaPolicy, "SLA policy");
        if (!slaPolicy.appliesTo(newPriority.getLevel(), service)) {
            throw new BusinessRuleException("SLA_POLICY_MISMATCH",
                    "SLA policy " + slaPolicy + " does not apply to a " + newPriority.getLevel()
                            + " incident on " + service.getName());
        }
    }

    private void requireAssignee(Technician by, String action) {
        Validate.requireNonNull(by, "Technician");
        if (assignee == null || !assignee.equals(by)) {
            throw new AuthorizationException("Only the assigned technician can " + action + " this incident");
        }
    }

    private void requireReporter(Employee by, String action) {
        Validate.requireNonNull(by, "Employee");
        if (!reporter.equals(by)) {
            throw new AuthorizationException("Only the reporter can " + action + " this incident");
        }
    }

    private static String requireNumber(String number) {
        String value = Validate.requireText(number, "Incident number", 20);
        if (!NUMBER_PATTERN.matcher(value).matches()) {
            throw new InvalidInputException("Incident number must look like INC-2026-000001 (was " + value + ")");
        }
        return value;
    }

    private static String requireDescription(String description) {
        String value = Validate.requireText(description, "Description", MAX_DESCRIPTION_LENGTH);
        if (value.length() < MIN_DESCRIPTION_LENGTH) {
            throw new InvalidInputException("Description must be at least " + MIN_DESCRIPTION_LENGTH
                    + " characters so support staff can understand the problem");
        }
        return value;
    }

    // =============================================================================================================
    // Queries
    // =============================================================================================================

    /** What can be done next. The frontend uses this to decide which buttons to show. */
    public Set<IncidentOperation> allowedOperations() {
        return state().allowedOperations();
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    public SlaStatus slaStatus(Instant now) {
        return sla().status(now);
    }

    public Optional<InfoRequest> getOpenInfoRequest() {
        return infoRequests.stream().filter(InfoRequest::isOpen).findFirst();
    }

    /** Notes a given user may see: employees never see internal notes. */
    public List<InvestigationNote> notesVisibleTo(User viewer) {
        if (viewer.getRole() == Role.EMPLOYEE) {
            return notes.stream().filter(note -> !note.isInternal()).toList();
        }
        return getNotes();
    }

    // ----- getters -----------------------------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Employee getReporter() {
        return reporter;
    }

    public ItService getService() {
        return service;
    }

    public Category getCategory() {
        return category;
    }

    public Impact getImpact() {
        return impact;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public int getAffectedUsers() {
        return affectedUsers;
    }

    public Priority getPriority() {
        return priority;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Technician getAssignee() {
        return assignee;
    }

    public SlaTracker getSla() {
        return sla();
    }

    public List<InvestigationNote> getNotes() {
        return Collections.unmodifiableList(notes);
    }

    public List<InfoRequest> getInfoRequests() {
        return Collections.unmodifiableList(infoRequests);
    }

    public Resolution getResolution() {
        return resolution;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public Incident getDuplicateOf() {
        return duplicateOf;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public SupportLevel getEscalationLevel() {
        return escalationLevel;
    }

    public Instant getLastEscalatedAt() {
        return lastEscalatedAt;
    }

    // ----- identity ----------------------------------------------------------------------------------------------

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Incident that && getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Incident.class.hashCode();
    }

    @Override
    public String toString() {
        return "Incident{" + number + ", " + status + (priority != null ? ", " + priority.getLevel() : "") + "}";
    }

    // =============================================================================================================
    // Builder
    // =============================================================================================================

    public static final class Builder {

        private String number;
        private String title;
        private String description;
        private Employee reporter;
        private ItService service;
        private Impact impact;
        private Urgency urgency;
        private int affectedUsers = 1;
        private Instant reportedAt;

        private Builder() {
        }

        public Builder number(String value) {
            this.number = value;
            return this;
        }

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder description(String value) {
            this.description = value;
            return this;
        }

        public Builder reporter(Employee value) {
            this.reporter = value;
            return this;
        }

        public Builder service(ItService value) {
            this.service = value;
            return this;
        }

        public Builder impact(Impact value) {
            this.impact = value;
            return this;
        }

        public Builder urgency(Urgency value) {
            this.urgency = value;
            return this;
        }

        /** Optional; defaults to 1. */
        public Builder affectedUsers(int value) {
            this.affectedUsers = value;
            return this;
        }

        public Builder reportedAt(Instant value) {
            this.reportedAt = value;
            return this;
        }

        /** Validates everything and creates the incident in status NEW. */
        public Incident build() {
            return new Incident(this);
        }
    }
}
