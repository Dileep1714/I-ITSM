package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.AuthDtos.LoginRequest;
import com.iitsm.backend.api.dto.AuthDtos.RegisterRequest;
import com.iitsm.backend.api.dto.AuthDtos.TokenResponse;
import com.iitsm.backend.api.dto.AuthDtos.UserView;
import com.iitsm.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** Self-registration as an employee. Returns a token, so the user is logged in straight away. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    /** Who am I? The frontend calls this on startup to restore the session from a saved token. */
    @GetMapping("/me")
    public UserView me() {
        return auth.me();
    }
}
