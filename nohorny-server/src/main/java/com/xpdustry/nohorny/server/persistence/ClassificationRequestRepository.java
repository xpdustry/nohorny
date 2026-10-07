// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ClassificationRequestRepository extends Repository<ClassificationRequest, String> {

    /// Greater than every UUIDv7, so the first page shares the indexed `id < :before` condition of the others.
    String NO_CURSOR = new UUID(-1L, -1L).toString();

    ClassificationRequest save(ClassificationRequest request);

    Optional<ClassificationRequest> findById(String id);

    @Query("SELECT r FROM ClassificationRequest r WHERE r.id < :before ORDER BY r.id DESC")
    List<ClassificationRequest> findBefore(String before, Limit limit);

    @Query("SELECT r FROM ClassificationRequest r WHERE r.bucket = :bucket AND r.id < :before ORDER BY r.id DESC")
    List<ClassificationRequest> findByBucketBefore(RatingBucket bucket, String before, Limit limit);

    /// @param bucket only return the requests of this bucket, or all if `null`
    /// @param before only return the requests with an identifier lower than this one, thus older
    /// @return the newest requests matching the filters
    default List<ClassificationRequest> findPage(
            final @Nullable RatingBucket bucket, final @Nullable String before, final int limit) {
        final var cursor = before == null ? NO_CURSOR : before;
        final var max = Limit.of(limit);
        return bucket == null ? this.findBefore(cursor, max) : this.findByBucketBefore(bucket, cursor, max);
    }

    /// @return the number of requests whose image is stored under the given hash
    @Query("SELECT COUNT(r) FROM ClassificationRequest r WHERE r.imageHash = :hash AND r.imageState = STORED")
    long countStoredByHash(String hash);

    /// Finds the images that no request created at or after the given instant stores. Their files can go once the
    /// older requests are expired or deleted.
    ///
    /// @return the hashes of the stored images of the requests created before the given instant, minus those still
    ///     stored by a newer request
    @Query("""
            SELECT DISTINCT r.imageHash
            FROM ClassificationRequest r
            WHERE r.imageState = STORED AND r.createdAt < :before AND NOT EXISTS (
                SELECT 1 FROM ClassificationRequest o
                WHERE o.imageHash = r.imageHash AND o.imageState = STORED AND o.createdAt >= :before)
            """)
    List<String> findHashesStoredOnlyBefore(Instant before);

    /// Marks the image as purged, whatever its state. The file is left to the caller, see [ImageStore].
    ///
    /// The bulk updates and deletes clear the persistence context, so an entity read earlier in the transaction is
    /// loaded again rather than served stale from the cache.
    ///
    /// @return `0` if the request does not exist
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ClassificationRequest r SET r.imageState = PURGED WHERE r.id = :id")
    int purgeImage(String id);

    /// Marks the stored images of the requests created before the given instant as expired. The files are left to
    /// the caller, see [ImageStore].
    ///
    /// @return the number of expired images
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ClassificationRequest r SET r.imageState = EXPIRED
            WHERE r.imageState = STORED AND r.createdAt < :before
            """)
    int expireImagesBefore(Instant before);

    /// @return `0` if the request does not exist
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ClassificationRequest r WHERE r.id = :id")
    int deleteById(String id);

    /// Deletes the requests created before the given instant, the daily statistics are kept.
    ///
    /// @return the number of deleted requests
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ClassificationRequest r WHERE r.createdAt < :before")
    int deleteCreatedBefore(Instant before);

    /// Native to pin the created_at index. Since the outcome index is ordered by the grouped column,
    /// SQLite would rather scan it whole than search the time window and sort.
    @Query(nativeQuery = true, value = """
            SELECT outcome AS bucket, COUNT(*) AS total
            FROM request INDEXED BY request_created_at_idx
            WHERE created_at >= :since
            GROUP BY outcome
            """)
    List<BucketCount> countByBucketSince(long since);

    /// @return the number of retained requests per bucket created since the given instant, missing buckets have none
    default List<BucketCount> countByBucketSince(final Instant since) {
        return this.countByBucketSince(since.toEpochMilli());
    }

    @Query(nativeQuery = true, value = """
            SELECT CAST(created_at / 3600000 AS TEXT) AS slot, outcome AS bucket, COUNT(*) AS total
            FROM request INDEXED BY request_created_at_idx
            WHERE created_at >= :since
            GROUP BY slot, bucket
            """)
    List<SlotCount> countByHourSince(long since);

    @Query(nativeQuery = true, value = """
            SELECT requester_name AS name, COUNT(*) AS total
            FROM request
            WHERE created_at >= :since AND requester_type = 'MINDUSTRY_NETWORK' AND requester_name IS NOT NULL
            GROUP BY requester_name
            """)
    List<NamedCount> countByNetworkSince(long since);

    /// @return the number of retained requests per named Mindustry network requester created since the given
    ///     instant
    default List<NamedCount> countByNetworkSince(final Instant since) {
        return this.countByNetworkSince(since.toEpochMilli());
    }

    @Query(nativeQuery = true, value = """
            SELECT version AS name, COUNT(*) AS total
            FROM request
            WHERE created_at >= :since AND version IS NOT NULL AND length(version) BETWEEN 1 AND 32
            GROUP BY version
            """)
    List<NamedCount> countByVersionSince(long since);

    /// @return the number of retained requests per plugin version created since the given instant, the versions past
    ///     32 characters left out like in [DailyVersionStat]
    default List<NamedCount> countByVersionSince(final Instant since) {
        return this.countByVersionSince(since.toEpochMilli());
    }

    /// @return the number of retained requests per epoch hour and bucket since the given instant,
    ///     missing slots have none
    default List<SlotCount> countByHourSince(final Instant since) {
        return this.countByHourSince(since.toEpochMilli());
    }
}
