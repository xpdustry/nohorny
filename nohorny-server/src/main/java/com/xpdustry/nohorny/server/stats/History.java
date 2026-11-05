// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.time.Instant;
import java.util.List;

public record History(Range range, Range.Slot slot, List<Slot> slots) {

    public record Slot(Instant start, Statistics.Ratings ratings) {}
}
