// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.mapping;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// @param type the requester the clients become: `user`, `mindustry-network`, `local` or `anonymous`
/// @param name the username of a user or the network name, `null` for the other types
public record RequesterMappingView(
        String id, String range, String type, @Nullable String name, Instant createdAt) {}
