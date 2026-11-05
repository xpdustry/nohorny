// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.time.Instant;
import java.util.List;

/// @param range the requested range, `24h`, `7d`, `30d` or `90d`
/// @param slot the length of a slot, `hour` or `day`
public record History(String range, String slot, List<Slot> slots) {

    public record Slot(Instant start, Statistics.Ratings ratings) {}
}
