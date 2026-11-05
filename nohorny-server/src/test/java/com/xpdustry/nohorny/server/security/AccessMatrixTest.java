// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.security;

import com.xpdustry.nohorny.server.ServerTest;
import com.xpdustry.nohorny.server.user.UserService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/// Every endpoint of the server, as an anonymous caller, a signed in user and an administrator.
final class AccessMatrixTest extends ServerTest {

    private static final String MEMBER = "matrix-member";
    private static final String MEMBER_PASSWORD = "matrix-password";
    private static final String MISSING_ID = "00000000-0000-7000-8000-000000000000";
    private static final String JSON_USER = "{\"username\":\"matrix-created\",\"password\":\"matrix-password\"}";
    private static final String JSON_PASSWORD = "{\"password\":\"matrix-password\"}";
    private static final String JSON_ADMIN = "{\"admin\":true}";
    private static final String JSON_RATE_LIMIT = "{\"rateLimit\":10}";
    private static final String JSON_MAPPING = "{\"range\":\"203.0.113.0/24\",\"type\":\"anonymous\"}";

    private enum Access {
        PUBLIC,
        SIGNED_IN,
        ADMIN
    }

    /// The `pattern` is the one of the handler, the `uri` is a request matching it.
    private record Endpoint(
            HttpMethod method,
            String pattern,
            String uri,
            @Nullable String body,
            Access access) {

        static Endpoint of(final HttpMethod method, final String pattern, final Access access) {
            return new Endpoint(method, pattern, pattern.replaceAll("\\{id}", MISSING_ID), null, access);
        }

        Endpoint on(final String uri) {
            return new Endpoint(this.method, this.pattern, uri, this.body, this.access);
        }

        Endpoint with(final String body) {
            return new Endpoint(this.method, this.pattern, this.uri, body, this.access);
        }

        String key() {
            return this.method + " " + this.pattern;
        }
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            Endpoint.of(HttpMethod.GET, "/api/status", Access.PUBLIC),
            Endpoint.of(HttpMethod.POST, "/api/classify", Access.PUBLIC).with(""),
            Endpoint.of(HttpMethod.GET, "/config.js", Access.PUBLIC),
            Endpoint.of(HttpMethod.GET, "/robots.txt", Access.PUBLIC),
            Endpoint.of(HttpMethod.GET, "/api/stats", Access.PUBLIC),
            Endpoint.of(HttpMethod.GET, "/api/stats/history", Access.PUBLIC),
            Endpoint.of(HttpMethod.GET, "/api/retention", Access.PUBLIC),
            Endpoint.of(HttpMethod.GET, "/api/requests/{id}", Access.PUBLIC),
            Endpoint.of(HttpMethod.GET, "/api/requests/{id}/image", Access.PUBLIC),
            Endpoint.of(HttpMethod.DELETE, "/api/requests/{id}/image", Access.PUBLIC),
            Endpoint.of(HttpMethod.GET, "/api/session", Access.SIGNED_IN),
            Endpoint.of(HttpMethod.GET, "/api/requests", Access.ADMIN),
            Endpoint.of(HttpMethod.DELETE, "/api/requests/{id}", Access.ADMIN),
            Endpoint.of(HttpMethod.GET, "/api/stats/networks", Access.ADMIN),
            Endpoint.of(HttpMethod.GET, "/api/stats/versions", Access.ADMIN),
            Endpoint.of(HttpMethod.GET, "/api/users", Access.ADMIN),
            Endpoint.of(HttpMethod.POST, "/api/users", Access.ADMIN).with(JSON_USER),
            Endpoint.of(HttpMethod.PUT, "/api/users/{username}/password", Access.ADMIN)
                    .on("/api/users/matrix-missing/password")
                    .with(JSON_PASSWORD),
            Endpoint.of(HttpMethod.PUT, "/api/users/{username}/admin", Access.ADMIN)
                    .on("/api/users/matrix-missing/admin")
                    .with(JSON_ADMIN),
            Endpoint.of(HttpMethod.PUT, "/api/users/{username}/rate-limit", Access.ADMIN)
                    .on("/api/users/matrix-missing/rate-limit")
                    .with(JSON_RATE_LIMIT),
            Endpoint.of(HttpMethod.DELETE, "/api/users/{username}", Access.ADMIN)
                    .on("/api/users/matrix-missing"),
            Endpoint.of(HttpMethod.GET, "/api/mappings", Access.ADMIN),
            Endpoint.of(HttpMethod.POST, "/api/mappings", Access.ADMIN).with(JSON_MAPPING),
            Endpoint.of(HttpMethod.PUT, "/api/mappings/{id}", Access.ADMIN).with(JSON_MAPPING),
            Endpoint.of(HttpMethod.DELETE, "/api/mappings/{id}", Access.ADMIN),
            Endpoint.of(HttpMethod.POST, "/api/mappings/{id}/apply", Access.ADMIN));

    @Autowired
    private RequestMappingHandlerMapping handlers;

    @Autowired
    private UserService users;

    @Test
    void everyHandlerIsInTheMatrix() {
        final Set<String> served = new TreeSet<>();
        this.handlers.getHandlerMethods().forEach((mapping, handler) -> {
            // The error page of Spring Boot is not ours
            if (!handler.getBeanType().getPackageName().startsWith("com.xpdustry.nohorny.server")) {
                return;
            }
            final var methods = mapping.getMethodsCondition().getMethods().stream()
                    .map(Enum::name)
                    .toList();
            for (final var pattern :
                    Objects.requireNonNull(mapping.getPathPatternsCondition()).getPatternValues()) {
                for (final var method : methods.isEmpty() ? List.of("ANY") : methods) {
                    served.add(method + " " + pattern);
                }
            }
        });
        final var tested = ENDPOINTS.stream().map(Endpoint::key).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(tested, served, "The endpoints of the server and of the access matrix differ");
    }

    @Test
    void anonymousCallers() throws Exception {
        final var csrf = this.csrf();
        for (final var endpoint : ENDPOINTS) {
            final var response = this.call(endpoint, csrf, null);
            switch (endpoint.access()) {
                case PUBLIC -> assertAllowed(endpoint, response);
                case SIGNED_IN, ADMIN -> assertDenied(endpoint, response, HttpStatus.UNAUTHORIZED, "unauthorized");
            }
        }
    }

    @Test
    void signedInUsers() throws Exception {
        this.users.create(MEMBER, MEMBER_PASSWORD, false, null);
        final var session = this.signIn(MEMBER, MEMBER_PASSWORD);
        final var csrf = this.csrf();
        for (final var endpoint : ENDPOINTS) {
            final var response = this.call(endpoint, csrf, session);
            switch (endpoint.access()) {
                case PUBLIC, SIGNED_IN -> assertAllowed(endpoint, response);
                case ADMIN -> assertDenied(endpoint, response, HttpStatus.FORBIDDEN, "forbidden");
            }
        }
    }

    @Test
    void administrators() throws Exception {
        final var session = this.signIn("admin", ADMIN_PASSWORD);
        final var csrf = this.csrf();
        for (final var endpoint : ENDPOINTS) {
            assertAllowed(endpoint, this.call(endpoint, csrf, session));
        }
    }

    @Test
    void unlistedEndpointsAreDenied() throws Exception {
        final var unlisted = Endpoint.of(HttpMethod.GET, "/api/unlisted", Access.ADMIN);
        final var csrf = this.csrf();
        assertDenied(unlisted, this.call(unlisted, csrf, null), HttpStatus.UNAUTHORIZED, "unauthorized");
        final var session = this.signIn("admin", ADMIN_PASSWORD);
        assertDenied(unlisted, this.call(unlisted, csrf, session), HttpStatus.FORBIDDEN, "forbidden");
    }

    private MockHttpServletResponse call(
            final Endpoint endpoint, final Cookie csrf, final @Nullable MockHttpSession session) throws Exception {
        final var builder =
                request(endpoint.method(), endpoint.uri()).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
        if (endpoint.body() != null) {
            builder.contentType(
                            endpoint.pattern().equals("/api/classify")
                                    ? MediaType.IMAGE_JPEG
                                    : MediaType.APPLICATION_JSON)
                    .content(endpoint.body());
        }
        if (session != null) {
            builder.session(session);
        }
        return this.mvc.perform(builder).andReturn().getResponse();
    }

    private Cookie csrf() throws Exception {
        return Objects.requireNonNull(
                this.mvc.perform(get("/api/status")).andReturn().getResponse().getCookie("XSRF-TOKEN"));
    }

    private static void assertAllowed(final Endpoint endpoint, final MockHttpServletResponse response) {
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus(), endpoint.key());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatus(), endpoint.key());
    }

    private static void assertDenied(
            final Endpoint endpoint,
            final MockHttpServletResponse response,
            final HttpStatus status,
            final String message)
            throws Exception {
        assertEquals(status.value(), response.getStatus(), endpoint.key());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType(), endpoint.key());
        assertEquals("{\"message\":\"" + message + "\"}", response.getContentAsString(), endpoint.key());
    }
}
