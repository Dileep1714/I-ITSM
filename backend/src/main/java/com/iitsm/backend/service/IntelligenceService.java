package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.IntelligenceDtos.CategorySuggestionView;
import com.iitsm.backend.api.dto.IntelligenceDtos.CorrelationView;
import com.iitsm.backend.api.dto.IntelligenceDtos.DraftAnalysis;
import com.iitsm.backend.api.dto.IntelligenceDtos.DraftAnalysisRequest;
import com.iitsm.backend.api.dto.IntelligenceDtos.RelatedIncidentView;
import com.iitsm.backend.common.exception.AuthorizationException;
import com.iitsm.backend.common.exception.DuplicateIncidentException;
import com.iitsm.backend.common.exception.NotFoundException;
import com.iitsm.backend.common.exception.ServiceNotFoundException;
import com.iitsm.backend.config.ItsmProperties;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.intelligence.CategorySuggester;
import com.iitsm.backend.domain.intelligence.IncidentCorrelator;
import com.iitsm.backend.domain.intelligence.RelatedIncidentFinder;
import com.iitsm.backend.domain.intelligence.RelatedIncidentFinder.Match;
import com.iitsm.backend.domain.intelligence.RelatedIncidentFinder.Probe;
import com.iitsm.backend.domain.intelligence.TextSimilarity;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.domain.user.User;
import com.iitsm.backend.repository.CategoryRepository;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.repository.ItServiceRepository;
import com.iitsm.backend.security.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The intelligent features, as use cases: analyse a draft while it is typed, find incidents related to an existing
 * one, correlate open incidents to a common root cause, classify automatically, and refuse a reporter's exact
 * repeat of their own open incident.
 *
 * <p>The algorithms live in the plain-Java {@code domain.intelligence} package; this class only chooses the
 * candidate sets (bounded in time and size so a big history never slows the form down) and applies visibility
 * rules: employees only ever see open incidents' titles, never someone else's resolution details.
 */
@Service
@Transactional(readOnly = true)
public class IntelligenceService {

    /** How far back to look for similar incidents and classification history. */
    static final Duration LOOKBACK = Duration.ofDays(180);
    /** Upper bound on how many incidents one comparison considers. */
    static final int MAX_CANDIDATES = 500;

    private static final Set<IncidentStatus> OPEN_STATUSES = Arrays.stream(IncidentStatus.values())
            .filter(IncidentStatus::isOpen)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(IncidentStatus.class)));

    private final IncidentRepository incidents;
    private final CategoryRepository categories;
    private final ItServiceRepository services;
    private final IncidentMapper mapper;
    private final CurrentUserService currentUser;
    private final ItsmProperties.Intelligence settings;
    private final Clock clock;

    private final CategorySuggester categorySuggester = new CategorySuggester();
    private final RelatedIncidentFinder relatedFinder = new RelatedIncidentFinder();
    private final IncidentCorrelator correlator = new IncidentCorrelator();

    public IntelligenceService(IncidentRepository incidents, CategoryRepository categories,
                               ItServiceRepository services, IncidentMapper mapper,
                               CurrentUserService currentUser, ItsmProperties.Intelligence settings, Clock clock) {
        this.incidents = incidents;
        this.categories = categories;
        this.services = services;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.settings = settings;
        this.clock = clock;
    }

    // ----- use cases exposed through the API --------------------------------------------------------------------

    /** Live help while reporting: which category it probably is, and whether someone already reported it. */
    public DraftAnalysis analyzeDraft(DraftAnalysisRequest request) {
        User user = currentUser.require();
        ItService service = request.serviceId() == null ? null : services.findById(request.serviceId())
                .orElseThrow(() -> new ServiceNotFoundException(request.serviceId()));
        String description = request.description() == null ? "" : request.description();

        List<CategorySuggestionView> suggestions = suggestCategories(request.title(), description).stream()
                .map(CategorySuggestionView::of)
                .toList();

        boolean staff = user.getRole() != Role.EMPLOYEE;
        List<RelatedIncidentView> similar = relatedFinder
                .find(new Probe(null, request.title(), description, service), recentIncidents()).stream()
                .filter(match -> staff || match.incident().isOpen())   // employees: only "already reported" hints
                .map(match -> toView(match, staff))
                .toList();
        return new DraftAnalysis(suggestions, similar);
    }

    /** Incidents related to an existing one: likely duplicates first, then similar ones (with past fixes). */
    public List<RelatedIncidentView> similarTo(Long incidentId) {
        requireStaff(currentUser.require());
        Incident incident = incidents.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("Incident", incidentId));
        return relatedFinder.find(Probe.of(incident), recentIncidents()).stream()
                .map(match -> toView(match, true))
                .toList();
    }

    /** Groups of recent open incidents that point at one common root-cause service. */
    public List<CorrelationView> correlations() {
        requireStaff(currentUser.require());
        Instant since = clock.instant().minus(settings.correlationWindow());
        List<Incident> open = incidents.findByStatusInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                OPEN_STATUSES, since);
        return correlator.correlate(open).stream()
                .map(c -> new CorrelationView(c.rootService().getId(), c.rootService().getName(),
                        c.affectedServices(), c.explanation(),
                        c.incidents().stream().map(mapper::toSummary).toList()))
                .toList();
    }

    // ----- used by IncidentService while reporting ------------------------------------------------------------

    /** The category to apply automatically, if automatic classification is on and the best guess is confident. */
    public Optional<Category> autoCategory(String title, String description) {
        if (!settings.autoClassify()) {
            return Optional.empty();
        }
        return suggestCategories(title, description).stream()
                .findFirst()
                .filter(best -> best.confidence() >= settings.autoClassifyMinConfidence())
                .map(CategorySuggester.Suggestion::category);
    }

    /**
     * Refuses a report that repeats one of the reporter's own open incidents on the same service almost word for
     * word (the spec's "duplicate incident" error). The reporter can override it.
     */
    public void rejectOwnDuplicate(Employee reporter, String title, String description, ItService service) {
        String text = title + "\n" + description;
        for (Incident own : incidents.findByReporterAndStatusIn(reporter, OPEN_STATUSES)) {
            if (own.getService().equals(service)
                    && TextSimilarity.similarity(text, own.getTitle() + "\n" + own.getDescription())
                    >= settings.ownDuplicateThreshold()) {
                throw new DuplicateIncidentException(own.getId(), own.getNumber(), own.getTitle());
            }
        }
    }

    // ----- helpers ----------------------------------------------------------------------------------------------

    private List<CategorySuggester.Suggestion> suggestCategories(String title, String description) {
        Instant since = clock.instant().minus(LOOKBACK);
        List<CategorySuggester.LabelledText> history = incidents
                .findByCategoryIsNotNullAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        since, PageRequest.of(0, MAX_CANDIDATES))
                .stream()
                .map(i -> new CategorySuggester.LabelledText(i.getTitle() + "\n" + i.getDescription(), i.getCategory()))
                .toList();
        return categorySuggester.suggest(title, description, categories.findByActiveTrueOrderByName(), history);
    }

    private List<Incident> recentIncidents() {
        return incidents.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                clock.instant().minus(LOOKBACK), PageRequest.of(0, MAX_CANDIDATES));
    }

    private static RelatedIncidentView toView(Match match, boolean staff) {
        Incident i = match.incident();
        boolean showFix = staff && i.getResolution() != null;
        return new RelatedIncidentView(i.getId(), i.getNumber(), i.getTitle(), i.getStatus(),
                i.getPriority() != null ? i.getPriority().getLevel() : null, i.getService().getName(),
                i.getCreatedAt(), match.score(), match.relation(), staff ? match.reasons() : List.of(),
                showFix ? i.getResolution().getSummary() : null, showFix ? i.getResolution().getCode() : null);
    }

    private void requireStaff(User user) {
        if (user.getRole() == Role.EMPLOYEE) {
            throw new AuthorizationException("Only support staff can view related incidents");
        }
    }
}
