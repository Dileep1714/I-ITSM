package com.iitsm.backend.domain.user;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.catalog.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * A technician's proficiency (1 = beginner … 5 = expert) in one incident category.
 *
 * <p><b>Value object:</b> no identity of its own and immutable. To change a proficiency, the technician replaces the
 * skill (see {@link Technician#addOrUpdateSkill}). Storing a level rather than just "has skill / doesn't" lets
 * skill-based assignment pick the <em>best</em> technician, not just <em>any</em> qualified one.
 */
@Embeddable
public class Skill {

    public static final int MIN_PROFICIENCY = 1;
    public static final int MAX_PROFICIENCY = 5;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private int proficiency;

    /** Required by JPA; not for application code. */
    protected Skill() {
    }

    public Skill(Category category, int proficiency) {
        this.category = Validate.requireNonNull(category, "Skill category");
        this.proficiency = Validate.requireRange(proficiency, MIN_PROFICIENCY, MAX_PROFICIENCY, "Skill proficiency");
    }

    public boolean isFor(Category other) {
        return category.equals(other);
    }

    public Category getCategory() {
        return category;
    }

    public int getProficiency() {
        return proficiency;
    }

    @Override
    public String toString() {
        return category.getName() + " (" + proficiency + "/" + MAX_PROFICIENCY + ")";
    }
}
