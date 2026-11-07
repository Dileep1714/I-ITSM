package com.iitsm.backend.domain.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * IT manager / administrator: manages users, services, categories, SLA policies and assignment strategies.
 *
 * <p>Has no extra fields today; it exists as its own type because its <em>role</em> (and therefore what it may
 * do) is different. Administrator-specific data can be added here later without affecting other user types.
 */
@Entity
@Table(name = "administrators")
public class Administrator extends User {

    /** Required by JPA; not for application code. */
    protected Administrator() {
    }

    public Administrator(String fullName, String email, String rawPassword, PasswordHasher hasher) {
        super(fullName, email, rawPassword, hasher);
    }

    @Override
    public Role getRole() {
        return Role.ADMINISTRATOR;
    }
}
