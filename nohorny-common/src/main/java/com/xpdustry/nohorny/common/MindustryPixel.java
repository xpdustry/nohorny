// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import org.jspecify.annotations.Nullable;

public record MindustryPixel(int rgba, @Nullable MindustryAuthor author) implements MindustryImage {

    @Override
    public int resolution() {
        return 1;
    }
}
