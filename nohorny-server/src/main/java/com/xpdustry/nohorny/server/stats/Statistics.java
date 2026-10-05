// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import com.xpdustry.nohorny.server.persistence.RatingBucket;
import java.time.Instant;
import java.util.Map;

/// Aggregated classification counters, without any per-request data.
///
/// @param total the number of requests since the first one, deleted requests included
/// @param last24Hours the retained requests of the last 24 hours
public record Statistics(long total, Ratings ratings, Window last24Hours, Instant updatedAt) {

    public record Window(long total, Ratings ratings) {

        static Window of(final Map<RatingBucket, Long> counts) {
            final var ratings = Ratings.of(counts);
            return new Window(ratings.safe() + ratings.warn() + ratings.nsfw() + ratings.failed(), ratings);
        }
    }

    public record Ratings(long safe, long warn, long nsfw, long failed) {

        static Ratings of(final Map<RatingBucket, Long> counts) {
            return new Ratings(
                    counts.getOrDefault(RatingBucket.SAFE, 0L),
                    counts.getOrDefault(RatingBucket.WARN, 0L),
                    counts.getOrDefault(RatingBucket.NSFW, 0L),
                    counts.getOrDefault(RatingBucket.FAILED, 0L));
        }
    }
}
