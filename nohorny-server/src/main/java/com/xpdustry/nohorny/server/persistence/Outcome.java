// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Stored flat by its owners: the `outcome` column holds the [RatingBucket], next to the nullable `confidence` of a
/// verdict and `error` of a failure. The owners rebuild it with [#of] and flatten it with [#confidence] and [#error],
/// the rest of the code switches over the permitted subtypes.
public sealed interface Outcome permits Verdict, Failure {

    RatingBucket bucket();

    static @Nullable Double confidence(final Outcome outcome) {
        return switch (outcome) {
            case Verdict verdict -> verdict.confidence();
            case Failure ignored -> null;
        };
    }

    static @Nullable String error(final Outcome outcome) {
        return switch (outcome) {
            case Verdict ignored -> null;
            case Failure failure -> failure.error();
        };
    }

    static Outcome of(final RatingBucket bucket, final @Nullable Double confidence, final @Nullable String error) {
        final var rating = bucket.rating();
        return rating.isPresent()
                ? new Verdict(rating.get(), Objects.requireNonNull(confidence, "A verdict has a confidence"))
                : new Failure(Objects.requireNonNull(error, "A failure has an error"));
    }
}
