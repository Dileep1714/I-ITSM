package com.iitsm.backend.domain.catalog;

import com.iitsm.backend.common.Validate;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * An incident category such as Network, Database or Security.
 *
 * <p><b>Design decision:</b> categories are database rows, not a Java {@code enum}. An administrator can add a new
 * category at runtime without any code change, as the spec requires ("new categories … without requiring major
 * changes to unrelated parts of the application").
 *
 * <p>The {@code keywords} feed automatic categorization: an incident whose description mentions "vpn" or
 * "latency" is suggested the Network category.
 */
@Entity
@Table(name = "categories")
public class Category {

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final int MAX_KEYWORD_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_NAME_LENGTH)
    private String name;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "category_keywords", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "keyword", nullable = false, length = MAX_KEYWORD_LENGTH)
    private Set<String> keywords = new HashSet<>();

    @Column(nullable = false)
    private boolean active;

    /** Required by JPA; not for application code. */
    protected Category() {
    }

    public Category(String name, String description) {
        this.name = Validate.requireText(name, "Category name", MAX_NAME_LENGTH);
        this.description = Validate.optionalText(description, "Category description", MAX_DESCRIPTION_LENGTH);
        this.active = true;
    }

    // ----- behaviour -------------------------------------------------------------------------------------------

    public void rename(String newName) {
        this.name = Validate.requireText(newName, "Category name", MAX_NAME_LENGTH);
    }

    public void describe(String newDescription) {
        this.description = Validate.optionalText(newDescription, "Category description", MAX_DESCRIPTION_LENGTH);
    }

    /** Adds a keyword, normalized to lower case so matching is case-insensitive. Duplicates are ignored. */
    public void addKeyword(String keyword) {
        keywords.add(normalizeKeyword(keyword));
    }

    public void removeKeyword(String keyword) {
        keywords.remove(normalizeKeyword(keyword));
    }

    /** Inactive categories are kept for history but cannot be chosen for new incidents. */
    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    private static String normalizeKeyword(String keyword) {
        return Validate.requireText(keyword, "Keyword", MAX_KEYWORD_LENGTH).toLowerCase(Locale.ROOT);
    }

    // ----- read access -----------------------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /** Read-only view: callers must use {@link #addKeyword}/{@link #removeKeyword} to change keywords. */
    public Set<String> getKeywords() {
        return Collections.unmodifiableSet(keywords);
    }

    public boolean isActive() {
        return active;
    }

    // ----- identity --------------------------------------------------------------------------------------------

    /**
     * Two categories are equal when they are the same database row. Before saving (no id yet) an object is only
     * equal to itself. This is the standard, safe equality for JPA entities.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Category that && id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Category.class.hashCode(); // constant, so the hash doesn't change when the id is assigned on save
    }

    @Override
    public String toString() {
        return "Category{id=" + id + ", name='" + name + "'}";
    }
}
