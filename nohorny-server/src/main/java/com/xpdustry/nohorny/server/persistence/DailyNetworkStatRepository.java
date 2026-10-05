// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface DailyNetworkStatRepository extends Repository<DailyNetworkStat, DailyNetworkStat.Key> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO daily_network_stat (day, network, count) VALUES (:day, :network, 1)
            ON CONFLICT (day, network) DO UPDATE SET count = count + 1
            """)
    void increment(String day, String network);

    /// Counts a request in the statistics of its day and network.
    default void increment(final Instant createdAt, final String network) {
        this.increment(createdAt.atOffset(ZoneOffset.UTC).toLocalDate().toString(), network);
    }

    /// @param from the first ISO-8601 day, included
    /// @return the number of requests per listed network since the given day, deleted requests included
    @Query("""
            SELECT d.id.network AS network, SUM(d.count) AS total FROM DailyNetworkStat d
            WHERE d.id.day >= :from
            GROUP BY d.id.network
            """)
    List<NetworkCount> countByNetworkSince(String from);
}
