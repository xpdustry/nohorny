// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.user;

import com.xpdustry.nohorny.server.security.Usernames;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// The user accounts, restricted to the administrators by the security configuration.
@RestController
@RequestMapping(path = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
public final class UserController {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String PASSWORD_MESSAGE = "password must be at least " + MIN_PASSWORD_LENGTH + " characters";
    private static final int MAX_RATE_LIMIT = 100_000;
    private static final String RATE_LIMIT_MESSAGE = "rate limit must be between 1 and " + MAX_RATE_LIMIT;

    private final UserService users;

    public UserController(final UserService users) {
        this.users = users;
    }

    @GetMapping
    public List<UserView> onList() {
        return this.users.list();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserView onCreate(final @Valid @RequestBody CreateUser body) {
        return this.users.create(body.username(), body.password(), Boolean.TRUE.equals(body.admin()), body.rateLimit());
    }

    @PutMapping(path = "/{username}/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void onSetPassword(final @PathVariable String username, final @Valid @RequestBody SetPassword body) {
        this.users.setPassword(username, body.password());
    }

    @PutMapping(path = "/{username}/admin", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void onSetAdmin(
            final @PathVariable String username,
            final @Valid @RequestBody SetAdmin body,
            final Authentication authentication) {
        this.users.setAdmin(username, body.admin(), authentication.getName());
    }

    @PutMapping(path = "/{username}/rate-limit", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void onSetRateLimit(final @PathVariable String username, final @Valid @RequestBody SetRateLimit body) {
        this.users.setRateLimit(username, body.rateLimit());
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void onDelete(final @PathVariable String username, final Authentication authentication) {
        this.users.delete(username, authentication.getName());
    }

    /// @param admin whether the user is an administrator, `false` if omitted
    /// @param rateLimit the classifications per minute, the configured default if omitted
    public record CreateUser(
            @NotNull(message = "username is required") @Pattern(regexp = Usernames.PATTERN, message = Usernames.MESSAGE) String username,

            @NotNull(message = "password is required") @Size(min = MIN_PASSWORD_LENGTH, message = PASSWORD_MESSAGE) String password,

            @Nullable Boolean admin,

            @Positive(message = RATE_LIMIT_MESSAGE) @Max(value = MAX_RATE_LIMIT, message = RATE_LIMIT_MESSAGE) @Nullable Integer rateLimit) {}

    public record SetPassword(
            @NotNull(message = "password is required") @Size(min = MIN_PASSWORD_LENGTH, message = PASSWORD_MESSAGE) String password) {}

    public record SetRateLimit(
            @NotNull(message = "rate limit is required") @Positive(message = RATE_LIMIT_MESSAGE) @Max(value = MAX_RATE_LIMIT, message = RATE_LIMIT_MESSAGE) Integer rateLimit) {}

    public record SetAdmin(
            @NotNull(message = "admin is required") Boolean admin) {}
}
