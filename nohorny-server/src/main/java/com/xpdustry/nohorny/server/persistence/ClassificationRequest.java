// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.ArtKind;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Stored in `request`, its steps in `request_step`, see [ClassificationRequestRepository].
///
/// @param classifier the classifier of the final verdict, or the one that failed
/// @param authenticated whether it was sent with an account, rather than given a requester by a mapping
/// @param imageHash the [ImageStore#hash] of the image, given while the image state is [ImageState#STORED] and kept
///     once it expired or was deleted
public record ClassificationRequest(
        String id,
        Instant createdAt,
        long durationMillis,
        String classifier,
        Outcome outcome,
        @Nullable String version,
        @Nullable ArtKind artKind,
        Requester requester,
        boolean authenticated,
        String remoteAddress,
        ImageState imageState,
        @Nullable String imageHash,
        List<ClassificationStep> steps) {

    public ClassificationRequest {
        if (imageState == ImageState.STORED && imageHash == null) {
            throw new IllegalArgumentException("The image hash must be given while the image is stored");
        }
        steps = List.copyOf(steps);
    }

    ClassificationRequest withSteps(final List<ClassificationStep> steps) {
        return new ClassificationRequest(
                this.id,
                this.createdAt,
                this.durationMillis,
                this.classifier,
                this.outcome,
                this.version,
                this.artKind,
                this.requester,
                this.authenticated,
                this.remoteAddress,
                this.imageState,
                this.imageHash,
                steps);
    }
}
