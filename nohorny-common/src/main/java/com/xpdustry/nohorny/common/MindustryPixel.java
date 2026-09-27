// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import org.jspecify.annotations.Nullable;

// A building rendered as a single solid color, like sorters and illuminators
public record MindustryPixel(
        int rgba, // NOTE Must be in rgba
        @Nullable MindustryAuthor author)
        implements MindustryImage {

    @Override
    public int resolution() {
        return 1;
    }
}
