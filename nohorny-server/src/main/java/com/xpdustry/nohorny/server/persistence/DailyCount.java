// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

public interface DailyCount {

    String getDay();

    RatingBucket getBucket();

    String getRequesterType();

    String getNetwork();

    String getVersion();

    long getTotal();
}
