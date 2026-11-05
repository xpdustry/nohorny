// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Optional;

public enum RequesterType {
    USER,
    /// A listed Mindustry server without an account, named by its normalized network if the listing has one.
    MINDUSTRY_NETWORK,
    /// A client on a local address without an account, like a server on the same machine or Docker network, never
    /// named.
    LOCAL,
    ANONYMOUS;

    @JsonValue
    public String key() {
        return this.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

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

    @JsonCreator
    public static RequesterType deserialize(final String key) {
        return fromKey(key).orElseThrow(() -> new IllegalArgumentException("Unknown requester type " + key));
    }
}
