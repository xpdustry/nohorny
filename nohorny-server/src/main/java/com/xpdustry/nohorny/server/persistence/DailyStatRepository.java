// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface DailyStatRepository extends Repository<DailyStat, DailyStat.Key> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO daily_stat (day, bucket, requester_type, network, version, count)
            VALUES (:day, :bucket, :requesterType, :network, :version, 1)
            ON CONFLICT (day, bucket, requester_type, network, version) DO UPDATE SET count = count + 1
            """)
    void increment(String day, String bucket, String requesterType, String network, String version);

    /// Counts a request in the statistics of its day.
    default void increment(final ClassificationRequest request) {
        final var requester = request.getRequester();
        final var version = request.getVersion();
        this.increment(
                request.getCreatedAt().atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                request.bucket().name(),
                requester.type().name(),
                requester.type() == RequesterType.MINDUSTRY_NETWORK
                        ? Objects.requireNonNullElse(requester.name(), "")
                        : "",
                version != null && version.length() <= DailyStat.MAX_VERSION_LENGTH ? version : "");
    }

    /// @return the number of requests per bucket since the first one, deleted requests included
    @Query("SELECT d.id.bucket AS bucket, SUM(d.count) AS total FROM DailyStat d GROUP BY d.id.bucket")
    List<BucketCount> countByBucket();

    /// @param from the first ISO-8601 day, included
    /// @return the number of requests per day and bucket since the given day, missing slots have none
    @Query("""
            SELECT d.id.day AS slot, d.id.bucket AS bucket, SUM(d.count) AS total FROM DailyStat d
            WHERE d.id.day >= :from
            GROUP BY d.id.day, d.id.bucket
            """)
    List<SlotCount> countByDaySince(String from);

    /// @param from the first ISO-8601 day, included
    /// @return the number of requests per listed network since the given day, deleted requests included
    @Query("""
            SELECT d.id.network AS name, SUM(d.count) AS total FROM DailyStat d
            WHERE d.id.day >= :from AND d.id.network <> ''
            GROUP BY d.id.network
            """)
    List<NamedCount> countByNetworkSince(String from);

    /// @param from the first ISO-8601 day, included
    /// @return the number of requests from the loopback interface since the given day, deleted requests included
    @Query("""
            SELECT COALESCE(SUM(d.count), 0) FROM DailyStat d
            WHERE d.id.day >= :from AND d.id.requesterType = 'LOCALHOST'
            """)
    long countLocalhostSince(String from);

    /// @param from the first ISO-8601 day, included
    /// @return the number of requests per plugin version since the given day, deleted requests included
    @Query("""
            SELECT d.id.version AS name, SUM(d.count) AS total FROM DailyStat d
            WHERE d.id.day >= :from AND d.id.version <> ''
            GROUP BY d.id.version
            """)
    List<NamedCount> countByVersionSince(String from);
}
