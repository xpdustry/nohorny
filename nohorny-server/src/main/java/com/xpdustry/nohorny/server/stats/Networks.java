// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import com.xpdustry.nohorny.server.persistence.RequesterType;
import java.util.List;

/// @param requesters the requests not from a listed network, the most active first. A `mindustry-network` count comes
///     from the listed servers without a network
/// @param unattributed the requests counted before their requester was recorded
public record Networks(
        Range range, long total, List<Count> networks, List<RequesterCount> requesters, long unattributed) {

    public record RequesterCount(RequesterType type, long count) {}
}
