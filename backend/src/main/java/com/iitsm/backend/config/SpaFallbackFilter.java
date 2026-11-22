package com.iitsm.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Single-service deployment (e.g. Render): when the React app is bundled into the jar under {@code static/}, its
 * client-side routes such as {@code /incidents/42} must load {@code index.html} so the app can route them.
 *
 * <p>Only GET page requests are forwarded: never the API, the WebSocket, actuator, or files (paths with a dot).
 * When no web app is bundled (local development, where Vite serves it) this filter does nothing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SpaFallbackFilter extends OncePerRequestFilter {

    private final boolean appBundled = new ClassPathResource("static/index.html").exists();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!appBundled || !"GET".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/") || path.startsWith("/api/") || path.equals("/api") || path.startsWith("/ws")
                || path.startsWith("/actuator")) {
            return true;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return last.contains(".");   // a real file: let the static resource handler serve it
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.getRequestDispatcher("/index.html").forward(request, response);
    }
}
