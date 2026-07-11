// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.cli;

import com.xpdustry.nohorny.persistence.UserAccount;
import com.xpdustry.nohorny.persistence.UserAccountRepository;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserAccountCommands {

    private final ObjectProvider<UserAccountRepository> users;
    private final PasswordEncoder passwordEncoder;

    public UserAccountCommands(
            final ObjectProvider<UserAccountRepository> users, final PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    @Command(name = "user-add", description = "Create a user.")
    public String add(
            final @Option(longName = "username", required = true) String username,
            final @Option(longName = "password", required = true) String password,
            final @Option(longName = "admin", required = true, description = "Whether the user is an administrator")
                    boolean admin) {
        final var repository = this.users.getObject();
        if (repository.existsById(username)) {
            return "User already exists: " + username;
        }
        repository.save(new UserAccount(
                username, Objects.requireNonNull(this.passwordEncoder.encode(password), "password"), admin));
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
                    user.setPasswordHash(Objects.requireNonNull(this.passwordEncoder.encode(password), "password"));
                    return "Updated password for " + username;
                })
                .orElse("User does not exist: " + username);
    }

    @Transactional
    @Command(name = "user-admin", description = "Change a user's administrator flag.")
    public String admin(
            final @Option(longName = "username", required = true) String username,
            final @Option(longName = "admin", required = true) boolean admin) {
        return this.users
                .getObject()
                .findById(username)
                .map(user -> {
                    user.setAdmin(admin);
                    return "Set admin=" + admin + " for " + username;
                })
                .orElse("User does not exist: " + username);
    }

    @Transactional(readOnly = true)
    @Command(name = "user-list", description = "List users.")
    public String list() {
        return this.users.getObject().findAll(Sort.by("username")).stream()
                .map(user -> user.getUsername() + "\tadmin=" + user.isAdmin())
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
