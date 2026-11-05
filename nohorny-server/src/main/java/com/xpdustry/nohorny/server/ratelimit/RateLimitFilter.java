// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.ratelimit;

import com.xpdustry.nohorny.common.SimpleServerMessage;
import com.xpdustry.nohorny.server.RequesterResolver;
import com.xpdustry.nohorny.server.persistence.UserAccount;
import com.xpdustry.nohorny.server.persistence.UserAccountRepository;
import com.xpdustry.nohorny.server.security.SecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/// Runs after the security filters, so it knows the caller.
@Component
public final class RateLimitFilter extends OncePerRequestFilter {

    // Matched like Spring MVC routes, on the decoded path segments, so an encoded spelling such as /api/cl%61ssify
    // cannot reach a handler unlimited
    private static final PathPatternRequestMatcher.Builder PATH = PathPatternRequestMatcher.withDefaults();
    private static final RequestMatcher CLASSIFY = PATH.matcher(HttpMethod.POST, "/api/classify");
    private static final RequestMatcher REQUESTS = PATH.matcher("/api/requests/**");
    private static final RequestMatcher IMAGE_DELETION = PATH.matcher(HttpMethod.DELETE, "/api/requests/*/image");

    private final RateLimiter limiter;
    private final RateLimitProperties limits;
    private final UserAccountRepository users;
    private final RequesterResolver requesters;
    private final JsonMapper mapper;

    public RateLimitFilter(
            final RateLimiter limiter,
            final RateLimitProperties limits,
            final UserAccountRepository users,
            final RequesterResolver requesters,
            final JsonMapper mapper) {
        this.limiter = limiter;
        this.limits = limits;
        this.users = users;
        this.requesters = requesters;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
            throws ServletException, IOException {
        final var limit =
                this.limitOf(request, SecurityContextHolder.getContext().getAuthentication());
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }
        if (limit.perMinute() == 0) {
            this.write(response, HttpStatus.UNAUTHORIZED, "authentication required");
            return;
        }
        final var wait = this.limiter.tryAcquire(limit.scope(), limit.subject(), limit.perMinute());
        if (wait.isZero()) {
            chain.doFilter(request, response);
            return;
        }
        // Rounded up, retrying at the announced time always finds a token
        response.setHeader(
                HttpHeaders.RETRY_AFTER,
                Long.toString(wait.plusSeconds(1).minusNanos(1).toSeconds()));
        this.write(response, HttpStatus.TOO_MANY_REQUESTS, "too many requests");
    }

    /// @return the limit of the request, `null` if it is not limited
    private @Nullable Limit limitOf(final HttpServletRequest request, final @Nullable Authentication authentication) {
        final var method = request.getMethod();
        final var address = request.getRemoteAddr();
        if (CLASSIFY.matches(request)) {
            final var requester = this.requesters.resolve(authentication, address);
            final var name = requester.name();
            return switch (requester.type()) {
                case USER ->
                    new Limit(
                            "classify-user",
                            Objects.requireNonNull(name),
                            this.users
                                    .findById(name)
                                    .map(UserAccount::getRateLimit)
                                    .orElse(0));
                case MINDUSTRY_NETWORK ->
                    new Limit("classify-mindustry", name == null ? address : name, this.limits.mindustry());
                // The local addresses are limited like the anonymous clients, a reverse proxy would make every
                // client look local
                case LOCAL, ANONYMOUS -> new Limit("classify-anonymous", address, this.limits.anonymous());
            };
        }
        if (!REQUESTS.matches(request) || SecurityConfiguration.isAdmin(authentication)) {
            return null;
        }
        if (IMAGE_DELETION.matches(request)) {
            return new Limit("image-deletion", address, this.limits.imageDeletions());
        }
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)) {
            return new Limit("read", address, this.limits.reads());
        }
        return null;
    }

    private void write(final HttpServletResponse response, final HttpStatus status, final String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        this.mapper.writeValue(response.getOutputStream(), new SimpleServerMessage(message));
    }

    /// @param perMinute `0` when the caller must authenticate
    private record Limit(String scope, String subject, int perMinute) {}
}
