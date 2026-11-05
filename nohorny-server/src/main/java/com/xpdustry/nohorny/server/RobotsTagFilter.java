// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RobotsTagFilter extends OncePerRequestFilter {

    static final List<String> DISALLOWED_PATHS = List.of("/requests/", "/api/", "/admin");

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
            throws ServletException, IOException {
        final var path =
                request.getRequestURI().substring(request.getContextPath().length());
        if (DISALLOWED_PATHS.stream().anyMatch(path::startsWith)) {
            response.setHeader("X-Robots-Tag", "noindex, nofollow");
        }
        chain.doFilter(request, response);
    }
}
