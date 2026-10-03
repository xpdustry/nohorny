// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.Rating;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// A [ClassificationRequest] without its image bytes.
///
/// @param rating the final rating, `null` if the request failed
/// @param confidence the final confidence, `null` if the request failed
/// @param classifier the classifier of the final verdict, or the one that failed
/// @param error the exception name if the request failed
/// @param version the plugin version of the caller, if sent
/// @param username the authenticated caller
/// @param imageMediaType the media type of the image, if it was stored at some point
public record ClassificationRequestSummary(
        String id,
        Instant createdAt,
        long durationMillis,
        boolean successful,
        @Nullable Rating rating,
        @Nullable Double confidence,
        String classifier,
        @Nullable String error,
        @Nullable String version,
        @Nullable String username,
        String remoteAddress,
        @Nullable String imageMediaType,
        ImageState imageState,
        List<ClassificationStep> steps) {

    /// The constructor of the JPQL projections, the steps are fetched separately.
    public ClassificationRequestSummary(
            final String id,
            final Instant createdAt,
            final long durationMillis,
            final boolean successful,
            final @Nullable Rating rating,
            final @Nullable Double confidence,
            final String classifier,
            final @Nullable String error,
            final @Nullable String version,
            final @Nullable String username,
            final String remoteAddress,
            final @Nullable String imageMediaType,
            final ImageState imageState) {
        this(
                id,
                createdAt,
                durationMillis,
                successful,
                rating,
                confidence,
                classifier,
                error,
                version,
                username,
                remoteAddress,
                imageMediaType,
                imageState,
                List.of());
    }

    public ClassificationRequestSummary withSteps(final List<ClassificationStep> steps) {
        return new ClassificationRequestSummary(
                this.id,
                this.createdAt,
                this.durationMillis,
                this.successful,
                this.rating,
                this.confidence,
                this.classifier,
                this.error,
                this.version,
                this.username,
                this.remoteAddress,
                this.imageMediaType,
                this.imageState,
                List.copyOf(steps));
    }
}
