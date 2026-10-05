// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.security;

import java.io.Serial;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/// A user account as authenticated, see [SessionRevalidationFilter].
///
/// The session version comes from the same read as the password hash and the authorities, so it identifies exactly
/// the account state the caller signed in with.
public final class AccountDetails extends User {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long sessionVersion;

    public AccountDetails(
            final String username,
            final String passwordHash,
            final Collection<? extends GrantedAuthority> authorities,
            final long sessionVersion) {
        super(username, passwordHash, authorities);
        this.sessionVersion = sessionVersion;
    }

    public long sessionVersion() {
        return this.sessionVersion;
    }

    // The session version is part of the identity, a session signed in before a change is not the same principal
    @Override
    public boolean equals(final @Nullable Object other) {
        return other instanceof AccountDetails that && super.equals(that) && this.sessionVersion == that.sessionVersion;
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Long.hashCode(this.sessionVersion);
    }
}
