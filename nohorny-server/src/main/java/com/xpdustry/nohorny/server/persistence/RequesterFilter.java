// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import org.jspecify.annotations.Nullable;

/// Restricts a list of requests to one requester type, and optionally to one name.
///
/// @param type the requester type
/// @param name `null` for any name, empty for the requesters without one, like the listed servers without a network,
///     or the name itself
public record RequesterFilter(RequesterType type, @Nullable String name) {}
