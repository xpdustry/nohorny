// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.cli;

import com.xpdustry.nohorny.persistence.UserAccount;
import com.xpdustry.nohorny.persistence.UserRepository;
import com.xpdustry.nohorny.persistence.UserRole;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserCommands {

    private final ObjectProvider<UserRepository> users;
    private final PasswordEncoder passwordEncoder;

    public UserCommands(final ObjectProvider<UserRepository> users, final PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    @Command(name = "user-add", description = "Create a user.")
    public String add(
            final @Option(longName = "username", required = true) String username,
            final @Option(longName = "password", required = true) String password,
            final @Option(longName = "roles", required = true, description = "Comma-separated ADMIN and/or API roles")
                    String roles) {
        final var repository = this.users.getObject();
        if (repository.existsById(username)) {
            return "User already exists: " + username;
        }
        repository.save(new UserAccount(username, this.passwordEncoder.encode(password), parseRoles(roles)));
        return "Created user " + username;
    }

    @Transactional
    @Command(name = "user-remove", description = "Remove a user.")
    public String remove(final @Option(longName = "username", required = true) String username) {
        final var repository = this.users.getObject();
        if (!repository.existsById(username)) {
            return "User does not exist: " + username;
        }
        repository.deleteById(username);
        return "Removed user " + username;
    }

    @Transactional
    @Command(name = "user-password", description = "Change a user's password.")
    public String password(
            final @Option(longName = "username", required = true) String username,
            final @Option(longName = "password", required = true) String password) {
        return this.users
                .getObject()
                .findById(username)
                .map(user -> {
                    user.setPasswordHash(this.passwordEncoder.encode(password));
                    return "Updated password for " + username;
                })
                .orElse("User does not exist: " + username);
    }

    @Transactional
    @Command(name = "user-role-add", description = "Grant a role to a user.")
    public String addRole(
            final @Option(longName = "username", required = true) String username,
            final @Option(longName = "role", required = true) UserRole role) {
        return this.users
                .getObject()
                .findById(username)
                .map(user -> {
                    user.addRole(role);
                    return "Granted " + role + " to " + username;
                })
                .orElse("User does not exist: " + username);
    }

    @Transactional
    @Command(name = "user-role-remove", description = "Revoke a role from a user.")
    public String removeRole(
            final @Option(longName = "username", required = true) String username,
            final @Option(longName = "role", required = true) UserRole role) {
        return this.users
                .getObject()
                .findById(username)
                .filter(user -> user.getRoles().contains(role))
                .map(user -> {
                    user.removeRole(role);
                    return "Revoked " + role + " from " + username;
                })
                .orElse("User or role does not exist");
    }

    @Transactional(readOnly = true)
    @Command(name = "user-list", description = "List users and their roles.")
    public String list() {
        return this.users.getObject().findAll(Sort.by("username")).stream()
                .map(user -> user.getUsername() + "\t"
                        + user.getRoles().stream().map(Enum::name).sorted().collect(Collectors.joining(",")))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static EnumSet<UserRole> parseRoles(final String value) {
        final var result = Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(role -> !role.isEmpty())
                .map(role -> UserRole.valueOf(role.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(UserRole.class)));
        if (result.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        return result;
    }
}
