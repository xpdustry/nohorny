// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.util.Locale;
import java.util.Optional;

/// Who sent a classification request, which also decides its rate limit. A requester mapping can give any of them to
/// the clients of an address range without an account, see `RequesterMappingService`.
public enum RequesterType {
    /// An authenticated account, named by its username.
    USER,
    /// A listed Mindustry server without an account, named by its normalized network if the listing has one.
    MINDUSTRY_NETWORK,
    /// A client on a local address without an account, like a server on the same machine or Docker network, never
    /// named.
    LOCAL,
    /// Any other client, never named.
    ANONYMOUS;

    /// @return the lowercase name used by the API, such as `mindustry-network`
    public String key() {
        return this.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /// @return the type of the given enum name, as stored in the database
    public static Optional<RequesterType> fromName(final String name) {
        for (final var type : values()) {
            if (type.name().equals(name)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static Optional<RequesterType> fromKey(final String key) {
        for (final var type : values()) {
            if (type.key().equals(key)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
