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

    private int rateLimit;

    private long sessionVersion;

    @Convert(converter = EpochMillisConverter.class)
    private Instant createdAt;

    @SuppressWarnings("NullAway")
    protected UserAccount() {}

    /// @param rateLimit the classifications per minute, shared by all the addresses of the account
    public UserAccount(
            final String username,
            final String passwordHash,
            final boolean admin,
            final int rateLimit,
            final Instant createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.admin = admin;
        this.rateLimit = rateLimit;
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

    /// Ends the sessions of the account.
    public void setPasswordHash(final String passwordHash) {
        this.passwordHash = passwordHash;
        this.sessionVersion++;
    }

    public boolean isAdmin() {
        return this.admin;
    }

    /// Ends the sessions of the account if the role changes.
    public void setAdmin(final boolean admin) {
        if (this.admin != admin) {
            this.admin = admin;
            this.sessionVersion++;
        }
    }

    /// @return the version the sessions must carry, see `SessionRevalidationFilter`
    public long getSessionVersion() {
        return this.sessionVersion;
    }

    public int getRateLimit() {
        return this.rateLimit;
    }

    public void setRateLimit(final int rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }
}
