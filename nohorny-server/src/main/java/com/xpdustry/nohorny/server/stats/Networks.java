// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.util.List;

/// The classified requests per listed Mindustry network and from the loopback interface without an account, over the
/// windows of [History].
///
/// @param range the requested range, `24h`, `7d`, `30d` or `90d`
/// @param total every request of the range. The part not covered by the networks and the loopback interface comes
///     from the users, the unnamed listed servers and the anonymous clients
/// @param networks the listed networks by their normalized name, the most active first
/// @param localhost the requests from the loopback interface, like a server running on the same machine
public record Networks(String range, long total, List<Count> networks, long localhost) {}
