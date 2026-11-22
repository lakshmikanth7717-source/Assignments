package com.taskmanagement.authorization;

import com.taskmanagement.common.ApiErrors;
import com.taskmanagement.authentication.AuthRateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper,
                                 @Value("${app.auth-requests-per-minute}") int authLimit) throws Exception {
        // Clients authenticate with bearer headers rather than cookies or HTTP sessions.
        return http.csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {
            })
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/health").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(401);
                    response.setHeader("WWW-Authenticate", "Bearer");
                    response.setContentType("application/json");
                    mapper.writeValue(response.getOutputStream(), ApiErrors.body(HttpStatus.UNAUTHORIZED, "Authentication required", request.getRequestURI(), Map.of()));
                })
                .accessDeniedHandler((request, response, ex) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    mapper.writeValue(response.getOutputStream(), ApiErrors.body(HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI(), Map.of()));
                }))
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
            }).authenticationEntryPoint((request, response, ex) -> {
                response.setStatus(401);
                response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
                response.setContentType("application/json");
                mapper.writeValue(response.getOutputStream(), ApiErrors.body(HttpStatus.UNAUTHORIZED, "Invalid or expired token", request.getRequestURI(), Map.of()));
            }))
            .addFilterBefore(new AuthRateLimitFilter(mapper, authLimit), BearerTokenAuthenticationFilter.class)
            .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isBlank()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Location", "Content-Disposition", "Retry-After"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
