// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.security;

import com.xpdustry.nohorny.common.SimpleServerMessage;
import com.xpdustry.nohorny.server.persistence.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/// Users authenticate with HTTP Basic, stateless, or with the session cookie obtained from `POST /login`.
/// Nothing redirects, the failures answer JSON errors.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiSecurityProperties.class)
public class SecurityConfiguration {

    public static final String ADMIN_ROLE = "ADMIN";
    private static final String ADMIN_AUTHORITY = "ROLE_" + ADMIN_ROLE;

    public static boolean isAdmin(final @Nullable Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    @Bean
    public UserDetailsService userDetailsService(final UserAccountRepository users) {
        return username -> {
            final var account = users.findById(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
            return User.withUsername(account.getUsername())
                    .password(account.getPasswordHash())
                    .roles(account.isAdmin() ? new String[] {"USER", ADMIN_ROLE} : new String[] {"USER"})
                    .build();
        };
    }

    /// Tracks the sessions per user, so they can be expired when the user changes.
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /// Removes the destroyed sessions from the registry.
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            final HttpSecurity http,
            final ApiSecurityProperties properties,
            final JsonMapper mapper,
            final SessionRegistry sessions) {
        final var unauthorized = new JsonStatusWriter(mapper, HttpStatus.UNAUTHORIZED, "unauthorized");
        final var forbidden = new JsonStatusWriter(mapper, HttpStatus.FORBIDDEN, "forbidden");
        final var invalidCsrf = new JsonStatusWriter(mapper, HttpStatus.FORBIDDEN, "invalid csrf token");
        final var path = PathPatternRequestMatcher.withDefaults();

        // Basic requests are not sent automatically by browsers, and the public POSTs need no session
        final var csrfExempt = new OrRequestMatcher(
                request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null,
                path.matcher(HttpMethod.POST, "/api/classify"),
                path.matcher(HttpMethod.POST, "/api/requests/*/purge"));

        return http.authorizeHttpRequests(authorize -> {
                    authorize
                            .requestMatchers(HttpMethod.GET, "/api/requests")
                            .hasRole(ADMIN_ROLE)
                            .requestMatchers(HttpMethod.DELETE, "/api/requests/*")
                            .hasRole(ADMIN_ROLE)
                            .requestMatchers("/api/users", "/api/users/**")
                            .hasRole(ADMIN_ROLE)
                            .requestMatchers("/api/session")
                            .authenticated();
                    if (properties.apiDefaultPolicy() == ApiSecurityProperties.ApiDefaultPolicy.DENY_ALL) {
                        authorize.requestMatchers("/api/classify").authenticated();
                    }
                    authorize.anyRequest().permitAll();
                })
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized))
                .formLogin(form -> form
                        // Disables the generated login page, the admin page holds the form
                        .loginPage("/admin")
                        .loginProcessingUrl("/login")
                        .successHandler((request, response, authentication) -> {
                            // Writes the rotated XSRF-TOKEN cookie, so the page can keep going
                            loadCsrfToken(request);
                            response.setStatus(HttpStatus.NO_CONTENT.value());
                        })
                        .failureHandler((request, response, exception) -> unauthorized.write(response)))
                .logout(logout -> logout.logoutUrl("/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                // Unlimited sessions per user, registered so they can be expired, see UserService
                .sessionManagement(session -> session.maximumSessions(-1)
                        .sessionRegistry(sessions)
                        .expiredSessionStrategy(event -> unauthorized.write(event.getResponse())))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, exception) -> {
                            (exception instanceof CsrfException ? invalidCsrf : forbidden).write(response);
                        }))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(request ->
                                CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request) && !csrfExempt.matches(request)))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // The API never redirects to a saved request
                .requestCache(cache -> cache.disable())
                .build();
    }

    private static void loadCsrfToken(final HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
    }

    /// The CSRF tokens are deferred, loading them on page and API reads makes sure browsers hold the `XSRF-TOKEN`
    /// cookie before sending a `POST` or `DELETE`. Stateless clients, such as the plugin, never get it.
    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
                throws ServletException, IOException {
            if (HttpMethod.GET.matches(request.getMethod()) && request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
                loadCsrfToken(request);
            }
            chain.doFilter(request, response);
        }
    }

    /// Answers a status with a JSON message. Never sends `WWW-Authenticate`, which would make browsers prompt.
    private record JsonStatusWriter(JsonMapper mapper, HttpStatus status, String message)
            implements AuthenticationEntryPoint {

        @Override
        public void commence(
                final HttpServletRequest request,
                final HttpServletResponse response,
                final AuthenticationException exception)
                throws IOException {
            this.write(response);
        }

        void write(final HttpServletResponse response) throws IOException {
            response.setStatus(this.status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            this.mapper.writeValue(response.getOutputStream(), new SimpleServerMessage(this.message));
        }
    }
}
