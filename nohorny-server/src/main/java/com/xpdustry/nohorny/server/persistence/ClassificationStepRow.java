// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.Rating;
import org.jspecify.annotations.Nullable;

/// A [ClassificationStep] with the identifier of its request, to fetch the steps of many requests at once.
public record ClassificationStepRow(String requestId, ClassificationStep step) {

    /// The constructor of the JPQL projection.
    public ClassificationStepRow(
            final String requestId,
            final String classifier,
            final @Nullable Rating rating,
            final @Nullable Double confidence,
            final long durationMillis,
            final @Nullable String error) {
        this(requestId, new ClassificationStep(classifier, rating, confidence, durationMillis, error));
    }
}
