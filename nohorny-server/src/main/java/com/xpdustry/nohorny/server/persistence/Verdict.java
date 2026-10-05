// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.Rating;

/// A successful classification.
public record Verdict(Rating rating, double confidence) implements Outcome {

    @Override
    public RatingBucket bucket() {
        return RatingBucket.of(this.rating);
    }
}
