// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.ArtKind;
import com.xpdustry.nohorny.server.database.SQLiteDatabase;
import com.xpdustry.nohorny.server.database.SQLiteDatabase.StatementBuilderExecute;
import com.xpdustry.nohorny.server.database.SQLiteDatabase.Transaction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/// The requests live in `request`, their steps in `request_step` at their position in the list. The steps go with
/// their request, the foreign key deletes them along.
@Component
public final class ClassificationRequestRepository {

    /// Greater than every UUIDv7, so the first page shares the indexed `id < ?` condition of the others.
    private static final String NO_CURSOR = new UUID(-1L, -1L).toString();

    private static final String SELECT_REQUEST = """
            SELECT id, created_at, duration_millis, classifier, outcome, confidence, error, version, art_kind,
                   requester_type, requester_name, authenticated, remote_address, image_state, image_hash
            FROM request
            """;

    private final SQLiteDatabase database;

    public ClassificationRequestRepository(final SQLiteDatabase database) {
        this.database = database;
    }

    public void insert(final ClassificationRequest request) {
        this.database.withTransaction(transaction -> {
            final var outcome = request.outcome();
            final var requester = request.requester();
            transaction
                    .prepareStatement("""
                            INSERT INTO request (id, created_at, duration_millis, classifier, outcome, confidence, error,
                                                 version, art_kind, requester_type, requester_name, authenticated,
                                                 remote_address, image_state, image_hash)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .bind(request.id())
                    .bind(request.createdAt())
                    .bind(request.durationMillis())
                    .bind(request.classifier())
                    .bind(outcome.bucket())
                    .bind(Outcome.confidence(outcome))
                    .bind(Outcome.error(outcome))
                    .bind(request.version())
                    .bind(request.artKind())
                    .bind(requester.type())
                    .bind(requester.name())
                    .bind(request.authenticated())
                    .bind(request.remoteAddress())
                    .bind(request.imageState())
                    .bind(request.imageHash())
                    .executeSingleUpdate();
            final var steps = request.steps();
            if (steps.isEmpty()) {
                return 0;
            }
            return transaction
                    .prepareStatement("""
                            INSERT INTO request_step
                                (request_id, position, classifier, duration_millis, outcome, confidence, error)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """)
                    .batch(IntStream.range(0, steps.size()).boxed().toList(), (binder, position) -> {
                        final var step = steps.get(position);
                        binder.bind(request.id())
                                .bind(position)
                                .bind(step.classifier())
                                .bind(step.durationMillis())
                                .bind(step.outcome().bucket())
                                .bind(Outcome.confidence(step.outcome()))
                                .bind(Outcome.error(step.outcome()));
                    })
                    .executeUpdate();
        });
    }

    public Optional<ClassificationRequest> findById(final String id) {
        return this.database.withReadTransaction(transaction -> {
            final var request = transaction
                    .prepareStatement(SELECT_REQUEST + "WHERE id = ?")
                    .bind(id)
                    .executeSingleSelect(ClassificationRequestRepository::mapRequest);
            return request.isEmpty()
                    ? request
                    : Optional.of(withSteps(transaction, List.of(request.get())).getFirst());
        });
    }

    /// @param before the identifier the page starts after, the first page if `null`
    public List<ClassificationRequest> findPage(
            final RequestFilter filter, final @Nullable String before, final int limit) {
        final var cursor = before == null ? NO_CURSOR : before;
        return this.database.withReadTransaction(transaction -> {
            final var requester = filter.requester();
            final var bucket = filter.bucket();
            final StatementBuilderExecute statement;
            if (requester != null || !filter.version().isAny()) {
                final var name = requester == null ? new ValueFilter.Any() : requester.name();
                final var version = filter.version();
                statement = transaction
                        .prepareStatement(SELECT_REQUEST + """
                                WHERE id < ?
                                    AND (? IS NULL OR outcome = ?)
                                    AND (? IS NULL OR requester_type = ?)
                                    AND (? = 1 OR requester_name = ? OR (? IS NULL AND requester_name IS NULL))
                                    AND (? = 1 OR version = ?
                                        OR (? IS NULL AND (version IS NULL OR length(version) NOT BETWEEN 1 AND ?)))
                                ORDER BY id DESC
                                LIMIT ?
                                """)
                        .bind(cursor)
                        .bind(bucket)
                        .bind(bucket)
                        .bind(requester == null ? null : requester.type())
                        .bind(requester == null ? null : requester.type())
                        .bind(name.isAny())
                        .bind(name.value())
                        .bind(name.value())
                        .bind(version.isAny())
                        .bind(version.value())
                        .bind(version.value())
                        .bind(DailyStatRepository.MAX_VERSION_LENGTH)
                        .bind(limit);
            } else if (bucket == null) {
                // The common lists keep their own queries, which use the indexes
                statement = transaction
                        .prepareStatement(SELECT_REQUEST + "WHERE id < ? ORDER BY id DESC LIMIT ?")
                        .bind(cursor)
                        .bind(limit);
            } else {
                statement = transaction
                        .prepareStatement(SELECT_REQUEST + "WHERE outcome = ? AND id < ? ORDER BY id DESC LIMIT ?")
                        .bind(bucket)
                        .bind(cursor)
                        .bind(limit);
            }
            return withSteps(transaction, statement.executeSelect(ClassificationRequestRepository::mapRequest));
        });
    }

    public long countStoredByHash(final String hash) {
        return this.database.withReadTransaction(transaction -> transaction
                .prepareStatement("SELECT COUNT(*) FROM request WHERE image_hash = ? AND image_state = 'STORED'")
                .bind(hash)
                .executeSelect(result -> result.getLong(1))
                .getFirst());
    }

    public List<String> findHashesStoredOnlyBefore(final Instant before) {
        return this.database.withReadTransaction(transaction -> transaction
                .prepareStatement("""
                        SELECT DISTINCT r.image_hash
                        FROM request r
                        WHERE r.image_state = 'STORED' AND r.created_at < ? AND NOT EXISTS (
                            SELECT 1 FROM request o
                            WHERE o.image_hash = r.image_hash AND o.image_state = 'STORED' AND o.created_at >= ?)
                        """)
                .bind(before)
                .bind(before)
                .executeSelect(result -> result.getString(1)));
    }

    /// Marks the image as deleted, whatever its state. The file is left to the caller, see [ImageStore].
    public int markImageDeleted(final String id) {
        return this.database.withTransaction(transaction -> transaction
                .prepareStatement("UPDATE request SET image_state = 'DELETED' WHERE id = ?")
                .bind(id)
                .executeUpdate());
    }

    /// Marks the stored images of the requests created before the given instant as expired. The files are left to
    /// the caller, see [ImageStore].
    public int markImagesExpiredBefore(final Instant before) {
        return this.database.withTransaction(
                transaction -> transaction.prepareStatement("""
                        UPDATE request SET image_state = 'EXPIRED'
                        WHERE image_state = 'STORED' AND created_at < ?
                        """).bind(before).executeUpdate());
    }

    public int deleteById(final String id) {
        return this.database.withTransaction(transaction -> transaction
                .prepareStatement("DELETE FROM request WHERE id = ?")
                .bind(id)
                .executeUpdate());
    }

    public int deleteCreatedBefore(final Instant before) {
        return this.database.withTransaction(transaction -> transaction
                .prepareStatement("DELETE FROM request WHERE created_at < ?")
                .bind(before)
                .executeUpdate());
    }

    public List<String> findUnauthenticatedAddresses() {
        return this.database.withReadTransaction(transaction -> transaction
                .prepareStatement("SELECT DISTINCT remote_address FROM request WHERE authenticated = 0")
                .executeSelect(result -> result.getString(1)));
    }

    /// The retained requests of the given addresses that [#relabel] would change, counted per `daily_stat` key like
    /// [DailyStatRepository#increment].
    public List<DailyCount> countRelabeled(final Collection<String> addresses, final Requester requester) {
        return this.database.withReadTransaction(transaction -> {
            final var statement = transaction
                    .prepareStatement("""
                            SELECT date(created_at / 1000, 'unixepoch') AS day,
                                   outcome AS bucket,
                                   requester_type,
                                   CASE WHEN requester_type = 'MINDUSTRY_NETWORK' THEN coalesce(requester_name, '')
                                       ELSE '' END AS network,
                                   CASE WHEN length(version) BETWEEN 1 AND ? THEN version ELSE '' END AS version,
                                   COUNT(*) AS total
                            FROM request
                            WHERE remote_address IN (%s) AND authenticated = 0
                              AND (requester_type <> ? OR requester_name IS NOT ?)
                            GROUP BY 1, 2, 3, 4, 5
                            """.formatted(placeholders(addresses.size())))
                    .bind(DailyStatRepository.MAX_VERSION_LENGTH);
            return bindAll(statement, addresses)
                    .bind(requester.type())
                    .bind(requester.name())
                    .executeSelect(result -> new DailyCount(
                            result.getString("day"),
                            DailyStatRepository.bucketOf(result),
                            result.getString("requester_type"),
                            result.getString("network"),
                            result.getString("version"),
                            result.getLong("total")));
        });
    }

    /// Gives the unauthenticated requests of the given addresses the requester, see [#countRelabeled].
    public int relabel(final Collection<String> addresses, final Requester requester) {
        return this.database.withTransaction(transaction -> {
            final var statement = transaction
                    .prepareStatement("""
                            UPDATE request SET requester_type = ?, requester_name = ?
                            WHERE remote_address IN (%s) AND authenticated = 0
                              AND (requester_type <> ? OR requester_name IS NOT ?)
                            """.formatted(placeholders(addresses.size())))
                    .bind(requester.type())
                    .bind(requester.name());
            return bindAll(statement, addresses)
                    .bind(requester.type())
                    .bind(requester.name())
                    .executeUpdate();
        });
    }

    /// Pins the created_at index. Since the outcome index is ordered by the grouped column, SQLite would rather scan
    /// it whole than search the time window and sort.
    public List<BucketCount> countByBucketSince(final Instant since) {
        return this.database.withReadTransaction(transaction -> transaction
                .prepareStatement("""
                        SELECT outcome AS bucket, COUNT(*) AS total
                        FROM request INDEXED BY request_created_at_idx
                        WHERE created_at >= ?
                        GROUP BY outcome
                        """)
                .bind(since)
                .executeSelect(
                        result -> new BucketCount(DailyStatRepository.bucketOf(result), result.getLong("total"))));
    }

    public List<SlotCount> countByHourSince(final Instant since) {
        return this.database.withReadTransaction(transaction -> transaction
                .prepareStatement("""
                        SELECT CAST(created_at / 3600000 AS TEXT) AS slot, outcome AS bucket, COUNT(*) AS total
                        FROM request INDEXED BY request_created_at_idx
                        WHERE created_at >= ?
                        GROUP BY slot, bucket
                        """)
                .bind(since)
                .executeSelect(result -> new SlotCount(
                        result.getString("slot"), DailyStatRepository.bucketOf(result), result.getLong("total"))));
    }

    public List<NamedCount> countByNetworkSince(final Instant since) {
        return this.database.withReadTransaction(transaction ->
                transaction.prepareStatement("""
                        SELECT requester_name AS name, COUNT(*) AS total
                        FROM request
                        WHERE created_at >= ? AND requester_type = 'MINDUSTRY_NETWORK' AND requester_name IS NOT NULL
                        GROUP BY requester_name
                        """).bind(since).executeSelect(DailyStatRepository::namedCount));
    }

    public List<NamedCount> countByUnnamedRequesterSince(final Instant since) {
        return this.database.withReadTransaction(transaction ->
                transaction.prepareStatement("""
                        SELECT requester_type AS name, COUNT(*) AS total
                        FROM request
                        WHERE created_at >= ?
                          AND NOT (requester_type = 'MINDUSTRY_NETWORK' AND requester_name IS NOT NULL)
                        GROUP BY requester_type
                        """).bind(since).executeSelect(DailyStatRepository::namedCount));
    }

    /// @return the number of retained requests per plugin version created since the given instant, the versions past
    ///     [DailyStatRepository#MAX_VERSION_LENGTH] characters left out
    public List<NamedCount> countByVersionSince(final Instant since) {
        return this.database.withReadTransaction(transaction -> transaction
                .prepareStatement("""
                        SELECT version AS name, COUNT(*) AS total
                        FROM request
                        WHERE created_at >= ? AND version IS NOT NULL AND length(version) BETWEEN 1 AND ?
                        GROUP BY version
                        """)
                .bind(since)
                .bind(DailyStatRepository.MAX_VERSION_LENGTH)
                .executeSelect(DailyStatRepository::namedCount));
    }

    /// Loads the steps of all the requests in one query.
    private static List<ClassificationRequest> withSteps(
            final Transaction transaction, final List<ClassificationRequest> requests) throws SQLException {
        if (requests.isEmpty()) {
            return requests;
        }
        final var statement = transaction.prepareStatement("""
                SELECT request_id, classifier, duration_millis, outcome, confidence, error
                FROM request_step
                WHERE request_id IN (%s)
                ORDER BY request_id, position
                """.formatted(placeholders(requests.size())));
        final var rows = bindAll(
                        statement,
                        requests.stream().map(ClassificationRequest::id).toList())
                .executeSelect(result -> Map.entry(
                        result.getString("request_id"),
                        new ClassificationStep(
                                result.getString("classifier"),
                                result.getLong("duration_millis"),
                                mapOutcome(result))));
        final var steps = new HashMap<String, List<ClassificationStep>>();
        for (final var row : rows) {
            steps.computeIfAbsent(row.getKey(), ignored -> new ArrayList<>()).add(row.getValue());
        }
        return requests.stream()
                .map(request -> request.withSteps(steps.getOrDefault(request.id(), List.of())))
                .toList();
    }

    private static StatementBuilderExecute bindAll(
            final StatementBuilderExecute statement, final Collection<String> values) throws SQLException {
        var bound = statement;
        for (final var value : values) {
            bound = bound.bind(value);
        }
        return bound;
    }

    private static String placeholders(final int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private static ClassificationRequest mapRequest(final ResultSet result) throws SQLException {
        final var artKind = result.getString("art_kind");
        return new ClassificationRequest(
                result.getString("id"),
                Instant.ofEpochMilli(result.getLong("created_at")),
                result.getLong("duration_millis"),
                result.getString("classifier"),
                mapOutcome(result),
                result.getString("version"),
                artKind == null ? null : ArtKind.valueOf(artKind),
                new Requester(
                        RequesterType.valueOf(result.getString("requester_type")), result.getString("requester_name")),
                result.getBoolean("authenticated"),
                result.getString("remote_address"),
                ImageState.valueOf(result.getString("image_state")),
                result.getString("image_hash"),
                List.of());
    }

    /// See [Outcome] for the columns.
    private static Outcome mapOutcome(final ResultSet result) throws SQLException {
        final var value = result.getDouble("confidence");
        final var confidence = result.wasNull() ? null : value;
        return Outcome.of(RatingBucket.valueOf(result.getString("outcome")), confidence, result.getString("error"));
    }
}
