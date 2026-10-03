// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.Rating;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.jspecify.annotations.Nullable;

/// One classifier invocation of a [ClassificationRequest].
///
/// @param rating `null` if the classifier failed
/// @param confidence `null` if the classifier failed
/// @param error the exception name if the classifier failed
@Embeddable
public record ClassificationStep(
        String classifier,
        @Enumerated(EnumType.STRING) @Nullable Rating rating,
        @Nullable Double confidence,
        long durationMillis,
        @Nullable String error) {}
