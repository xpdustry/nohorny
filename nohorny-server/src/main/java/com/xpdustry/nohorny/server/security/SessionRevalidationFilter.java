// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.security;

import com.xpdustry.nohorny.server.persistence.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/// Changing the password or the role of an account bumps its session version, deleting it removes it. A new account
/// starts at a random version, so recreating a deleted username does not revive the sessions of the previous one.
/// Since the version is checked on every request rather than swept once, a sign in racing with the change cannot keep
/// the previous rights.
final class SessionRevalidationFilter extends OncePerRequestFilter {

    private final UserAccountRepository users;

    SessionRevalidationFilter(final UserAccountRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
            throws ServletException, IOException {
        final var session = request.getSession(false);
        if (session != null
                && SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                        instanceof AccountDetails details
                && !this.isCurrent(details)) {
            session.invalidate();
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private boolean isCurrent(final AccountDetails details) {
        return this.users
                .findById(details.getUsername())
                .map(account -> account.getSessionVersion() == details.sessionVersion())
                .orElse(false);
    }
}
