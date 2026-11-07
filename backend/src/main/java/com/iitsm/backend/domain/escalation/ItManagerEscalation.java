package com.iitsm.backend.domain.escalation;

import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.user.SupportLevel;

import java.time.Instant;

/**
 * Step 3: a P1 incident is still unresolved at 150 % of its resolution target, long past the deadline →
 * IT Manager. Reserved for genuine crises, so managers are not paged for every breach.
 */
public class ItManagerEscalation extends EscalationHandler {

    static final double OVERRUN_THRESHOLD = 1.5;

    @Override
    protected SupportLevel targetLevel() {
        return SupportLevel.L4_IT_MANAGER;
    }

    @Override
    protected boolean shouldEscalate(Incident incident, Instant now) {
        return incident.getPriority().getLevel() == PriorityLevel.P1
                && incident.getSla().resolutionElapsedFraction(now) >= OVERRUN_THRESHOLD;
    }

    @Override
    protected String reason(Incident incident, Instant now) {
        return "P1 incident still unresolved at "
                + Math.round(incident.getSla().resolutionElapsedFraction(now) * 100) + "% of its resolution target";
    }
}
