package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.user.Skill;

/** Picks the technician with the highest proficiency in the incident's category. Unskilled technicians are excluded. */
public class SkillBasedStrategy extends ScoringAssignmentStrategy {

    public static final String KEY = "SKILL_BASED";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Skill-based";
    }

    @Override
    public String description() {
        return "Assigns the technician with the strongest skill in the incident's category.";
    }

    @Override
    protected boolean isEligible(Incident incident, AssignmentCandidate candidate) {
        return super.isEligible(incident, candidate) && candidate.technician().canHandle(incident.getCategory());
    }

    @Override
    public Evaluation evaluate(Incident incident, AssignmentCandidate candidate) {
        int proficiency = candidate.technician().proficiencyIn(incident.getCategory());
        String categoryName = incident.getCategory() != null ? incident.getCategory().getName() : "unclassified";
        return new Evaluation((double) proficiency / Skill.MAX_PROFICIENCY,
                categoryName + " skill " + proficiency + "/" + Skill.MAX_PROFICIENCY);
    }
}
