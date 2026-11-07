package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.IncidentDtos.AcceptRequest;
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
import com.iitsm.backend.config.ItsmProperties;
import com.iitsm.backend.domain.assignment.AssignmentStrategy;
import com.iitsm.backend.domain.assignment.TechnicianScore;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentOperation;
import com.iitsm.backend.domain.incident.Priority;
import com.iitsm.backend.domain.priority.PriorityCalculator;
import com.iitsm.backend.domain.priority.PriorityFactors;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.domain.user.User;
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
 * <p>Each method follows the same shape: identify the user → load the incident → check role and visibility →
 * call the domain method (which enforces the lifecycle and ownership rules) → return the updated view. The
 * whole method runs in one database transaction, so either every change is saved or none is.
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
    private final SlaPolicyResolver slaPolicies;
    private final AssignmentCandidateService candidates;
    private final StrategyRegistry<PriorityCalculator> priorityCalculators;
    private final StrategyRegistry<AssignmentStrategy> assignmentStrategies;
    private final IncidentPermissions permissions;
    private final IncidentMapper mapper;
    private final CurrentUserService currentUser;
    private final ItsmProperties.Assignment assignmentSettings;
    private final Clock clock;

    public IncidentService(IncidentRepository incidents, ItServiceRepository services,
                           CategoryRepository categories, TechnicianRepository technicians,
                           SlaPolicyResolver slaPolicies, AssignmentCandidateService candidates,
                           StrategyRegistry<PriorityCalculator> priorityCalculators,
                           StrategyRegistry<AssignmentStrategy> assignmentStrategies,
                           IncidentPermissions permissions, IncidentMapper mapper,
                           CurrentUserService currentUser, ItsmProperties.Assignment assignmentSettings,
                           Clock clock) {
        this.incidents = incidents;
        this.services = services;
        this.categories = categories;
        this.technicians = technicians;
        this.slaPolicies = slaPolicies;
        this.candidates = candidates;
        this.priorityCalculators = priorityCalculators;
        this.assignmentStrategies = assignmentStrategies;
        this.permissions = permissions;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.assignmentSettings = assignmentSettings;
        this.clock = clock;
    }

    // ----- reporting & classification ----------------------------------------------------------------------------

    /**
     * An employee reports an incident. If they chose a category, the system immediately calculates the priority,
     * applies the SLA and (if enabled) assigns the best technician: steps 1–8 of the spec's problem scenario.
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

        if (request.categoryId() != null) {
            classifyAndRoute(incident, loadCategory(request.categoryId()), now);
        }
        return mapper.toDetail(incident, user);
    }

    /** Support staff classify an incident that was reported without a category. */
    public IncidentDetail classify(Long incidentId, Long categoryId) {
        return perform(incidentId, IncidentOperation.CLASSIFY,
                (incident, user, now) -> classifyAndRoute(incident, loadCategory(categoryId), now));
    }

    private void classifyAndRoute(Incident incident, Category category, Instant now) {
        Priority priority = priorityCalculators.active().calculate(PriorityFactors.of(incident));
        SlaPolicy policy = slaPolicies.resolve(priority.getLevel(), incident.getService());
        incident.classify(category, priority, policy, now);
        if (assignmentSettings.autoAssign()) {
            autoAssign(incident, now);
        }
    }

    /** Assign the active strategy's top recommendation. If nobody is eligible, the incident waits (and escalates). */
    private void autoAssign(Incident incident, Instant now) {
        AssignmentStrategy strategy = assignmentStrategies.active();
        strategy.recommend(incident, candidates.candidatesFor(incident)).ifPresent(best -> {
            incident.assign(best.technician(), best.candidate().activeIncidents(), now);
            strategy.onAssigned(best.technician());
        });
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

    // ----- notes & reading ---------------------------------------------------------------------------------------

    public IncidentDetail addNote(Long incidentId, NoteRequest request) {
        User user = currentUser.require();
        Incident incident = load(incidentId);
        permissions.requireVisible(user, incident);
        incident.addNote(user, request.text(), request.internal(), clock.instant());
        return mapper.toDetail(incident, user);
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
        action.apply(incident, user, clock.instant());
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
