// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import org.jspecify.annotations.Nullable;

public record MindustryCanvas(
        int resolution,
        ImmutableIntArray palette, // NOTE Palette elements must be in rgba
        ImmutableByteArray pixels,
        @Nullable MindustryAuthor author)
        implements MindustryImage {
    public MindustryCanvas {
        NoHornyPreconditions.positive(resolution, "resolution");
        final var expectedPixelCount = resolution * resolution;
        if (expectedPixelCount != pixels.length()) {
            throw new IllegalArgumentException(
                    "Resolution to pixel count mismatch, expecting " + expectedPixelCount + ", got " + pixels.length());
        }
        NoHornyPreconditions.within(palette.length(), 0, Byte.MAX_VALUE, "palette length");
    }
}
