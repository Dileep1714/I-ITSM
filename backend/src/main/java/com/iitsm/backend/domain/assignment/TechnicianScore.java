package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.domain.user.Technician;

import java.util.List;

/**
 * A ranked recommendation: which technician, how strong a match (0.0–1.0), and <em>why</em>.
 * The UI shows the reasons so a dispatcher can trust (or overrule) the recommendation.
 */
public record TechnicianScore(AssignmentCandidate candidate, double score, List<String> reasons) {

    public TechnicianScore {
        reasons = List.copyOf(reasons); // defensive copy: the record stays immutable
    }

    public Technician technician() {
        return candidate.technician();
    }
}
