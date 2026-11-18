// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.server.database.SQLiteDatabase;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/// The number of requests per day, bucket, requester type, network and plugin version in `daily_stat`, kept after the
/// requests themselves are deleted. Each breakdown of the statistics is a sum over the other columns.
///
/// None of the key columns is null, the empty string stands for the absence of a value: SQLite `UNIQUE` treats NULLs
/// as distinct, so the upsert of a key holding one would never conflict. The day is the ISO-8601 date in UTC, the
/// requester type the name of the [RequesterType] or empty for the counts migrated without their requests, the
/// network the one of a named [RequesterType#MINDUSTRY_NETWORK] requester, and the version the plugin version sent by
/// the client, absent past [#MAX_VERSION_LENGTH].
@Component
public final class DailyStatRepository {

    /// The longest version counted. The clients send it freely, a longer one is not a release.
    public static final int MAX_VERSION_LENGTH = 32;

    private final SQLiteDatabase database;

    public DailyStatRepository(final SQLiteDatabase database) {
        this.database = database;
    }

    public void increment(final ClassificationRequest request) {
        final var requester = request.requester();
        final var version = request.version();
        this.add(
                request.createdAt().atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                request.outcome().bucket(),
                requester.type().name(),
                networkOf(requester),
                version != null && version.length() <= MAX_VERSION_LENGTH ? version : "",
                1);
    }

    /// Moves counted requests to another requester, the rows left empty are deleted.
    ///
    /// @param counts the requests per key before the change, see [ClassificationRequestRepository#countRelabeled]
    /// @param requester the requester they now have
    public void move(final List<DailyCount> counts, final Requester requester) {
        this.database.withTransaction(transaction -> {
            final var network = networkOf(requester);
            for (final var count : counts) {
                this.add(
                        count.day(),
                        count.bucket(),
                        count.requesterType(),
                        count.network(),
                        count.version(),
                        -count.total());
                this.add(count.day(), count.bucket(), requester.type().name(), network, count.version(), count.total());
            }
            return transaction
                    .prepareStatement("DELETE FROM daily_stat WHERE count <= 0")
                    .executeUpdate();
        });
    }

    private void add(
            final String day,
            final RatingBucket bucket,
            final String requesterType,
            final String network,
            final String version,
            final long delta) {
        this.database.withTransaction(transaction -> transaction
                .prepareStatement("""
                        INSERT INTO daily_stat (day, bucket, requester_type, network, version, count)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (day, bucket, requester_type, network, version) DO UPDATE SET count = count + ?
                        """)
                .bind(day)
                .bind(bucket)
                .bind(requesterType)
                .bind(network)
                .bind(version)
                .bind(delta)
                .bind(delta)
                .executeUpdate());
    }

    public List<BucketCount> countByBucket() {
        return this.database.withReadTransaction(transaction -> transaction
                .prepareStatement("SELECT bucket, SUM(count) AS total FROM daily_stat GROUP BY bucket")
                .executeSelect(result -> new BucketCount(bucketOf(result), result.getLong("total"))));
    }

    /// @param from the first ISO-8601 day, included
    public List<SlotCount> countByDaySince(final String from) {
        return this.database.withReadTransaction(transaction -> transaction
                .prepareStatement("""
                        SELECT day, bucket, SUM(count) AS total FROM daily_stat
                        WHERE day >= ?
                        GROUP BY day, bucket
                        """)
                .bind(from)
                .executeSelect(
                        result -> new SlotCount(result.getString("day"), bucketOf(result), result.getLong("total"))));
    }

    /// @param from the first ISO-8601 day, included
    public List<NamedCount> countByNetworkSince(final String from) {
        return this.database.withReadTransaction(transaction ->
                transaction.prepareStatement("""
                        SELECT network AS name, SUM(count) AS total FROM daily_stat
                        WHERE day >= ? AND network <> ''
                        GROUP BY network
                        """).bind(from).executeSelect(DailyStatRepository::namedCount));
    }

    /// @param from the first ISO-8601 day, included
    /// @return the number of requests per [RequesterType] name since the given day, the named Mindustry networks
    ///     excluded, deleted requests included. The name is empty for the counts migrated without their requests
    public List<NamedCount> countByUnnamedRequesterSince(final String from) {
        return this.database.withReadTransaction(transaction ->
                transaction.prepareStatement("""
                        SELECT requester_type AS name, SUM(count) AS total FROM daily_stat
                        WHERE day >= ? AND network = ''
                        GROUP BY requester_type
                        """).bind(from).executeSelect(DailyStatRepository::namedCount));
    }

    /// @param from the first ISO-8601 day, included
    public List<NamedCount> countByVersionSince(final String from) {
        return this.database.withReadTransaction(transaction ->
                transaction.prepareStatement("""
                        SELECT version AS name, SUM(count) AS total FROM daily_stat
                        WHERE day >= ? AND version <> ''
                        GROUP BY version
                        """).bind(from).executeSelect(DailyStatRepository::namedCount));
    }

    private static String networkOf(final Requester requester) {
        return requester.type() == RequesterType.MINDUSTRY_NETWORK
                ? Objects.requireNonNullElse(requester.name(), "")
                : "";
    }

    static RatingBucket bucketOf(final ResultSet result) throws SQLException {
        return RatingBucket.valueOf(result.getString("bucket"));
    }

    static NamedCount namedCount(final ResultSet result) throws SQLException {
        return new NamedCount(result.getString("name"), result.getLong("total"));
    }
}
