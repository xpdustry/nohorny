// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface DailyVersionStatRepository extends Repository<DailyVersionStat, DailyVersionStat.Key> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO daily_version_stat (day, version, count) VALUES (:day, :version, 1)
            ON CONFLICT (day, version) DO UPDATE SET count = count + 1
            """)
    void increment(String day, String version);

    /// Counts a request in the statistics of its day and plugin version.
    default void increment(final Instant createdAt, final String version) {
        this.increment(createdAt.atOffset(ZoneOffset.UTC).toLocalDate().toString(), version);
    }

    /// @param from the first ISO-8601 day, included
    /// @return the number of requests per plugin version since the given day, deleted requests included
    @Query("""
            SELECT d.id.version AS name, SUM(d.count) AS total FROM DailyVersionStat d
            WHERE d.id.day >= :from
            GROUP BY d.id.version
            """)
    List<NamedCount> countByVersionSince(String from);
}
