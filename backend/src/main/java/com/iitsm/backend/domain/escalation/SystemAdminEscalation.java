package com.iitsm.backend.domain.escalation;

import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.sla.SlaTracker;
import com.iitsm.backend.domain.user.SupportLevel;

import java.time.Instant;

/** Step 2: a P1/P2 incident has actually breached a deadline → System Administrator. */
public class SystemAdminEscalation extends EscalationHandler {

    @Override
    protected SupportLevel targetLevel() {
        return SupportLevel.L3_SYSTEM_ADMIN;
    }

    @Override
    protected boolean shouldEscalate(Incident incident, Instant now) {
        SlaTracker sla = incident.getSla();
        boolean responseBreached = unacknowledged(incident) && sla.isResponseBreached(now);
        return isCritical(incident) && (responseBreached || sla.isResolutionBreached(now));
    }

    @Override
    protected String reason(Incident incident, Instant now) {
        return unacknowledged(incident) && incident.getSla().isResponseBreached(now)
                ? "response SLA breached with no acknowledgement"
                : "resolution SLA breached";
    }
}
