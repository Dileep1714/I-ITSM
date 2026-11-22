package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.IntelligenceDtos.SlaForecastView;
import com.iitsm.backend.common.exception.AuthorizationException;
import com.iitsm.backend.common.exception.NotFoundException;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.prediction.BreachForecast;
import com.iitsm.backend.domain.prediction.BreachPredictor;
import com.iitsm.backend.domain.prediction.HistoricalBreachPredictor;
import com.iitsm.backend.domain.prediction.PredictionContext;
import com.iitsm.backend.domain.prediction.ResolutionSample;
import com.iitsm.backend.domain.prediction.RiskLevel;
import com.iitsm.backend.domain.sla.SlaStatus;
import com.iitsm.backend.domain.sla.SlaTracker;
import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.domain.user.User;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.security.CurrentUserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Predictive SLA: forecasts which open incidents will miss their resolution deadline, before they do.
 *
 * <p>The model is a {@link BreachPredictor} strategy (by default {@link HistoricalBreachPredictor}); this class
 * gathers its inputs (resolved incidents as history, the assignee's queue) and applies visibility: support staff
 * only, and L1 technicians see forecasts for their own incidents.
 */
@Service
@Transactional(readOnly = true)
public class SlaForecastService {

    /** How far back resolved incidents count as history. */
    static final Duration LOOKBACK = Duration.ofDays(180);

    private static final Set<IncidentStatus> RESOLVED = EnumSet.of(
            IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED);
    /** Statuses in which the resolution clock is running, so a forecast is meaningful. */
    private static final Set<IncidentStatus> FORECASTABLE = EnumSet.of(
            IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS);
    private static final Set<IncidentStatus> QUEUED = EnumSet.of(
            IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.ON_HOLD);

    private final IncidentRepository incidents;
    private final CurrentUserService currentUser;
    private final Clock clock;
    private final BreachPredictor predictor = new HistoricalBreachPredictor();

    public SlaForecastService(IncidentRepository incidents, CurrentUserService currentUser, Clock clock) {
        this.incidents = incidents;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ----- API use cases ----------------------------------------------------------------------------------------

    /** The forecast for one incident, if its resolution clock is running and it is not already late. */
    public Optional<SlaForecastView> forecastFor(Long incidentId) {
        User user = requireStaff();
        Incident incident = incidents.findById(incidentId).orElseThrow(() -> new NotFoundException("Incident", incidentId));
        if (!canSee(user, incident)) {
            throw new AuthorizationException("You can only see forecasts for your own incidents");
        }
        Instant now = clock.instant();
        return forecast(incident, history(now), now).map(f -> toView(incident, f, now));
    }

    /** Open incidents at {@code minimum} risk or above, most likely to breach first. */
    public List<SlaForecastView> atRisk(RiskLevel minimum) {
        User user = requireStaff();
        Instant now = clock.instant();
        List<ResolutionSample> history = history(now);
        return incidents.findByStatusInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(FORECASTABLE, Instant.EPOCH)
                .stream()
                .filter(incident -> canSee(user, incident))
                .flatMap(incident -> forecast(incident, history, now).stream()
                        .filter(f -> f.risk().compareTo(minimum) >= 0)
                        .map(f -> toView(incident, f, now)))
                .sorted(Comparator.comparingDouble(SlaForecastView::probability).reversed())
                .toList();
    }

    // ----- used by the SLA monitor ------------------------------------------------------------------------------

    /** Resolved incidents as training data. Call once per monitor cycle and reuse. */
    public List<ResolutionSample> history(Instant now) {
        return incidents.findByStatusInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(RESOLVED, now.minus(LOOKBACK))
                .stream()
                .filter(i -> i.getSla() != null && i.getSla().isStarted() && i.getSla().getResolvedAt() != null)
                .map(i -> new ResolutionSample(
                        i.getSla().activeElapsed(i.getSla().getResolvedAt()),
                        i.getCategory() != null ? i.getCategory().getId() : null,
                        i.getPriority() != null ? i.getPriority().getLevel().name() : null,
                        i.getAssignee() != null ? i.getAssignee().getId() : null))
                .filter(s -> !s.activeDuration().isNegative() && !s.activeDuration().isZero())
                .toList();
    }

    /** Forecast for an incident whose resolution clock is running and which is not late yet; empty otherwise. */
    public Optional<BreachForecast> forecast(Incident incident, List<ResolutionSample> history, Instant now) {
        SlaTracker sla = incident.getSla();
        if (sla == null || !FORECASTABLE.contains(incident.getStatus()) || !sla.isStarted()
                || sla.resolutionTarget() == null) {
            return Optional.empty();
        }
        SlaStatus slaStatus = sla.status(now);
        if (slaStatus == SlaStatus.BREACHED || slaStatus == SlaStatus.PAUSED || slaStatus == SlaStatus.MET) {
            return Optional.empty();
        }
        Technician assignee = incident.getAssignee();
        PredictionContext context = new PredictionContext(
                incident.getCategory() != null ? incident.getCategory().getId() : null,
                incident.getCategory() != null ? incident.getCategory().getName() : null,
                incident.getPriority() != null ? incident.getPriority().getLevel().name() : null,
                assignee != null ? assignee.getId() : null,
                assignee != null ? firstName(assignee.getFullName()) : null,
                sla.activeElapsed(now),
                sla.resolutionTarget(),
                queueAhead(incident));
        return Optional.of(predictor.forecast(context, history));
    }

    // ----- helpers ----------------------------------------------------------------------------------------------

    /** Open incidents in the same assignee's queue that come first: more urgent, or as urgent and older. */
    private int queueAhead(Incident incident) {
        Technician assignee = incident.getAssignee();
        if (assignee == null || incident.getPriority() == null) {
            return 0;
        }
        var level = incident.getPriority().getLevel();
        return (int) incidents.findByAssigneeAndStatusIn(assignee, QUEUED).stream()
                .filter(other -> !other.getId().equals(incident.getId()) && other.getPriority() != null)
                .filter(other -> other.getPriority().getLevel().isHigherThan(level)
                        || (other.getPriority().getLevel() == level && other.getCreatedAt().isBefore(incident.getCreatedAt())))
                .count();
    }

    private SlaForecastView toView(Incident incident, BreachForecast f, Instant now) {
        Instant deadline = incident.getSla().resolutionDue(now);
        return new SlaForecastView(incident.getId(), incident.getNumber(), incident.getTitle(),
                incident.getPriority() != null ? incident.getPriority().getLevel() : null, incident.getStatus(),
                incident.getAssignee() != null ? incident.getAssignee().getFullName() : null,
                f.probability(), f.risk().name(), now.plus(f.expectedRemaining()), deadline,
                f.expectedRemaining().toMinutes(), f.sampleSize(), f.lowConfidence(), f.basis(), f.reasons());
    }

    private User requireStaff() {
        User user = currentUser.require();
        if (user.getRole() == Role.EMPLOYEE) {
            throw new AuthorizationException("Only support staff can view SLA forecasts");
        }
        return user;
    }

    /** Administrators and team leads (L2+) see everything; L1 technicians see their own incidents. */
    private static boolean canSee(User user, Incident incident) {
        if (user.getRole() == Role.ADMINISTRATOR) {
            return true;
        }
        if (user instanceof Technician tech && tech.getSupportLevel() != SupportLevel.L1_TECHNICIAN) {
            return true;
        }
        return incident.getAssignee() != null && incident.getAssignee().getId().equals(user.getId());
    }

    private static String firstName(String fullName) {
        return fullName == null ? null : fullName.split("\\s+")[0];
    }
}
