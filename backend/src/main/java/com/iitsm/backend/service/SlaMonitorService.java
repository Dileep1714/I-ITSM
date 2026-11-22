package com.iitsm.backend.service;

import com.iitsm.backend.domain.escalation.EscalationChain;
import com.iitsm.backend.domain.escalation.EscalationHandler;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.prediction.ResolutionSample;
import com.iitsm.backend.domain.prediction.RiskLevel;
import com.iitsm.backend.domain.sla.SlaSignal;
import com.iitsm.backend.event.IncidentEventPublisher;
import com.iitsm.backend.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The background "watchdog". One cycle looks at every open incident and:
 * <ol>
 *   <li>auto-verifies resolutions the reporter ignored for 72 hours,</li>
 *   <li>retries automatic assignment for incidents nobody could take earlier,</li>
 *   <li>raises SLA warnings and records breaches (once each),</li>
 *   <li>runs the escalation chain.</li>
 * </ol>
 * Each incident is processed in its own short transaction: a problem with one incident is logged and never stops
 * the others, and long-running locks are avoided.
 *
 * <p>Time is a parameter, so tests (and the admin "run now" button) can run a cycle at any moment.
 */
@Service
public class SlaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorService.class);

    static final Set<IncidentStatus> MONITORED = EnumSet.of(
            IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED,
            IncidentStatus.IN_PROGRESS, IncidentStatus.ON_HOLD, IncidentStatus.RESOLVED);

    private final IncidentRepository incidents;
    private final IncidentRouter router;
    private final IncidentEventPublisher events;
    private final TransactionTemplate transactions;
    private final SlaForecastService forecasts;
    private final EscalationHandler escalationChain = EscalationChain.standard();

    public SlaMonitorService(IncidentRepository incidents, IncidentRouter router, IncidentEventPublisher events,
                             TransactionTemplate transactions, SlaForecastService forecasts) {
        this.incidents = incidents;
        this.router = router;
        this.events = events;
        this.transactions = transactions;
        this.forecasts = forecasts;
    }

    public CycleReport runCycle(Instant now) {
        List<Long> ids = incidents.findIdsByStatusIn(MONITORED);
        CycleReport.Builder report = new CycleReport.Builder();
        // Resolution history for the breach forecast: loaded once per cycle, shared by every incident.
        List<ResolutionSample> history = transactions.execute(status -> forecasts.history(now));
        for (Long id : ids) {
            try {
                transactions.executeWithoutResult(status -> process(id, now, report, history));
            } catch (RuntimeException e) {
                report.failed++;
                log.warn("SLA monitor could not process incident {}: {}", id, e.getMessage());
            }
        }
        CycleReport result = report.build(ids.size(), now);
        if (result.hasActivity()) {
            log.info("SLA monitor: {}", result);
        }
        return result;
    }

    private void process(Long incidentId, Instant now, CycleReport.Builder report, List<ResolutionSample> history) {
        Incident incident = incidents.findById(incidentId).orElse(null);
        if (incident == null) {
            return;
        }
        if (incident.isAutoVerifiable(now)) {
            incident.autoVerify(now);
            report.autoVerified++;
        }
        if (incident.getStatus() == IncidentStatus.CLASSIFIED && incident.getAssignee() == null
                && router.autoAssign(incident, now)) {
            report.assigned++;
        }
        for (SlaSignal signal : incident.evaluateSla(now)) {
            if (signal.isBreach()) {
                report.breaches++;
            } else {
                report.warnings++;
            }
        }
        // Predictive SLA: warn the assignee and team leads before the deadline is missed (once per incident).
        forecasts.forecast(incident, history == null ? List.of() : history, now)
                .filter(forecast -> forecast.risk() == RiskLevel.HIGH)
                .filter(forecast -> incident.recordPredictedBreach((int) Math.round(forecast.probability() * 100),
                        String.join("; ", forecast.reasons()), now))
                .ifPresent(forecast -> report.predicted++);
        if (escalationChain.handle(incident, router, now)) {
            report.escalations++;
        }
        events.publishChanges(incident, null); // the system is the actor
    }

    /** What one cycle did. Returned to the admin "run now" endpoint and logged. */
    public record CycleReport(Instant ranAt, int checked, int warnings, int predicted, int breaches,
                              int escalations, int assigned, int autoVerified, int failed) {

        boolean hasActivity() {
            return warnings + predicted + breaches + escalations + assigned + autoVerified + failed > 0;
        }

        static final class Builder {
            int warnings;
            int predicted;
            int breaches;
            int escalations;
            int assigned;
            int autoVerified;
            int failed;

            CycleReport build(int checked, Instant ranAt) {
                return new CycleReport(ranAt, checked, warnings, predicted, breaches, escalations, assigned,
                        autoVerified, failed);
            }
        }
    }
}
