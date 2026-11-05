// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.server.persistence.RequesterType;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// @param kind the [com.xpdustry.nohorny.common.ArtKind#key()] of the image, `null` for the older clients
/// @param adminOnly the administrator only fields, omitted entirely for the other callers
public record RequestView(
        String id,
        Instant createdAt,
        long durationMillis,
        boolean successful,
        @Nullable Rating rating,
        @Nullable Double confidence,
        String classifier,
        @Nullable String version,
        @Nullable String kind,
        Requester requester,
        Image image,
        List<Step> steps,
        @JsonUnwrapped @Nullable AdminOnly adminOnly) {

    public record Image(
            String state,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String url) {}

    public record Step(
            String classifier,
            @Nullable Rating rating,
            @Nullable Double confidence,
            long durationMillis,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable FailureDetails error) {}

    public record AdminOnly(String remoteAddress, @Nullable FailureDetails error) {}

    /// The details of a failure. Kept from the public, they reveal the internals of the server.
    public record FailureDetails(String type, String stackTrace) {}

    public record Requester(RequesterType type, @Nullable String name) {}
}
