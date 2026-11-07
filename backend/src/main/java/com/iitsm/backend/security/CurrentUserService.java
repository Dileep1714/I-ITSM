package com.iitsm.backend.security;

import com.iitsm.backend.common.exception.AuthorizationException;
import com.iitsm.backend.common.exception.InvalidCredentialsException;
import com.iitsm.backend.domain.user.User;
import com.iitsm.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Resolves the logged-in user for the current request from their verified token.
 *
 * <p>The user is reloaded from the database on every request, so a deactivated account is locked out
 * immediately, even if it still holds a token that has not expired yet.
 */
@Service
public class CurrentUserService {

    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    public User require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new InvalidCredentialsException();
        }
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidCredentialsException();
        }
        User user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive()) {
            throw new AuthorizationException("This account has been deactivated");
        }
        return user;
    }
}
