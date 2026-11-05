// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/// The number of requests per day, bucket, requester type, network and plugin version, kept after the requests
/// themselves are deleted. Each breakdown of the statistics is a sum over the other columns.
@Entity
@Table(name = "daily_stat")
public class DailyStat {

    /// The longest version counted. The clients send it freely, a longer one is not a release.
    public static final int MAX_VERSION_LENGTH = 32;

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

    /// None of the columns is null, the empty string stands for the absence of a value: SQLite `UNIQUE` treats NULLs
    /// as distinct, so the upsert of a key holding one would never conflict.
    ///
    /// @param day the ISO-8601 date in UTC
    /// @param requesterType the name of the [RequesterType], empty for the counts migrated without their requests
    /// @param network the network of a named [RequesterType#MINDUSTRY_NETWORK] requester
    /// @param version the plugin version sent by the client, absent past [#MAX_VERSION_LENGTH]
    @Embeddable
    public record Key(
            String day,
            @Enumerated(EnumType.STRING) RatingBucket bucket,
            String requesterType,
            String network,
            String version) {}
}
