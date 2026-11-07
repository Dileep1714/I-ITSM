package com.iitsm.backend.security;

import com.iitsm.backend.domain.user.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * <b>Adapter pattern:</b> makes Spring Security's {@link PasswordEncoder} (BCrypt) fit the domain's own
 * {@link PasswordHasher} interface. The domain never depends on Spring Security; this adapter is the only bridge.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    public BCryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
