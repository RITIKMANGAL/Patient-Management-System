package com.patientmanagement.demo.security;

import com.patientmanagement.auth.security.JwtPrincipal;
import com.patientmanagement.auth.security.SecurityErrorWriter;
import com.patientmanagement.demo.config.DemoAiProperties;
import com.patientmanagement.demo.config.DemoModeProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DemoAiProtectionFilter extends OncePerRequestFilter {

    private final DemoModeProperties demoModeProperties;
    private final DemoAiProperties demoAiProperties;
    private final SecurityErrorWriter errors;
    private final Clock clock;
    private final Semaphore concurrentRequests;
    private final Map<String, Window> requestWindows = new HashMap<>();

    public DemoAiProtectionFilter(
            DemoModeProperties demoModeProperties,
            DemoAiProperties demoAiProperties,
            SecurityErrorWriter errors,
            Clock clock
    ) {
        this.demoModeProperties = demoModeProperties;
        this.demoAiProperties = demoAiProperties;
        this.errors = errors;
        this.clock = clock;
        this.concurrentRequests = new Semaphore(demoAiProperties.maxConcurrentRequests(), true);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!demoModeProperties.enabled() || !isAiRequest(request)) {
            chain.doFilter(request, response);
            return;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)
                || !demoModeProperties.adminUsername().equalsIgnoreCase(principal.username())) {
            errors.write(response, HttpStatus.FORBIDDEN, "AI demo access is restricted to the demo administrator.");
            return;
        }
        if (!allow(principal.userId().toString())) {
            response.setHeader("Retry-After", "60");
            errors.write(response, HttpStatus.TOO_MANY_REQUESTS, "AI demo request limit reached. Try again later.");
            return;
        }
        if (!concurrentRequests.tryAcquire()) {
            response.setHeader("Retry-After", "5");
            errors.write(response, HttpStatus.TOO_MANY_REQUESTS, "AI demo is busy. Try again shortly.");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            concurrentRequests.release();
        }
    }

    private boolean isAiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.matches("/api/v1/(patients/[^/]+/ai/summary|consultations/[^/]+/ai/draft)");
    }

    private synchronized boolean allow(String userId) {
        long minute = Math.floorDiv(clock.millis(), 60_000);
        Window window = requestWindows.get(userId);
        if (window == null || window.minute != minute) {
            requestWindows.put(userId, new Window(minute, 1));
            return true;
        }
        if (window.count >= demoAiProperties.maxRequestsPerMinute()) {
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
