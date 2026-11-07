package com.iitsm.backend.domain.user;

/**
 * Test double for {@link PasswordHasher}. Predictable and fast, unlike real BCrypt.
 * Works only because {@code User} depends on the interface, not on a concrete hasher (dependency inversion).
 */
class FakePasswordHasher implements PasswordHasher {

    @Override
    public String hash(String rawPassword) {
        return "hashed:" + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        return hash(rawPassword).equals(hashedPassword);
    }
}
