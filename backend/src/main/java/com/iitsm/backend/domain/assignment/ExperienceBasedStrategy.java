package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.user.Technician;

/**
 * Prefers technicians who have fixed this <em>service</em> before, and who have more years of experience overall.
 * Both are capped at 10, so ten past fixes count as "fully experienced" and one veteran doesn't take every incident.
 */
public class ExperienceBasedStrategy extends ScoringAssignmentStrategy {

    public static final String KEY = "EXPERIENCE_BASED";
    static final int SATURATION = 10;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Experience-based";
    }

    @Override
    public String description() {
        return "Prefers technicians who have resolved incidents on the same service before, then overall experience.";
    }

    @Override
    public Evaluation evaluate(Incident incident, AssignmentCandidate candidate) {
        Technician technician = candidate.technician();
        double serviceExperience = Math.min(candidate.resolvedOnService(), SATURATION) / (double) SATURATION;
        double generalExperience = Math.min(technician.getExperienceYears(), SATURATION) / (double) SATURATION;
        double score = 0.6 * serviceExperience + 0.4 * generalExperience;
        return new Evaluation(score, "resolved " + candidate.resolvedOnService() + " "
                + incident.getService().getName() + " incidents, " + technician.getExperienceYears() + " yrs experience");
    }
}
