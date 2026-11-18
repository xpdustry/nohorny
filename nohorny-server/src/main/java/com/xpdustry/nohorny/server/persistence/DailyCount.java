// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

/// The requests counted under a key of `daily_stat`, see [DailyStatRepository].
public record DailyCount(
        String day, RatingBucket bucket, String requesterType, String network, String version, long total) {}
