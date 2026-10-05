// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.user;

import com.xpdustry.nohorny.server.persistence.UserAccount;
import com.xpdustry.nohorny.server.persistence.UserAccountRepository;
import com.xpdustry.nohorny.server.ratelimit.RateLimitProperties;
import com.xpdustry.nohorny.server.security.ApiSecurityProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/// Manages the user accounts and the bootstrap administrator.
///
/// The sessions of a user are expired when its password or role changes, or when it is deleted, so they sign in again
/// with their current permissions.
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessions;
    private final ApiSecurityProperties.Admin bootstrap;
    private final int defaultRateLimit;

    public UserService(
            final UserAccountRepository users,
            final PasswordEncoder passwordEncoder,
            final SessionRegistry sessions,
            final ApiSecurityProperties properties,
            final RateLimitProperties limits) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.bootstrap = properties.admin();
        this.defaultRateLimit = limits.user();
    }

    /// Creates the bootstrap administrator if missing, or resets its password and role if they changed.
    @Transactional
    public void ensureBootstrapAdministrator() {
        final var password = this.bootstrap.password();
        if (this.bootstrap.unresolved()) {
            log.warn(
                    "Ignoring the bootstrap administrator, nohorny.security.admin.password is an unresolved placeholder");
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("No bootstrap administrator is configured, set nohorny.security.admin.password to create one");
            return;
        }
        final var username = this.bootstrap.username();
        final var existing = this.users.findById(username);
        if (existing.isEmpty()) {
            this.users.save(new UserAccount(username, this.encode(password), true, this.defaultRateLimit, now()));
            log.info("Created the bootstrap administrator {}", username);
            return;
        }
        final var account = existing.get();
        if (!this.passwordEncoder.matches(password, account.getPasswordHash())) {
            account.setPasswordHash(this.encode(password));
            log.info("Reset the password of the bootstrap administrator {} to the configured one", username);
        }
        if (!account.isAdmin()) {
            account.setAdmin(true);
            log.info("Restored the administrator role of the bootstrap administrator {}", username);
        }
    }

    public boolean isBootstrap(final String username) {
        return this.bootstrap.configured() && this.bootstrap.username().equals(username);
    }

    /// @return the users sorted by username
    public List<UserView> list() {
        return this.users.findAllByOrderByUsernameAsc().stream()
                .map(this::toView)
                .toList();
    }

    /// @param rateLimit the classifications per minute, the configured default if `null`
    @Transactional
    public UserView create(
            final String username, final String password, final boolean admin, final @Nullable Integer rateLimit) {
        if (this.users.existsById(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "user already exists");
        }
        final var limit = rateLimit == null ? this.defaultRateLimit : rateLimit;
        final var account = this.users.save(new UserAccount(username, this.encode(password), admin, limit, now()));
        log.info("Created user {} (admin={}, rateLimit={})", username, admin, limit);
        return this.toView(account);
    }

    /// Applies to the next classification, the sessions are kept.
    @Transactional
    public void setRateLimit(final String username, final int rateLimit) {
        this.find(username).setRateLimit(rateLimit);
        log.info("Set the rate limit of user {} to {} per minute", username, rateLimit);
    }

    /// @param caller the user doing the change, keeps its own sessions
    @Transactional
    public void setPassword(final String username, final String password, final String caller) {
        final var account = this.find(username);
        if (this.isBootstrap(username)) {
            throw conflict("cannot change the password of the bootstrap administrator");
        }
        account.setPasswordHash(this.encode(password));
        log.info("Changed the password of user {}", username);
        if (!username.equals(caller)) {
            this.expireSessions(username);
        }
    }

    /// @param caller the user doing the change, cannot demote itself
    @Transactional
    public void setAdmin(final String username, final boolean admin, final String caller) {
        final var account = this.find(username);
        if (account.isAdmin() == admin) {
            return;
        }
        if (!admin) {
            if (this.isBootstrap(username)) {
                throw conflict("cannot demote the bootstrap administrator");
            }
            if (username.equals(caller)) {
                throw conflict("cannot demote your own account");
            }
            if (this.users.countByAdminTrue() <= 1) {
                throw conflict("cannot demote the last administrator");
            }
        }
        account.setAdmin(admin);
        log.info("Set admin={} for user {}", admin, username);
        this.expireSessions(username);
    }

    /// @param caller the user doing the deletion, cannot delete itself
    @Transactional
    public void delete(final String username, final String caller) {
        final var account = this.find(username);
        if (this.isBootstrap(username)) {
            throw conflict("cannot delete the bootstrap administrator");
        }
        if (username.equals(caller)) {
            throw conflict("cannot delete your own account");
        }
        this.users.delete(account);
        log.info("Deleted user {}", username);
        this.expireSessions(username);
    }

    private UserAccount find(final String username) {
        return this.users
                .findById(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    }

    private void expireSessions(final String username) {
        for (final var principal : this.sessions.getAllPrincipals()) {
            if (principal instanceof UserDetails details
                    && details.getUsername().equals(username)) {
                this.sessions.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
            }
        }
    }

    private UserView toView(final UserAccount account) {
        return new UserView(
                account.getUsername(),
                account.isAdmin(),
                account.getRateLimit(),
                account.getCreatedAt(),
                this.isBootstrap(account.getUsername()));
    }

    private String encode(final String password) {
        return Objects.requireNonNull(this.passwordEncoder.encode(password), "password");
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static ResponseStatusException conflict(final String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
