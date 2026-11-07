package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.user.Technician;

/**
 * Picks the technician with the most spare capacity, relative to their own limit: 1 of 2 slots used (50 %) is
 * busier than 2 of 8 (25 %). Spreads work evenly and prevents burnout.
 */
public class LoadBasedStrategy extends ScoringAssignmentStrategy {

    public static final String KEY = "LOAD_BASED";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Load-based";
    }

    @Override
    public String description() {
        return "Assigns the technician with the lowest workload relative to their capacity.";
    }

    @Override
    public Evaluation evaluate(Incident incident, AssignmentCandidate candidate) {
        Technician technician = candidate.technician();
        double utilization = technician.utilization(candidate.activeIncidents());
        return new Evaluation(Math.max(0.0, 1.0 - utilization),
                candidate.activeIncidents() + " of " + technician.getMaxActiveIncidents() + " slots in use");
    }
}
