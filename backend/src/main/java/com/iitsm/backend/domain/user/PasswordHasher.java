package com.iitsm.backend.domain.user;

/**
 * Hashes and checks passwords.
 *
 * <p><b>Dependency inversion:</b> the domain defines the contract it needs; the security layer provides the
 * implementation (BCrypt). {@link User} therefore never depends on Spring Security, and unit tests can use a simple
 * fake.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hashedPassword);
}
