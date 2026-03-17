// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common.image;

import com.xpdustry.nohorny.common.NoHornyChecks;
import com.xpdustry.nohorny.common.struct.ImmutableByteArray;
import com.xpdustry.nohorny.common.struct.ImmutableIntArray;
import org.jspecify.annotations.Nullable;

public record MindustryCanvas(
        int resolution,
        ImmutableIntArray palette,
        ImmutableByteArray pixels,
        @Nullable MindustryAuthor author) implements MindustryImage {
    public MindustryCanvas {
        NoHornyChecks.positive(resolution, "resolution");
        final var expectedPixelCount = resolution * resolution;
        if (expectedPixelCount != pixels.length()) {
            throw new IllegalArgumentException(
                    "Resolution to pixel count mismatch, expecting " + expectedPixelCount + ", got " + pixels.length());
        }
        NoHornyChecks.within(palette.length(), 0, Byte.MAX_VALUE, "palette length");
    }
}
