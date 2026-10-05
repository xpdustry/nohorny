// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// What a classification produced, a [Verdict] or a [Failure].
///
/// Stored flat by its owners: the `outcome` column holds the [RatingBucket], next to the nullable `confidence` of a
/// verdict and `error` of a failure. The owners rebuild it with [#of] and flatten it with [#confidence] and [#error],
/// the rest of the code switches over the permitted subtypes.
public sealed interface Outcome permits Verdict, Failure {

    /// @return the bucket of the statistics and the filters, stored in the `outcome` column
    RatingBucket bucket();

    /// @return the confidence of a verdict, `null` for a failure
    static @Nullable Double confidence(final Outcome outcome) {
        return switch (outcome) {
            case Verdict verdict -> verdict.confidence();
            case Failure ignored -> null;
        };
    }

    /// @return the error of a failure, `null` for a verdict
    static @Nullable String error(final Outcome outcome) {
        return switch (outcome) {
            case Verdict ignored -> null;
            case Failure failure -> failure.error();
        };
    }

    /// Rebuilds an outcome from its columns.
    static Outcome of(final RatingBucket bucket, final @Nullable Double confidence, final @Nullable String error) {
        final var rating = bucket.rating();
        return rating.isPresent()
                ? new Verdict(rating.get(), Objects.requireNonNull(confidence, "A verdict has a confidence"))
                : new Failure(Objects.requireNonNull(error, "A failure has an error"));
    }
}
