// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.util.List;

/// @param requesters the requests not from a listed network, by `RequesterType` key, the most active first. A
///     `mindustry-network` count comes from the listed servers without a network, an empty key from the counts
///     migrated without their requests
public record Networks(String range, long total, List<Count> networks, List<Count> requesters) {}
