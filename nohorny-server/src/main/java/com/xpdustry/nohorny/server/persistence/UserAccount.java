// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_account")
public class UserAccount extends AssignedIdEntity<String> {

    @Id
    private String username;

    private String passwordHash;

    private boolean admin;

    @Convert(converter = EpochMillisConverter.class)
    private Instant createdAt;

    @SuppressWarnings("NullAway")
    protected UserAccount() {}

    public UserAccount(final String username, final String passwordHash, final boolean admin, final Instant createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.admin = admin;
        this.createdAt = createdAt;
    }

    @Override
    public String getId() {
        return this.username;
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
