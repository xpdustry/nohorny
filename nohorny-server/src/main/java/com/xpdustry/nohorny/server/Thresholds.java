// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.NoHornyChecks;
import com.xpdustry.nohorny.common.classification.Rating;

public record Thresholds(double nsfw, double warn) {
    public Thresholds {
        NoHornyChecks.greaterThan(nsfw, warn, "nsfw");
    }

    public Rating apply(final double confidence) {
        if (confidence >= nsfw) {
            return Rating.NSFW;
        }
        if (confidence >= warn) {
            return Rating.WARN;
        }
        return Rating.SAFE;
    }
}
