// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.persistence.ClassificationRequestSummary;
import com.xpdustry.nohorny.server.MindustryClientDirectory.ClientInfo;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A classification request as shown in the admin panel, enriched with its resolved client origin. */
public record ClassificationRequestView(
        long id,
        Instant createdAt,
        long durationMillis,
        String classifier,
        @Nullable Rating rating,
        @Nullable Double confidence,
        boolean successful,
        @Nullable String error,
        @Nullable String username,
        String remoteAddress,
        String clientType,
        String clientName) {

    public static ClassificationRequestView of(final ClassificationRequestSummary summary, final ClientInfo client) {
        return new ClassificationRequestView(
                summary.getId(),
                summary.getCreatedAt(),
                summary.getDurationMillis(),
                summary.getClassifier(),
                summary.getRating(),
                summary.getConfidence(),
                summary.isSuccessful(),
                summary.getError(),
                summary.getUsername(),
                summary.getRemoteAddress(),
                client.type(),
                client.name());
    }
}
