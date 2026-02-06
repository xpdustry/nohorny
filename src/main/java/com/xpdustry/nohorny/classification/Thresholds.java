// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.classification;

public record Thresholds(double nsfw, double warn) {
    public Thresholds {
        if (nsfw <= warn) {
            throw new IllegalArgumentException(
                    "nsfw must be greater than warn, got " + nsfw + " less or equal than " + warn);
        }
    }

    public Classification apply(final double confidence) {
        if (confidence >= nsfw) {
            return Classification.NSFW;
        }
        if (confidence >= warn) {
            return Classification.WARN;
        }
        return Classification.SAFE;
    }
}
