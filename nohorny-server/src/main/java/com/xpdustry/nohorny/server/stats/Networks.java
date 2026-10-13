// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.util.List;

/// The classified requests per listed Mindustry network and from the local addresses without an account, over the
/// windows of [History].
///
/// @param range the requested range, `24h`, `7d`, `30d` or `90d`
/// @param total every request of the range. The part not covered by the networks and the local addresses comes
///     from the users, the unnamed listed servers and the anonymous clients
/// @param networks the listed networks by their normalized name, the most active first
/// @param local the requests from the local addresses, like a server on the same machine or Docker network
public record Networks(String range, long total, List<Count> networks, long local) {}
