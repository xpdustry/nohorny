// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.Rating;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/// The outcome of a classification request, used by the filters and the statistics.
public enum RatingBucket {
    SAFE,
    WARN,
    NSFW,
    FAILED;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /// @return the rating of the requests in this bucket, `null` for the failed ones
    public @Nullable Rating rating() {
        return switch (this) {
            case SAFE -> Rating.SAFE;
            case WARN -> Rating.WARN;
            case NSFW -> Rating.NSFW;
            case FAILED -> null;
        };
    }

    public static RatingBucket of(final @Nullable Rating rating) {
        return switch (rating) {
            case SAFE -> SAFE;
            case WARN -> WARN;
            case NSFW -> NSFW;
            case null -> FAILED;
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
