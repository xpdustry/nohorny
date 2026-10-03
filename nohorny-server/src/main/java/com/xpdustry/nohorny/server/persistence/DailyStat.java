// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/// The number of requests per day and bucket, kept after the requests themselves are deleted.
///
/// Only incremented through [DailyStatRepository#increment].
@Entity
@Table(name = "daily_stat")
public class DailyStat {

    @EmbeddedId
    private Key id;

    private long count;

    @SuppressWarnings("NullAway")
    protected DailyStat() {}

    public Key getId() {
        return this.id;
    }

    public long getCount() {
        return this.count;
    }

    /// @param day the ISO-8601 date in UTC
    @Embeddable
    public record Key(
            String day, @Enumerated(EnumType.STRING) RatingBucket bucket) {}
}
