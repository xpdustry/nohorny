// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

/// The number of requests of a [RatingBucket] in a time slot.
public interface SlotCount {

    /// @return the slot, an ISO-8601 date for days or the epoch hour for hours
    String getSlot();

    RatingBucket getBucket();

    long getTotal();
}
