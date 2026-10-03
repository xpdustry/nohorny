// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import org.jspecify.annotations.Nullable;

/// Result of an image classification.
///
/// @param classifier the classifier that produced the result
/// @param rating the rating assigned to the image
/// @param confidence the confidence for the rating, from `0.0` to `1.0`
/// @param identifier the server-generated identifier for this classification
/// @param url the web page of the request on the server, when it exposes one
public record ClassificationResponse(
        String classifier,
        Rating rating,
        double confidence,
        String identifier,
        @Nullable String url) {}
