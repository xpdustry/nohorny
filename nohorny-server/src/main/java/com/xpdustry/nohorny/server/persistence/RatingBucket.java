// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.Rating;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public enum RatingBucket {
    SAFE(Rating.SAFE),
    WARN(Rating.WARN),
    NSFW(Rating.NSFW),
    FAILED(null);

    private final @Nullable Rating rating;

    RatingBucket(final @Nullable Rating rating) {
        this.rating = rating;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

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
