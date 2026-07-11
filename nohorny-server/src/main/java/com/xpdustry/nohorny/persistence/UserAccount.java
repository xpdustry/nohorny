// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private String username;

    private String passwordHash;

    private boolean admin;

    private Instant createdAt;

    protected UserAccount() {}

    public UserAccount(final String username, final String passwordHash, final boolean admin) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.admin = admin;
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

    public boolean isAdmin() {
        return this.admin;
    }

    public void setAdmin(final boolean admin) {
        this.admin = admin;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }
}
