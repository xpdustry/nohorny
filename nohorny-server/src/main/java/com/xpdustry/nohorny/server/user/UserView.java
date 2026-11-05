// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.user;

import java.time.Instant;

public record UserView(String username, boolean admin, int rateLimit, Instant createdAt, boolean bootstrap) {}
