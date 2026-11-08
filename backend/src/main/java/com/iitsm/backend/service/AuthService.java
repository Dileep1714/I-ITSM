package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.AuthDtos.LoginRequest;
import com.iitsm.backend.api.dto.AuthDtos.RegisterRequest;
import com.iitsm.backend.api.dto.AuthDtos.TokenResponse;
import com.iitsm.backend.api.dto.AuthDtos.UserView;
import com.iitsm.backend.common.exception.BusinessRuleException;
import com.iitsm.backend.common.exception.InvalidCredentialsException;
import com.iitsm.backend.config.ItsmProperties;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.PasswordHasher;
import com.iitsm.backend.domain.user.User;
import com.iitsm.backend.repository.UserRepository;
import com.iitsm.backend.security.CurrentUserService;
import com.iitsm.backend.security.TokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Registration and login.
 *
 * <p>Self-registration always creates an {@link Employee}. Technician and administrator accounts are created by
 * an administrator, so nobody can give themselves elevated rights.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenService tokens;
    private final CurrentUserService currentUser;
    /** Lower-case email domains allowed to self-register; empty = any. */
    private final List<String> registrationDomains;

    /**
     * A real BCrypt hash of a throwaway password. When an email is unknown, login still checks the password
     * against this hash, so a failed login takes the same time whether or not the account exists. Otherwise an
     * attacker could find out which emails are registered just by timing the responses.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordHasher hasher, TokenService tokens,
                       CurrentUserService currentUser, ItsmProperties.Security security) {
        this.registrationDomains = security.registrationDomains().stream()
                .map(domain -> domain.strip().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .toList();
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
        this.currentUser = currentUser;
        this.dummyHash = hasher.hash("timing-equalizer-0");
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        requireAllowedDomain(email);
        if (users.existsByEmail(email)) {
            throw new BusinessRuleException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }
        Employee employee = new Employee(request.fullName(), email, request.password(), hasher,
                request.department(), request.location());
        users.save(employee);
        return tokenFor(employee);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> found = users.findByEmail(normalize(request.email()));
        if (found.isEmpty()) {
            hasher.matches(request.password(), dummyHash); // same work as a real check; result ignored
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        // Deactivated accounts get the same generic message: no hint that the account exists.
        if (!user.passwordMatches(request.password(), hasher) || !user.isActive()) {
            throw new InvalidCredentialsException();
        }
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public UserView me() {
        return UserView.of(currentUser.require());
    }

    private TokenResponse tokenFor(User user) {
        TokenService.IssuedToken token = tokens.issue(user);
        return new TokenResponse(token.value(), "Bearer", token.expiresAt(), UserView.of(user));
    }

    /**
     * Self-registration is for the organisation's own staff: only work email domains are accepted. Checked before
     * the "already registered" check, so the answer never reveals whether an outside address has an account.
     */
    private void requireAllowedDomain(String email) {
        if (registrationDomains.isEmpty()) {
            return;
        }
        String domain = email.substring(email.lastIndexOf('@') + 1);
        if (!registrationDomains.contains(domain)) {
            throw new BusinessRuleException("REGISTRATION_DOMAIN_NOT_ALLOWED",
                    "Registration is only open to work email addresses (" + registrationDomains.stream()
                            .map(d -> "@" + d).collect(Collectors.joining(", "))
                            + "). Ask an administrator if you need an account.");
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
