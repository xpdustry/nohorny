// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.Rating;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/// Reads never select the image column, except [#findImageById].
public interface ClassificationRequestRepository extends Repository<ClassificationRequest, String> {

    String SELECT_SUMMARY = """
            SELECT new com.xpdustry.nohorny.server.persistence.ClassificationRequestSummary(
                r.id, r.createdAt, r.durationMillis, r.successful, r.rating, r.confidence, r.classifier, r.error,
                r.version, r.username, r.remoteAddress, r.imageMediaType, r.imageState)
            FROM ClassificationRequest r
            """;

    /// Greater than every UUIDv7, so the first page shares the indexed `id < :before` condition of the others.
    String NO_CURSOR = new UUID(-1L, -1L).toString();

    ClassificationRequest save(ClassificationRequest request);

    @Query(SELECT_SUMMARY + "WHERE r.id = :id")
    Optional<ClassificationRequestSummary> findSummaryById(String id);

    @Query(SELECT_SUMMARY + "WHERE r.id < :before ORDER BY r.id DESC")
    List<ClassificationRequestSummary> findSummariesBefore(String before, Limit limit);

    @Query(SELECT_SUMMARY + "WHERE r.rating = :rating AND r.id < :before ORDER BY r.id DESC")
    List<ClassificationRequestSummary> findSummariesByRatingBefore(Rating rating, String before, Limit limit);

    @Query(SELECT_SUMMARY + "WHERE r.rating IS NULL AND r.id < :before ORDER BY r.id DESC")
    List<ClassificationRequestSummary> findFailedSummariesBefore(String before, Limit limit);

    /// The steps are not included, see [#findSteps].
    ///
    /// @param bucket only return the requests of this bucket, or all if `null`
    /// @param before only return the requests with an identifier lower than this one, thus older
    /// @return the newest requests matching the filters
    default List<ClassificationRequestSummary> findSummaries(
            final @Nullable RatingBucket bucket, final @Nullable String before, final int limit) {
        final var cursor = before == null ? NO_CURSOR : before;
        final var max = Limit.of(limit);
        if (bucket == null) {
            return this.findSummariesBefore(cursor, max);
        }
        final var rating = bucket.rating();
        return rating == null
                ? this.findFailedSummariesBefore(cursor, max)
                : this.findSummariesByRatingBefore(rating, cursor, max);
    }

    /// @return the steps of the given requests, ordered by request then position
    @Query("""
            SELECT new com.xpdustry.nohorny.server.persistence.ClassificationStepRow(
                r.id, s.classifier, s.rating, s.confidence, s.durationMillis, s.error)
            FROM ClassificationRequest r JOIN r.steps s
            WHERE r.id IN :ids
            ORDER BY r.id, INDEX(s)
            """)
    List<ClassificationStepRow> findSteps(Collection<String> ids);

    @Query("""
            SELECT new com.xpdustry.nohorny.server.persistence.StoredImage(r.imageState, r.imageMediaType, r.image)
            FROM ClassificationRequest r
            WHERE r.id = :id
            """)
    Optional<StoredImage> findImageById(String id);

    /// Deletes the image bytes and marks the image as purged, whatever its state.
    ///
    /// @return `0` if the request does not exist
    @Modifying
    @Query("UPDATE ClassificationRequest r SET r.image = NULL, r.imageState = PURGED WHERE r.id = :id")
    int purgeImage(String id);

    /// Deletes the stored images of the requests created before the given instant.
    ///
    /// @return the number of expired images
    @Modifying
    @Query("""
            UPDATE ClassificationRequest r SET r.image = NULL, r.imageState = EXPIRED
            WHERE r.imageState = STORED AND r.createdAt < :before
            """)
    int expireImagesBefore(Instant before);

    /// @return `0` if the request does not exist
    @Modifying
    @Query("DELETE FROM ClassificationRequest r WHERE r.id = :id")
    int deleteById(String id);

    /// Deletes the requests created before the given instant, the daily statistics are kept.
    ///
    /// @return the number of deleted requests
    @Modifying
    @Query("DELETE FROM ClassificationRequest r WHERE r.createdAt < :before")
    int deleteCreatedBefore(Instant before);

    /// Native, grouping by the bucket expression makes SQLite search the created_at index,
    /// grouping by the rating column would make it scan the rating index instead.
    @Query(nativeQuery = true, value = """
            SELECT COALESCE(rating, 'FAILED') AS bucket, COUNT(*) AS total
            FROM request
            WHERE created_at >= :since
            GROUP BY bucket
            """)
    List<BucketCount> countByBucketSince(long since);

    /// @return the number of retained requests per bucket created since the given instant, missing buckets have none
    default List<BucketCount> countByBucketSince(final Instant since) {
        return this.countByBucketSince(since.toEpochMilli());
    }
}
