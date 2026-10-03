// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/// Keeps the request pages and the API out of the search engines.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RobotsTagFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
            throws ServletException, IOException {
        final var path =
                request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/requests/") || path.startsWith("/api/")) {
            response.setHeader("X-Robots-Tag", "noindex, nofollow");
        }
        chain.doFilter(request, response);
    }
}
