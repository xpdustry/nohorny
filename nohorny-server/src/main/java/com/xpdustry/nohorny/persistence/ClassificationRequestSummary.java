// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.persistence;

import com.xpdustry.nohorny.common.Rating;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Projection of {@link ClassificationRequest} without the image blob. */
public interface ClassificationRequestSummary {

    long getId();

    Instant getCreatedAt();

    long getDurationMillis();

    String getClassifier();

    @Nullable Rating getRating();

    @Nullable Double getConfidence();

    boolean isSuccessful();

    @Nullable String getError();

    @Nullable String getUsername();

    String getRemoteAddress();

    String getImageMediaType();
}
