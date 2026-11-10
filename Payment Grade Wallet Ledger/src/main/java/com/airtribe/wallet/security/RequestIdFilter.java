package com.airtribe.wallet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = "wallet.request_id";

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value != null) { return value.toString(); }
        String id = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        return id;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        String supplied = request.getHeader("X-Request-Id");
        if (supplied != null && supplied.length() == 36) {
            try { id = UUID.fromString(supplied).toString(); } catch (IllegalArgumentException ignored) { }
        }
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader("X-Request-Id", id);
        MDC.put("request_id", id);
        try { chain.doFilter(request, response); }
        finally { MDC.remove("request_id"); }
    }
}
