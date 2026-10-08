// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.util.Locale;

/// Who sent a classification request, which also decides its rate limit.
public enum RequesterType {
    /// An authenticated account, named by its username.
    USER,
    /// A listed Mindustry server without an account, named by its normalized network if the listing has one.
    MINDUSTRY_NETWORK,
    /// A client on the loopback interface without an account, like a server running on the same machine, never named.
    LOCALHOST,
    /// Any other client, never named.
    ANONYMOUS;

    /// @return the lowercase name used by the API, such as `mindustry-network`
    public String key() {
        return this.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
