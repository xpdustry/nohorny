// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/// The number of requests per day of a named [RequesterType#MINDUSTRY_NETWORK] requester, kept after the requests
/// themselves are deleted. The requests of the other clients are only counted in [DailyStat].
///
/// Only incremented through [DailyNetworkStatRepository#increment].
@Entity
@Table(name = "daily_network_stat")
public class DailyNetworkStat {

    @EmbeddedId
    private Key id;

    private long count;

    @SuppressWarnings("NullAway")
    protected DailyNetworkStat() {}

    public Key getId() {
        return this.id;
    }

    public long getCount() {
        return this.count;
    }

    /// @param day the ISO-8601 date in UTC
    /// @param network the normalized network name
    @Embeddable
    public record Key(String day, String network) {}
}
