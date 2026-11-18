// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.security.SecureRandom;
import java.time.Instant;

/// Stored in `user_account`, see [UserAccountRepository].
///
/// @param rateLimit the classifications per minute, shared by all the addresses of the account
/// @param sessionVersion bumped when the password or the role changes, which ends the sessions signed in before
public record UserAccount(
        String username, String passwordHash, boolean admin, int rateLimit, long sessionVersion, Instant createdAt) {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static UserAccount create(
            final String username,
            final String passwordHash,
            final boolean admin,
            final int rateLimit,
            final Instant createdAt) {
        // Random rather than 0, so the sessions of a deleted account never match an account created with its name
        return new UserAccount(username, passwordHash, admin, rateLimit, RANDOM.nextLong(), createdAt);
    }

    /// Ends the sessions of the account.
    public UserAccount withPassword(final String passwordHash) {
        return new UserAccount(
                this.username, passwordHash, this.admin, this.rateLimit, this.sessionVersion + 1, this.createdAt);
    }

    /// Ends the sessions of the account if the role changes.
    public UserAccount withRole(final boolean admin) {
        return this.admin == admin
                ? this
                : new UserAccount(
                        this.username,
                        this.passwordHash,
                        admin,
                        this.rateLimit,
                        this.sessionVersion + 1,
                        this.createdAt);
    }

    public UserAccount withRateLimit(final int rateLimit) {
        return new UserAccount(
                this.username, this.passwordHash, this.admin, rateLimit, this.sessionVersion, this.createdAt);
    }
}
