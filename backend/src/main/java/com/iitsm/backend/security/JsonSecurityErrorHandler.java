package com.iitsm.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Security failures (no token, bad token, wrong role) are rejected before any controller runs, so they never reach
 * the normal exception handler. This class writes them in the <em>same JSON shape</em> as every other API error,
 * so the frontend can handle all errors one way.
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final Clock clock;

    public JsonSecurityErrorHandler(Clock clock) {
        this.clock = clock;
    }

    /** No token, an expired token or a forged token: 401. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(response, request, 401, "UNAUTHENTICATED", "Login required: missing, invalid or expired token");
    }

    /** Valid token, but the role is not allowed here: 403. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, request, 403, "FORBIDDEN", "You do not have permission to perform this action");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, int status, String code,
                       String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Hand-written JSON: every value here is a fixed string except the path, which is escaped.
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"status\":" + status
                + ",\"path\":\"" + escape(request.getRequestURI()) + "\",\"timestamp\":\"" + clock.instant() + "\"}");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
