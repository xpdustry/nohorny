// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import org.jspecify.annotations.Nullable;

/// Who sent a classification request.
///
/// @param name the username of a [RequesterType#USER], the network of a [RequesterType#MINDUSTRY_NETWORK] if listed
///     with one, always `null` for a [RequesterType#LOCALHOST] or [RequesterType#ANONYMOUS] requester
public record Requester(RequesterType type, @Nullable String name) {

    public Requester {
        if (type == RequesterType.USER && name == null) {
            throw new IllegalArgumentException("A user requester is named");
        }
        if ((type == RequesterType.LOCALHOST || type == RequesterType.ANONYMOUS) && name != null) {
            throw new IllegalArgumentException("A " + type.key() + " requester is not named");
        }
    }

    public static Requester user(final String username) {
        return new Requester(RequesterType.USER, username);
    }

    public static Requester mindustryNetwork(final @Nullable String network) {
        return new Requester(RequesterType.MINDUSTRY_NETWORK, network);
    }

    public static Requester localhost() {
        return new Requester(RequesterType.LOCALHOST, null);
    }

    public static Requester anonymous() {
        return new Requester(RequesterType.ANONYMOUS, null);
    }
}
