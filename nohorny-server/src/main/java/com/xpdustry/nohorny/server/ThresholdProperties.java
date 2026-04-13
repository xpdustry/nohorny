// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.Rating;
import jakarta.validation.constraints.Positive;

public record ThresholdProperties(
        @Positive double warn, @Positive double nsfw) {
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
