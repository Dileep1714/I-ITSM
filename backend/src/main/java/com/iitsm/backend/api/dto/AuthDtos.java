package com.iitsm.backend.api.dto;

import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.domain.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request and response bodies for login and registration. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = User.MAX_NAME_LENGTH) String fullName,
            @NotBlank @Email @Size(max = User.MAX_EMAIL_LENGTH) String email,
            @NotBlank @Size(min = User.MIN_PASSWORD_LENGTH, max = User.MAX_PASSWORD_LENGTH) String password,
            @Size(max = 80) String department,
            @Size(max = 80) String location) {

        /** Never print the password, e.g. in logs. */
        @Override
        public String toString() {
            return "RegisterRequest[fullName=" + fullName + ", email=" + email + "]";
        }
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + "]";
        }
    }

    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, UserView user) {
    }

    public record UserView(Long id, String fullName, String email, Role role) {

        public static UserView of(User user) {
            return new UserView(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
        }
    }
}
