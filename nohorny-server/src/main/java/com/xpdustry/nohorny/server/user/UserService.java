// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.user;

import com.xpdustry.nohorny.server.database.SQLiteDatabase;
import com.xpdustry.nohorny.server.mapping.RequesterMappingService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final SQLiteDatabase database;
    private final UserAccountRepository users;
    private final RequesterMappingService mappings;
    private final PasswordEncoder passwordEncoder;
    private final ApiSecurityProperties.Admin bootstrap;
    private final int defaultRateLimit;

    public UserService(
            final SQLiteDatabase database,
            final UserAccountRepository users,
            final RequesterMappingService mappings,
            final PasswordEncoder passwordEncoder,
            final ApiSecurityProperties properties,
            final RateLimitProperties limits) {
        this.database = database;
        this.users = users;
        this.mappings = mappings;
        this.passwordEncoder = passwordEncoder;
        this.bootstrap = properties.admin();
        this.defaultRateLimit = limits.user();
    }

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
        this.database.withTransaction(transaction -> {
            final var existing = this.users.findById(username);
            if (existing.isEmpty()) {
                this.users.insert(
                        UserAccount.create(username, this.encode(password), true, this.defaultRateLimit, now()));
                log.info("Created the bootstrap administrator {}", username);
                return Boolean.TRUE;
            }
            var account = existing.get();
            if (!this.passwordEncoder.matches(password, account.passwordHash())) {
                account = account.withPassword(this.encode(password));
                log.info("Reset the password of the bootstrap administrator {} to the configured one", username);
            }
            if (!account.admin()) {
                account = account.withRole(true);
                log.info("Restored the administrator role of the bootstrap administrator {}", username);
            }
            if (!account.equals(existing.get())) {
                this.users.update(account);
            }
            return Boolean.TRUE;
        });
    }

    public boolean isBootstrap(final String username) {
        return this.bootstrap.configured() && this.bootstrap.username().equals(username);
    }

    public List<UserView> list() {
        return this.users.findAllOrderedByUsername().stream().map(this::toView).toList();
    }

    public UserView create(
            final String username, final String password, final boolean admin, final @Nullable Integer rateLimit) {
        return this.database.withTransaction(transaction -> {
            if (this.users.existsById(username)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "user already exists");
            }
            final var limit = rateLimit == null ? this.defaultRateLimit : rateLimit;
            final var account = UserAccount.create(username, this.encode(password), admin, limit, now());
            this.users.insert(account);
            log.info("Created user {} (admin={}, rateLimit={})", username, admin, limit);
            return this.toView(account);
        });
    }

    public void setRateLimit(final String username, final int rateLimit) {
        this.database.withTransaction(transaction -> {
            this.users.update(this.find(username).withRateLimit(rateLimit));
            log.info("Set the rate limit of user {} to {} per minute", username, rateLimit);
            return Boolean.TRUE;
        });
    }

    /// Ends the sessions of the user, the caller included when it changes its own password.
    public void changePassword(final String username, final String password) {
        this.database.withTransaction(transaction -> {
            final var account = this.find(username);
            if (this.isBootstrap(username)) {
                throw conflict("cannot change the password of the bootstrap administrator");
            }
            this.users.update(account.withPassword(this.encode(password)));
            log.info("Changed the password of user {}", username);
            return Boolean.TRUE;
        });
    }

    public void changeRole(final String username, final boolean admin, final String caller) {
        this.database.withTransaction(transaction -> {
            final var account = this.find(username);
            if (account.admin() == admin) {
                return Boolean.FALSE;
            }
            if (!admin) {
                if (this.isBootstrap(username)) {
                    throw conflict("cannot demote the bootstrap administrator");
                }
                if (username.equals(caller)) {
                    throw conflict("cannot demote your own account");
                }
                if (this.users.countAdministrators() <= 1) {
                    throw conflict("cannot demote the last administrator");
                }
            }
            this.users.update(account.withRole(admin));
            log.info("Set admin={} for user {}", admin, username);
            return Boolean.TRUE;
        });
    }

    public void delete(final String username, final String caller) {
        this.database.withTransaction(transaction -> {
            final var account = this.find(username);
            if (this.isBootstrap(username)) {
                throw conflict("cannot delete the bootstrap administrator");
            }
            if (username.equals(caller)) {
                throw conflict("cannot delete your own account");
            }
            this.mappings.deleteForUser(username);
            this.users.delete(account.username());
            log.info("Deleted user {}", username);
            return Boolean.TRUE;
        });
    }

    private UserAccount find(final String username) {
        return this.users
                .findById(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    }

    private UserView toView(final UserAccount account) {
        return new UserView(
                account.username(),
                account.admin(),
                account.rateLimit(),
                account.createdAt(),
                this.isBootstrap(account.username()));
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
