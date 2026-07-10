// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private String username;

    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "username"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<UserRole> roles = EnumSet.noneOf(UserRole.class);

    private Instant createdAt;

    protected UserAccount() {}

    public UserAccount(final String username, final String passwordHash, final Set<UserRole> roles) {
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("A user must have at least one role");
        }
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = EnumSet.copyOf(roles);
        this.createdAt = Instant.now();
    }

    public String getUsername() {
        return this.username;
    }

    public String getPasswordHash() {
        return this.passwordHash;
    }

    public void setPasswordHash(final String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Set<UserRole> getRoles() {
        return Set.copyOf(this.roles);
    }

    public void addRole(final UserRole role) {
        this.roles.add(role);
    }

    public void removeRole(final UserRole role) {
        if (this.roles.contains(role) && this.roles.size() == 1) {
            throw new IllegalArgumentException("A user must have at least one role");
        }
        this.roles.remove(role);
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }
}
