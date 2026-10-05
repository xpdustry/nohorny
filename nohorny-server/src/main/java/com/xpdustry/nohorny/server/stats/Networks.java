// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.util.List;

/// The classified requests per listed Mindustry network without an account, over the windows of [History].
///
/// @param range the requested range, `24h`, `7d`, `30d` or `90d`
/// @param total every request of the range. The part not covered by the networks comes from the users and the
///     anonymous clients
/// @param networks the listed networks, the most active first
public record Networks(String range, long total, List<Network> networks) {

    /// @param name the normalized network name
    public record Network(String name, long count) {}
}
