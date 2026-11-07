package com.iitsm.backend.domain.escalation;

import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.sla.SlaTracker;
import com.iitsm.backend.domain.user.SupportLevel;

import java.time.Instant;

/** Step 1: a P1/P2 incident is at risk of missing a deadline (warning threshold reached) → Team Lead. */
public class TeamLeadEscalation extends EscalationHandler {

    @Override
    protected SupportLevel targetLevel() {
        return SupportLevel.L2_TEAM_LEAD;
    }

    @Override
    protected boolean shouldEscalate(Incident incident, Instant now) {
        SlaTracker sla = incident.getSla();
        boolean responseAtRisk = unacknowledged(incident)
                && sla.responseElapsedFraction(now) >= sla.getWarningThreshold();
        boolean resolutionAtRisk = sla.resolutionElapsedFraction(now) >= sla.getWarningThreshold();
        return isCritical(incident) && (responseAtRisk || resolutionAtRisk);
    }

    @Override
    protected String reason(Incident incident, Instant now) {
        return unacknowledged(incident)
                ? incident.getPriority().getLevel() + " incident not acknowledged and response deadline approaching"
                : incident.getPriority().getLevel() + " incident at risk of missing its resolution deadline";
    }
}
