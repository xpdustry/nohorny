// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.Rating;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/// The [Outcome] of a classification request as counted by the statistics and selected by the filters:
/// the rating of a [Verdict], or `FAILED` for a [Failure].
public enum RatingBucket {
    SAFE(Rating.SAFE),
    WARN(Rating.WARN),
    NSFW(Rating.NSFW),
    FAILED(null);

    private final @Nullable Rating rating;

    RatingBucket(final @Nullable Rating rating) {
        this.rating = rating;
    }

    /// @return the lowercase name used by the API
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /// @return the rating of the verdicts in this bucket, empty for the failures
    public Optional<Rating> rating() {
        return Optional.ofNullable(this.rating);
    }

    public static RatingBucket of(final Rating rating) {
        return switch (rating) {
            case SAFE -> SAFE;
            case WARN -> WARN;
            case NSFW -> NSFW;
        };
    }

    public static Optional<RatingBucket> fromKey(final String key) {
        for (final var bucket : values()) {
            if (bucket.key().equals(key)) {
                return Optional.of(bucket);
            }
        }
        return Optional.empty();
    }
}
