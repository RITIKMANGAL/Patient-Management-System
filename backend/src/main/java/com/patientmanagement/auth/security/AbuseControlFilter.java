package com.patientmanagement.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class AbuseControlFilter extends OncePerRequestFilter {
    private static final int MAX_KEYS = 10_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final SecurityErrorWriter errors;
    private final Clock clock;
    private final boolean enabled;

    public AbuseControlFilter(SecurityErrorWriter errors, Clock clock, boolean enabled) {
        this.errors = errors;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String group = null;
        int limit = 20;
        String identity = request.getRemoteAddr();
        if (enabled && !"OPTIONS".equals(request.getMethod())) {
            if ("POST".equals(request.getMethod()) && path.equals("/api/v1/auth/login")) {
                group = "login";
            } else if ("POST".equals(request.getMethod()) && path.equals("/api/v1/auth/refresh")) {
                group = "refresh";
                limit = 60;
            } else {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) {
                    identity = principal.userId().toString();
                    if (path.matches("/api/v1/(patients/[^/]+/ai/summary|consultations/[^/]+/ai/draft)")) {
                        group = "ai";
                        limit = 10;
                    } else if ("POST".equals(request.getMethod()) && path.equals("/api/v1/auth/register")) {
                        group = "provision";
                        limit = 10;
                    }
                }
            }
        }
        if (group != null && !allow(group + ":" + identity, limit)) {
            response.setHeader("Retry-After", "60");
            errors.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String key, int limit) {
        long minute = Math.floorDiv(clock.millis(), 60_000);
        Window window = windows.get(key);
        if (window == null || window.minute != minute) {
            if (windows.size() >= MAX_KEYS) {
                windows.values().removeIf(value -> value.minute != minute);
                if (windows.size() >= MAX_KEYS && !windows.containsKey(key)) {
                    return false;
                }
            }
            windows.put(key, new Window(minute, 1));
            return true;
        }
        if (window.count >= limit) {
            return false;
        }
        window.count++;
        return true;
    }

    private static final class Window {
        private final long minute;
        private int count;
        private Window(long minute, int count) {
            this.minute = minute;
            this.count = count;
        }
    }
}
