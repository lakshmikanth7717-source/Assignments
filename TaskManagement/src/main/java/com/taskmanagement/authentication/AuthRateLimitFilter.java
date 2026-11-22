package com.taskmanagement.authentication;

import com.taskmanagement.common.ApiErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

public final class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long start, int requests) {
    }

    private final Map<String, Window> windows = new HashMap<>();
    private final ObjectMapper mapper;
    private final int limit;

    public AuthRateLimitFilter(ObjectMapper mapper, int limit) {
        this.mapper = mapper;
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST") ||
            !(request.getServletPath().equals("/api/auth/login") || request.getServletPath().equals("/api/auth/register"));
    }

    private synchronized boolean allowed(String address) {
        long now = System.currentTimeMillis();
        Window window = windows.get(address);
        if (window == null || now - window.start >= 60_000) {
            if (windows.size() >= 10_000) {
                windows.values().removeIf(w -> now - w.start >= 60_000);
            }
            if (window == null && windows.size() >= 10_000) {
                return false;
            }
            windows.put(address, new Window(now, 1));
            return true;
        }
        if (window.requests >= limit) {
            return false;
        }
        windows.put(address, new Window(window.start, window.requests + 1));
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (allowed(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), ApiErrors.body(HttpStatus.TOO_MANY_REQUESTS,
            "Too many authentication attempts; retry in one minute", request.getRequestURI(), Map.of()));
    }
}
