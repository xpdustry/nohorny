// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

public interface SlotCount {

    /// @return the slot, an ISO-8601 date for days or the epoch hour for hours
    String getSlot();

    RatingBucket getBucket();

    long getTotal();
}
