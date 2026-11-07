package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.user.Technician;

/**
 * A technician plus the facts about them that come from incident history.
 *
 * <p>These numbers are computed by the service layer with database queries and passed in, so the strategies stay
 * pure, fast, database-free logic that is easy to unit-test.
 *
 * @param activeIncidents   incidents currently assigned to the technician and still being worked on
 * @param resolvedOnService incidents this technician has resolved on the same service as the incident
 *                          being assigned (experience with that system)
 */
public record AssignmentCandidate(Technician technician, int activeIncidents, int resolvedOnService) {

    public AssignmentCandidate {
        Validate.requireNonNull(technician, "Technician");
        Validate.requireRange(activeIncidents, 0, Integer.MAX_VALUE, "Active incidents");
        Validate.requireRange(resolvedOnService, 0, Integer.MAX_VALUE, "Resolved on service");
    }
}
