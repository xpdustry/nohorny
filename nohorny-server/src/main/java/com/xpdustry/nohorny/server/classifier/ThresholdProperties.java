// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import com.xpdustry.nohorny.common.Rating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

public record ThresholdProperties(
        @Positive @Max(1) double warn, @Positive @Max(1) double nsfw) {
    public ThresholdProperties {
        if (warn >= nsfw) {
            throw new IllegalArgumentException(
                    "warn threshold (" + warn + ") is greater than nsfw threshold (" + nsfw + ")");
        }
    }

    public Rating apply(final double confidence) {
        if (confidence >= this.nsfw) {
            return Rating.NSFW;
        }
        if (confidence >= this.warn) {
            return Rating.WARN;
        }
        return Rating.SAFE;
    }
}
