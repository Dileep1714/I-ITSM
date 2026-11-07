package com.iitsm.backend.domain.user;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.common.exception.InvalidCredentialsException;
import com.iitsm.backend.common.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Base class for everyone who uses I-ITSM.
 *
 * <p><b>Abstract:</b> there is no "plain" user; every user is an {@link Employee}, a {@link Technician} or an
 * {@link Administrator}. Each subclass declares its role through {@link #getRole()} (polymorphism).
 *
 * <p><b>Encapsulation:</b>
 * <ul>
 *   <li>No public setters. State changes go through methods that enforce the rules ({@link #changeEmail},
 *       {@link #changePassword}…).</li>
 *   <li>The password hash never leaves this object: there is no getter for it. Callers ask
 *       {@link #passwordMatches} instead.</li>
 *   <li>Every constructor and method validates its input, so a {@code User} can never be in an invalid state.</li>
 * </ul>
 *
 * <p><b>Persistence:</b> {@code JOINED} inheritance gives a {@code users} table for shared fields plus one table per
 * subclass. That mirrors the class hierarchy in the ER diagram and avoids columns that are empty for most rows.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class User {

    public static final int MAX_NAME_LENGTH = 120;
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt only uses the first 72 bytes of a password; longer ones would be silently truncated. */
    public static final int MAX_PASSWORD_LENGTH = 72;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = MAX_NAME_LENGTH)
    private String fullName;

    @Column(nullable = false, unique = true, length = MAX_EMAIL_LENGTH)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA; not for application code. */
    protected User() {
    }

    protected User(String fullName, String email, String rawPassword, PasswordHasher hasher) {
        this.fullName = Validate.requireText(fullName, "Full name", MAX_NAME_LENGTH);
        this.email = normalizeEmail(email);
        this.passwordHash = hashStrongPassword(rawPassword, hasher);
        this.active = true;
        this.createdAt = Instant.now();
    }

    /** Each concrete user type states its own role. */
    public abstract Role getRole();

    // ----- password ----------------------------------------------------------------------------------------------

    public boolean passwordMatches(String rawPassword, PasswordHasher hasher) {
        Validate.requireNonNull(hasher, "Password hasher");
        return rawPassword != null && hasher.matches(rawPassword, passwordHash);
    }

    /** Self-service change: the user must prove they know the current password. */
    public void changePassword(String currentPassword, String newPassword, PasswordHasher hasher) {
        if (!passwordMatches(currentPassword, hasher)) {
            throw new InvalidCredentialsException();
        }
        this.passwordHash = hashStrongPassword(newPassword, hasher);
    }

    /** Administrator reset: no current password needed. Authorization is checked by the calling service. */
    public void resetPassword(String newPassword, PasswordHasher hasher) {
        this.passwordHash = hashStrongPassword(newPassword, hasher);
    }

    // ----- profile & account -------------------------------------------------------------------------------------

    public void rename(String newFullName) {
        this.fullName = Validate.requireText(newFullName, "Full name", MAX_NAME_LENGTH);
    }

    public void changeEmail(String newEmail) {
        this.email = normalizeEmail(newEmail);
    }

    /** Deactivated users cannot log in, but stay in the database so incident history keeps its references. */
    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    // ----- validation helpers ------------------------------------------------------------------------------------

    private static String normalizeEmail(String email) {
        String normalized = Validate.requireText(email, "Email", MAX_EMAIL_LENGTH).toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidInputException("Email '" + normalized + "' is not a valid email address");
        }
        return normalized;
    }

    private static String hashStrongPassword(String rawPassword, PasswordHasher hasher) {
        Validate.requireNonNull(hasher, "Password hasher");
        requireStrongPassword(rawPassword);
        return hasher.hash(rawPassword);
    }

    /** Password policy: 8–72 characters with at least one letter and one digit. */
    static void requireStrongPassword(String rawPassword) {
        if (rawPassword == null
                || rawPassword.length() < MIN_PASSWORD_LENGTH
                || rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidInputException("Password must be between " + MIN_PASSWORD_LENGTH
                    + " and " + MAX_PASSWORD_LENGTH + " characters");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new InvalidInputException("Password must contain at least one letter and one digit");
        }
    }

    // ----- read access -------------------------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ----- identity ----------------------------------------------------------------------------------------------

    /** Same database row ⇒ equal. Before saving (no id) an object is only equal to itself. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        // getId(), not the field: Hibernate may hand us a lazy proxy whose fields are not loaded
        return other instanceof User that && getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return User.class.hashCode();
    }

    /** Never includes the password hash, so it can't leak into logs. */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", email='" + email + "', role=" + getRole() + "}";
    }
}
