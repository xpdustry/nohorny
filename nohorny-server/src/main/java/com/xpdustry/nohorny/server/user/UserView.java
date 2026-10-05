// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.user;

import java.time.Instant;

/// The JSON representation of a user account.
///
/// @param rateLimit the classifications per minute, shared by all the addresses of the account
/// @param bootstrap whether this is the bootstrap administrator configured on the server
public record UserView(String username, boolean admin, int rateLimit, Instant createdAt, boolean bootstrap) {}
