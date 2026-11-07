package com.iitsm.backend.service;

import com.iitsm.backend.common.strategy.StrategyRegistry;
import com.iitsm.backend.config.ItsmProperties;
import com.iitsm.backend.domain.assignment.AssignmentCandidate;
import com.iitsm.backend.domain.assignment.AssignmentStrategy;
import com.iitsm.backend.domain.assignment.SmartCompositeStrategy;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.escalation.EscalationContext;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.Priority;
import com.iitsm.backend.domain.priority.PriorityCalculator;
import com.iitsm.backend.domain.priority.PriorityFactors;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.SupportLevel;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Sends incidents to the right people: classification (priority + SLA), automatic assignment with the active
 * strategy, and finding someone at a given level for escalation. Shared by user actions ({@link IncidentService})
 * and the background {@link SlaMonitorService}.
 */
@Component
public class IncidentRouter implements EscalationContext {

    private final SlaPolicyResolver slaPolicies;
    private final AssignmentCandidateService candidates;
    private final StrategyRegistry<PriorityCalculator> priorityCalculators;
    private final StrategyRegistry<AssignmentStrategy> assignmentStrategies;
    private final ItsmProperties.Assignment assignmentSettings;
    /** Escalation always picks the best person at the target level, whatever the everyday strategy is. */
    private final AssignmentStrategy escalationRanking = SmartCompositeStrategy.standard();

    public IncidentRouter(SlaPolicyResolver slaPolicies, AssignmentCandidateService candidates,
                          StrategyRegistry<PriorityCalculator> priorityCalculators,
                          StrategyRegistry<AssignmentStrategy> assignmentStrategies,
                          ItsmProperties.Assignment assignmentSettings) {
        this.slaPolicies = slaPolicies;
        this.candidates = candidates;
        this.priorityCalculators = priorityCalculators;
        this.assignmentStrategies = assignmentStrategies;
        this.assignmentSettings = assignmentSettings;
    }

    /** Calculates priority with the active calculator, applies the matching SLA, then assigns if enabled. */
    public void classifyAndRoute(Incident incident, Category category, Instant now) {
        Priority priority = priorityCalculators.active().calculate(PriorityFactors.of(incident));
        SlaPolicy policy = slaPolicies.resolve(priority.getLevel(), incident.getService());
        incident.classify(category, priority, policy, now);
        if (assignmentSettings.autoAssign()) {
            autoAssign(incident, now);
        }
    }

    /**
     * Assigns the active strategy's top recommendation.
     *
     * @return true if someone was assigned; false if nobody is eligible right now (the SLA monitor retries)
     */
    public boolean autoAssign(Incident incident, Instant now) {
        AssignmentStrategy strategy = assignmentStrategies.active();
        return strategy.recommend(incident, candidates.candidatesFor(incident)).map(best -> {
            incident.assign(best.technician(), best.candidate().activeIncidents(), now);
            strategy.onAssigned(best.technician());
            return true;
        }).orElse(false);
    }

    @Override
    public Optional<AssignmentCandidate> bestAvailableAt(SupportLevel level, Incident incident) {
        List<AssignmentCandidate> atLevel = candidates.candidatesFor(incident).stream()
                .filter(candidate -> candidate.technician().getSupportLevel() == level)
                .toList();
        return escalationRanking.recommend(incident, atLevel).map(score -> score.candidate());
    }
}
