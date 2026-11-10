package com.airtribe.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.contains;

class BasicAuthenticationFilterTests {
    private final ObjectMapper json = new ObjectMapper();
    private final BasicAuthenticationFilter filter = new BasicAuthenticationFilter(42, json);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Bearer admin", "Basic !invalid!", "Basic dXNlcjp1c2VyMTIz", "Basic YWRtaW46d3Jvbmc="})
    void missingOrWrongCredentialsNeverReachControllers(String authorization) throws Exception {
        var request = new MockHttpServletRequest();
        if (authorization != null) { request.addHeader("Authorization", authorization); }
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "request-42");
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertThat(called).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Basic ");
        var error = json.readTree(response.getContentAsString());
        assertThat(error.path("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(error.path("request_id").asText()).isEqualTo("request-42");
    }

    @Test
    void credentialsResolveToServerSideNumericUserAndAdminRole() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "basic " + Base64.getEncoder().encodeToString(
                "admin:user123".getBytes(StandardCharsets.UTF_8)));
        request.addHeader("X-User-Id", "999");
        request.addHeader("X-Role", "SUPERUSER");
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> {
            var authenticated = (HttpServletRequest) req;
            assertThat(CurrentUser.id(authenticated.getUserPrincipal())).isEqualTo(42);
            assertThat(AdminAccess.requireAdmin(authenticated)).isEqualTo(42);
            assertThat(authenticated.getRemoteUser()).isEqualTo("42");
            assertThat(authenticated.getAuthType()).isEqualTo(HttpServletRequest.BASIC_AUTH);
            assertThat(authenticated.isUserInRole("SUPERUSER")).isFalse();
            called.set(true);
        });
        assertThat(called).isTrue();
        assertThat(response.getHeader("X-Authenticated-User-Id")).isEqualTo("42");
    }

    @Test
    void defaultFilterInitializesTheRealAdminIdentity() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class,
                "admin@digi-wallet.local")).thenReturn(73L);
        var defaultFilter = new BasicAuthenticationFilter(jdbc, json);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                "admin:user123".getBytes(StandardCharsets.UTF_8)));
        defaultFilter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(AdminAccess.requireAdmin((HttpServletRequest) req)).isEqualTo(73));
        verify(jdbc).update(contains("ON CONFLICT (email) DO NOTHING"));
    }
}
