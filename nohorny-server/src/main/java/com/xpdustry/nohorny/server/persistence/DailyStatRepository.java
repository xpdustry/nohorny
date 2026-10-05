// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface DailyStatRepository extends Repository<DailyStat, DailyStat.Key> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO daily_stat (day, bucket, count) VALUES (:day, :bucket, 1)
            ON CONFLICT (day, bucket) DO UPDATE SET count = count + 1
            """)
    void increment(String day, String bucket);

    /// Counts a request in the statistics of its day.
    default void increment(final Instant createdAt, final RatingBucket bucket) {
        this.increment(createdAt.atOffset(ZoneOffset.UTC).toLocalDate().toString(), bucket.name());
    }

    /// @return the number of requests per bucket since the first one, deleted requests included
    @Query("SELECT d.id.bucket AS bucket, SUM(d.count) AS total FROM DailyStat d GROUP BY d.id.bucket")
    List<BucketCount> countByBucket();

    /// @param from the first ISO-8601 day, included
    /// @return the number of requests per day and bucket since the given day, missing slots have none
    @Query("""
            SELECT d.id.day AS slot, d.id.bucket AS bucket, d.count AS total FROM DailyStat d
            WHERE d.id.day >= :from
            """)
    List<SlotCount> countByDaySince(String from);
}
