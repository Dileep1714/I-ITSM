package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.IncidentDtos.AcceptRequest;
import com.iitsm.backend.api.dto.IncidentDtos.AuditEntryView;
import com.iitsm.backend.api.dto.IncidentDtos.IncidentDetail;
import com.iitsm.backend.api.dto.IncidentDtos.IncidentSummary;
import com.iitsm.backend.api.dto.IncidentDtos.NoteRequest;
import com.iitsm.backend.api.dto.IncidentDtos.Recommendation;
import com.iitsm.backend.api.dto.IncidentDtos.ReportIncidentRequest;
import com.iitsm.backend.api.dto.IncidentDtos.ResolveRequest;
import com.iitsm.backend.api.dto.PageResponse;
import com.iitsm.backend.common.exception.AuthorizationException;
import com.iitsm.backend.common.exception.NotFoundException;
import com.iitsm.backend.common.exception.ServiceNotFoundException;
import com.iitsm.backend.common.strategy.StrategyRegistry;
import com.iitsm.backend.domain.assignment.AssignmentStrategy;
import com.iitsm.backend.domain.assignment.TechnicianScore;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentOperation;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.domain.user.User;
import com.iitsm.backend.event.IncidentEventPublisher;
import com.iitsm.backend.repository.AuditEntryRepository;
import com.iitsm.backend.repository.CategoryRepository;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.repository.ItServiceRepository;
import com.iitsm.backend.repository.TechnicianRepository;
import com.iitsm.backend.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Use cases for incidents: report, classify, assign, work, resolve, verify, close.
 *
 * <p>Each state-changing method follows the same shape (see {@link #perform}): identify the user → check role
 * and visibility → call the domain method (which enforces lifecycle and ownership rules) → check the SLA →
 * publish what changed → return the updated view. All in one transaction, so the change, its audit entries and
 * its in-app notifications are saved together or not at all.
 *
 * <p>The service holds <b>no incident rules</b> of its own; it coordinates. The rules live in {@link Incident}.
 */
@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidents;
    private final ItServiceRepository services;
    private final CategoryRepository categories;
    private final TechnicianRepository technicians;
    private final AuditEntryRepository auditEntries;
    private final AssignmentCandidateService candidates;
    private final StrategyRegistry<AssignmentStrategy> assignmentStrategies;
    private final IncidentRouter router;
    private final IncidentPermissions permissions;
    private final IncidentMapper mapper;
    private final IncidentEventPublisher events;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public IncidentService(IncidentRepository incidents, ItServiceRepository services,
                           CategoryRepository categories, TechnicianRepository technicians,
                           AuditEntryRepository auditEntries, AssignmentCandidateService candidates,
                           StrategyRegistry<AssignmentStrategy> assignmentStrategies, IncidentRouter router,
                           IncidentPermissions permissions, IncidentMapper mapper, IncidentEventPublisher events,
                           CurrentUserService currentUser, Clock clock) {
        this.incidents = incidents;
        this.services = services;
        this.categories = categories;
        this.technicians = technicians;
        this.auditEntries = auditEntries;
        this.candidates = candidates;
        this.assignmentStrategies = assignmentStrategies;
        this.router = router;
        this.permissions = permissions;
        this.mapper = mapper;
        this.events = events;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ----- reporting & classification ----------------------------------------------------------------------------

    /**
     * An employee reports an incident. If they chose a category, the system immediately calculates the priority,
     * applies the SLA and (if enabled) assigns the best technician: steps 1–9 of the spec's problem scenario.
     */
    public IncidentDetail report(ReportIncidentRequest request) {
        User user = currentUser.require();
        if (!(user instanceof Employee reporter)) {
            throw new AuthorizationException("Only employees can report incidents");
        }
        ItService service = services.findById(request.serviceId())
                .orElseThrow(() -> new ServiceNotFoundException(request.serviceId()));
        Instant now = clock.instant();

        Incident incident = Incident.builder()
                .number(nextNumber(now))
                .title(request.title())
                .description(request.description())
                .reporter(reporter)
                .service(service)
                .impact(request.impact())
                .urgency(request.urgency())
                .affectedUsers(request.affectedUsers() != null ? request.affectedUsers() : 1)
                .reportedAt(now)
                .build();
        incidents.save(incident);
        events.publishChanges(incident, user);  // "reported by Asha"

        if (request.categoryId() != null) {
            router.classifyAndRoute(incident, loadCategory(request.categoryId()), now);
        }
        incident.evaluateSla(now);
        // Classification and assignment were decided automatically, so they are the system's actions: the
        // history says so truthfully, and the reporter is notified about them (people are never notified of
        // their own actions).
        events.publishChanges(incident, null);
        return mapper.toDetail(incident, user);
    }

    /** Support staff classify an incident that was reported without a category. */
    public IncidentDetail classify(Long incidentId, Long categoryId) {
        return perform(incidentId, IncidentOperation.CLASSIFY,
                (incident, user, now) -> router.classifyAndRoute(incident, loadCategory(categoryId), now));
    }

    // ----- assignment --------------------------------------------------------------------------------------------

    public IncidentDetail assign(Long incidentId, Long technicianId) {
        return perform(incidentId, IncidentOperation.ASSIGN, (incident, user, now) -> {
            Technician technician = technicians.findById(technicianId)
                    .orElseThrow(() -> new NotFoundException("Technician", technicianId));
            incident.assign(technician, candidates.activeIncidentsOf(technician), now);
            assignmentStrategies.active().onAssigned(technician);
        });
    }

    @Transactional(readOnly = true)
    public List<Recommendation> recommendations(Long incidentId) {
        User user = currentUser.require();
        permissions.require(user, IncidentOperation.ASSIGN);
        Incident incident = load(incidentId);
        return assignmentStrategies.active().rank(incident, candidates.candidatesFor(incident)).stream()
                .limit(5)
                .map(IncidentService::toRecommendation)
                .toList();
    }

    private static Recommendation toRecommendation(TechnicianScore score) {
        Technician technician = score.technician();
        return new Recommendation(technician.getId(), technician.getFullName(),
                Math.round(score.score() * 100) / 100.0, score.reasons(),
                score.candidate().activeIncidents(), technician.getMaxActiveIncidents());
    }

    // ----- technician work ---------------------------------------------------------------------------------------

    public IncidentDetail acknowledge(Long incidentId) {
        return perform(incidentId, IncidentOperation.ACKNOWLEDGE,
                (incident, user, now) -> incident.acknowledge(asTechnician(user), now));
    }

    public IncidentDetail startWork(Long incidentId) {
        return perform(incidentId, IncidentOperation.START_WORK,
                (incident, user, now) -> incident.startWork(asTechnician(user), now));
    }

    public IncidentDetail requestInfo(Long incidentId, String question) {
        return perform(incidentId, IncidentOperation.REQUEST_INFO,
                (incident, user, now) -> incident.requestInfo(asTechnician(user), question, now));
    }

    public IncidentDetail resolve(Long incidentId, ResolveRequest request) {
        return perform(incidentId, IncidentOperation.RESOLVE, (incident, user, now) ->
                incident.resolve(asTechnician(user), request.summary(), request.rootCause(), request.code(), now));
    }

    // ----- reporter actions --------------------------------------------------------------------------------------

    public IncidentDetail provideInfo(Long incidentId, String answer) {
        return perform(incidentId, IncidentOperation.PROVIDE_INFO,
                (incident, user, now) -> incident.provideInfo(asEmployee(user), answer, now));
    }

    public IncidentDetail acceptResolution(Long incidentId, AcceptRequest request) {
        return perform(incidentId, IncidentOperation.ACCEPT_RESOLUTION, (incident, user, now) ->
                incident.acceptResolution(asEmployee(user), request.rating(), request.comment(), now));
    }

    public IncidentDetail rejectResolution(Long incidentId, String reason) {
        return perform(incidentId, IncidentOperation.REJECT_RESOLUTION,
                (incident, user, now) -> incident.rejectResolution(asEmployee(user), reason, now));
    }

    // ----- closing -----------------------------------------------------------------------------------------------

    public IncidentDetail close(Long incidentId) {
        return perform(incidentId, IncidentOperation.CLOSE, (incident, user, now) -> incident.close(now));
    }

    public IncidentDetail cancel(Long incidentId, String reason) {
        return perform(incidentId, IncidentOperation.CANCEL, (incident, user, now) -> incident.cancel(reason, now));
    }

    // ----- notes, history & reading ------------------------------------------------------------------------------

    public IncidentDetail addNote(Long incidentId, NoteRequest request) {
        User user = currentUser.require();
        Incident incident = load(incidentId);
        permissions.requireVisible(user, incident);
        incident.addNote(user, request.text(), request.internal(), clock.instant());
        events.publishChanges(incident, user);
        return mapper.toDetail(incident, user);
    }

    /** The incident's audit trail, oldest first. Employees see only entries meant for them. */
    @Transactional(readOnly = true)
    public List<AuditEntryView> history(Long incidentId) {
        User user = currentUser.require();
        Incident incident = load(incidentId);
        permissions.requireVisible(user, incident);
        boolean employee = user.getRole() == Role.EMPLOYEE;
        return auditEntries.findByIncidentIdOrderByOccurredAtAscIdAsc(incidentId).stream()
                .filter(entry -> !employee || entry.isVisibleToReporter())
                .map(AuditEntryView::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentDetail get(Long incidentId) {
        User user = currentUser.require();
        Incident incident = load(incidentId);
        permissions.requireVisible(user, incident);
        return mapper.toDetail(incident, user);
    }

    /** "My incidents": what I reported (employee), what is assigned to me (technician), everything (admin). */
    @Transactional(readOnly = true)
    public PageResponse<IncidentSummary> listMine(Pageable pageable) {
        User user = currentUser.require();
        Page<Incident> page = switch (user) {
            case Employee employee -> incidents.findByReporter(employee, pageable);
            case Technician technician -> incidents.findByAssignee(technician, pageable);
            default -> incidents.findAll(pageable);
        };
        return PageResponse.of(page, mapper::toSummary);
    }

    // ----- helpers -----------------------------------------------------------------------------------------------

    /** A use-case step that changes an incident, given the current user and time. */
    @FunctionalInterface
    private interface IncidentAction {
        void apply(Incident incident, User user, Instant now);
    }

    /** The common shape of every state-changing use case. */
    private IncidentDetail perform(Long incidentId, IncidentOperation operation, IncidentAction action) {
        User user = currentUser.require();
        permissions.require(user, operation);
        Incident incident = load(incidentId);
        permissions.requireVisible(user, incident);
        Instant now = clock.instant();
        action.apply(incident, user, now);
        incident.evaluateSla(now);      // e.g. a late acknowledgement is recorded as a breach immediately
        events.publishChanges(incident, user);
        return mapper.toDetail(incident, user);
    }

    private Incident load(Long incidentId) {
        return incidents.findById(incidentId).orElseThrow(() -> new NotFoundException("Incident", incidentId));
    }

    private Category loadCategory(Long categoryId) {
        return categories.findById(categoryId).orElseThrow(() -> new NotFoundException("Category", categoryId));
    }

    private String nextNumber(Instant now) {
        int year = now.atZone(ZoneOffset.UTC).getYear();
        return String.format("INC-%04d-%06d", year, incidents.nextIncidentNumber());
    }

    private static Technician asTechnician(User user) {
        if (user instanceof Technician technician) {
            return technician;
        }
        throw new AuthorizationException("Only technicians can do this");
    }

    private static Employee asEmployee(User user) {
        if (user instanceof Employee employee) {
            return employee;
        }
        throw new AuthorizationException("Only the reporting employee can do this");
    }
}
