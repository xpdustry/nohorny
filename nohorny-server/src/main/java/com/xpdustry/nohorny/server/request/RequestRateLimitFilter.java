// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import com.xpdustry.nohorny.common.SimpleServerMessage;
import com.xpdustry.nohorny.server.RequestProperties;
import com.xpdustry.nohorny.server.security.SecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/// Limits the public request endpoints per client address with token buckets. Runs after the security filters, so the
/// administrators are not limited.
@Component
public final class RequestRateLimitFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/api/requests/";
    private static final long WINDOW_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final Map<Key, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RequestProperties.RateLimit limits;
    private final JsonMapper mapper;

    public RequestRateLimitFilter(final RequestProperties properties, final JsonMapper mapper) {
        this.limits = properties.rateLimit();
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
            throws ServletException, IOException {
        final var kind = this.kindOf(request);
        if (kind == null
                || SecurityConfiguration.isAdmin(
                        SecurityContextHolder.getContext().getAuthentication())) {
            chain.doFilter(request, response);
            return;
        }
        final var capacity = kind == Kind.PURGE ? this.limits.purgesPerMinute() : this.limits.readsPerMinute();
        final var bucket =
                this.buckets.computeIfAbsent(new Key(kind, request.getRemoteAddr()), ignored -> new TokenBucket());
        final var wait = bucket.tryConsume(capacity, System.nanoTime());
        if (wait == 0L) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(
                HttpHeaders.RETRY_AFTER,
                Long.toString(Math.max(1L, Duration.ofNanos(wait).toSeconds())));
        this.mapper.writeValue(response.getOutputStream(), new SimpleServerMessage("too many requests"));
    }

    private @Nullable Kind kindOf(final HttpServletRequest request) {
        final var path =
                request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(PREFIX)) {
            return null;
        }
        if (HttpMethod.POST.matches(request.getMethod()) && path.endsWith("/purge")) {
            return Kind.PURGE;
        }
        if (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod())) {
            return Kind.READ;
        }
        return null;
    }

    /// Forgets the clients that have been idle long enough to refill their bucket.
    @Scheduled(fixedDelayString = "5m")
    public void cleanup() {
        final var now = System.nanoTime();
        this.buckets.values().removeIf(bucket -> bucket.isIdle(now));
    }

    private enum Kind {
        READ,
        PURGE
    }

    private record Key(Kind kind, String address) {}

    private static final class TokenBucket {

        private double tokens = -1;
        private long updated = 0L;

        /// @return `0` if a token was consumed, otherwise the nanoseconds to wait for the next one
        synchronized long tryConsume(final int capacity, final long now) {
            if (this.tokens < 0) {
                this.tokens = capacity;
            } else {
                final var refill = (double) (now - this.updated) * capacity / WINDOW_NANOS;
                this.tokens = Math.min(capacity, this.tokens + refill);
            }
            this.updated = now;
            if (this.tokens >= 1) {
                this.tokens -= 1;
                return 0L;
            }
            return (long) Math.ceil((1 - this.tokens) * WINDOW_NANOS / capacity);
        }

        synchronized boolean isIdle(final long now) {
            return now - this.updated > WINDOW_NANOS;
        }
    }
}
