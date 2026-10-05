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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/// Limits the API per client address, see [RateLimitProperties]. Runs after the security filters, so it knows the
/// caller.
///
/// The classifications are limited by tier: an account by its own limit, shared by all its addresses, then a listed
/// Mindustry server by the limit of the mindustry tier, shared by all the servers of its network, then an anonymous
/// client per address. A tier limited to `0` must authenticate. The request pages are limited per address for everyone
/// but the administrators. A limited request is answered `429` with a `Retry-After` header in seconds.
@Component
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final String REQUESTS = "/api/requests/";

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
        final var path =
                request.getRequestURI().substring(request.getContextPath().length());
        final var method = request.getMethod();
        final var address = request.getRemoteAddr();
        if (HttpMethod.POST.matches(method) && path.equals("/api/classify")) {
            final var requester = this.requesters.resolve(authentication, address);
            final var name = requester.name();
            return switch (requester.type()) {
                // Read on every request, so a change from the admin panel applies at once
                case USER ->
                    new Limit(
                            "classify-user",
                            Objects.requireNonNull(name),
                            this.users
                                    .findById(name)
                                    .map(UserAccount::getRateLimit)
                                    .orElse(0));
                // A listed server without a network name is limited on its own
                case MINDUSTRY_NETWORK ->
                    new Limit("classify-mindustry", name == null ? address : name, this.limits.mindustry());
                case ANONYMOUS -> new Limit("classify-anonymous", address, this.limits.anonymous());
            };
        }
        if (!path.startsWith(REQUESTS) || SecurityConfiguration.isAdmin(authentication)) {
            return null;
        }
        if (HttpMethod.POST.matches(method) && path.endsWith("/purge")) {
            return new Limit("purge", address, this.limits.purges());
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

    /// @param scope the bucket family
    /// @param subject the bucket owner within the scope
    /// @param perMinute `0` when the caller must authenticate
    private record Limit(String scope, String subject, int perMinute) {}
}
