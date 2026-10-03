// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.user;

import java.time.Instant;

/// The JSON representation of a user account.
///
/// @param bootstrap whether this is the bootstrap administrator configured on the server
public record UserView(String username, boolean admin, Instant createdAt, boolean bootstrap) {}
