// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.util.List;

/// The classified requests per listed Mindustry network and per other requester, over the windows of [History].
///
/// @param range the requested range, `24h`, `7d`, `30d` or `90d`
/// @param total every request of the range
/// @param networks the listed networks by their normalized name, the most active first
/// @param requesters the requests not from a listed network, by `RequesterType` key, the most active first. A
///     `mindustry-network` count comes from the listed servers without a network, an empty key from the counts
///     migrated without their requests
public record Networks(String range, long total, List<Count> networks, List<Count> requesters) {}
