// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/// The number of requests per day of a plugin version, kept after the requests themselves are deleted. The requests
/// without a version are only counted in [DailyStat].
///
/// Only incremented through [DailyVersionStatRepository#increment].
@Entity
@Table(name = "daily_version_stat")
public class DailyVersionStat {

    @EmbeddedId
    private Key id;

    private long count;

    @SuppressWarnings("NullAway")
    protected DailyVersionStat() {}

    public Key getId() {
        return this.id;
    }

    public long getCount() {
        return this.count;
    }

    /// @param day the ISO-8601 date in UTC
    /// @param version the plugin version sent by the client
    @Embeddable
    public record Key(String day, String version) {}
}
