// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import org.jspecify.annotations.Nullable;

/// @param confidence from `0.0` to `1.0`
public record ClassificationResponse(
        String classifier,
        Rating rating,
        double confidence,
        String identifier,
        @Nullable String url) {}
