package com.iitsm.backend.domain.priority;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.incident.Impact;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.Urgency;

/**
 * The inputs to priority calculation.
 *
 * <p>A separate record rather than the whole {@link Incident}: calculators depend only on what they need
 * (interface segregation), and the UI can <em>preview</em> the priority while the user is still filling in the
 * report form, before any incident exists.
 *
 * <p>A Java {@code record} is immutable by design; the compact constructor validates every field.
 */
public record PriorityFactors(Impact impact, Urgency urgency, BusinessCriticality criticality, int affectedUsers) {

    public PriorityFactors {
        Validate.requireNonNull(impact, "Impact");
        Validate.requireNonNull(urgency, "Urgency");
        Validate.requireNonNull(criticality, "Business criticality");
        Validate.requireRange(affectedUsers, 1, Incident.MAX_AFFECTED_USERS, "Affected users");
    }

    public static PriorityFactors of(Incident incident) {
        return new PriorityFactors(incident.getImpact(), incident.getUrgency(),
                incident.getService().getCriticality(), incident.getAffectedUsers());
    }
}
