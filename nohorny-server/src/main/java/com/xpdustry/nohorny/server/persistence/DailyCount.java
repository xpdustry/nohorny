// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

/// The number of requests sharing a [DailyStat] key.
public interface DailyCount {

    String getDay();

    RatingBucket getBucket();

    String getRequesterType();

    String getNetwork();

    String getVersion();

    long getTotal();
}
