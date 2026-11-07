package com.iitsm.backend.domain.user;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.catalog.Category;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A support engineer who investigates and resolves incidents.
 *
 * <p>Owns the rules about <em>itself</em>: whether it can handle a category, whether it has spare capacity, whether
 * it can be assigned right now. Assignment strategies ask these questions instead of re-implementing the rules
 * (Tell, Don't Ask / information expert).
 *
 * <p>The technician's current number of active incidents is <em>not</em> stored here; it is derived from the
 * incidents themselves and passed in. Storing it twice would let the two copies drift apart.
 */
@Entity
@Table(name = "technicians")
public class Technician extends User {

    public static final int MAX_EXPERIENCE_YEARS = 50;
    public static final int MIN_CAPACITY = 1;
    public static final int MAX_CAPACITY = 50;

    /** Composition: skills belong to this technician and are removed with it. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "technician_skills", joinColumns = @JoinColumn(name = "technician_id"))
    private List<Skill> skills = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "support_level", nullable = false, length = 20)
    private SupportLevel supportLevel;

    @Column(name = "experience_years", nullable = false)
    private int experienceYears;

    @Column(name = "max_active_incidents", nullable = false)
    private int maxActiveIncidents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Availability availability;

    /** Required by JPA; not for application code. */
    protected Technician() {
    }

    public Technician(String fullName, String email, String rawPassword, PasswordHasher hasher,
                      SupportLevel supportLevel, int experienceYears, int maxActiveIncidents) {
        super(fullName, email, rawPassword, hasher);
        this.supportLevel = Validate.requireNonNull(supportLevel, "Support level");
        this.experienceYears = Validate.requireRange(experienceYears, 0, MAX_EXPERIENCE_YEARS, "Experience (years)");
        this.maxActiveIncidents = Validate.requireRange(maxActiveIncidents, MIN_CAPACITY, MAX_CAPACITY,
                "Maximum active incidents");
        this.availability = Availability.AVAILABLE;
    }

    @Override
    public Role getRole() {
        return Role.TECHNICIAN;
    }

    // ----- skills ------------------------------------------------------------------------------------------------

    /** Adds a skill, or replaces the proficiency if the technician already has this category. Never duplicates. */
    public void addOrUpdateSkill(Category category, int proficiency) {
        Skill newSkill = new Skill(category, proficiency); // validates before anything is changed
        skills.removeIf(skill -> skill.isFor(category));
        skills.add(newSkill);
    }

    public void removeSkill(Category category) {
        skills.removeIf(skill -> skill.isFor(category));
    }

    /** Proficiency 1–5 in the category, or 0 if the technician has no skill in it. */
    public int proficiencyIn(Category category) {
        return skills.stream()
                .filter(skill -> skill.isFor(category))
                .mapToInt(Skill::getProficiency)
                .findFirst()
                .orElse(0);
    }

    public boolean canHandle(Category category) {
        return proficiencyIn(category) > 0;
    }

    // ----- workload & availability -------------------------------------------------------------------------------

    /** @param currentActiveIncidents how many open incidents are assigned to this technician right now */
    public boolean hasCapacity(int currentActiveIncidents) {
        Validate.requireRange(currentActiveIncidents, 0, Integer.MAX_VALUE, "Current active incidents");
        return currentActiveIncidents < maxActiveIncidents;
    }

    /** Active account, marked available, and below capacity. */
    public boolean isAssignable(int currentActiveIncidents) {
        return isActive() && availability.acceptsNewWork() && hasCapacity(currentActiveIncidents);
    }

    /** Fraction of capacity in use (0.0 = idle, 1.0 = full). Used by load-based assignment and the workload report. */
    public double utilization(int currentActiveIncidents) {
        Validate.requireRange(currentActiveIncidents, 0, Integer.MAX_VALUE, "Current active incidents");
        return (double) currentActiveIncidents / maxActiveIncidents;
    }

    public void changeAvailability(Availability newAvailability) {
        this.availability = Validate.requireNonNull(newAvailability, "Availability");
    }

    public void changeCapacity(int newMaxActiveIncidents) {
        this.maxActiveIncidents = Validate.requireRange(newMaxActiveIncidents, MIN_CAPACITY, MAX_CAPACITY,
                "Maximum active incidents");
    }

    public void changeSupportLevel(SupportLevel newLevel) {
        this.supportLevel = Validate.requireNonNull(newLevel, "Support level");
    }

    public void updateExperience(int years) {
        this.experienceYears = Validate.requireRange(years, 0, MAX_EXPERIENCE_YEARS, "Experience (years)");
    }

    // ----- read access -------------------------------------------------------------------------------------------

    /** Read-only view: use {@link #addOrUpdateSkill}/{@link #removeSkill} to change skills. */
    public List<Skill> getSkills() {
        return Collections.unmodifiableList(skills);
    }

    public SupportLevel getSupportLevel() {
        return supportLevel;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public int getMaxActiveIncidents() {
        return maxActiveIncidents;
    }

    public Availability getAvailability() {
        return availability;
    }
}
