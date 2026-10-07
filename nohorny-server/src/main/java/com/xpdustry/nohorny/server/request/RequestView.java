// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.xpdustry.nohorny.common.Rating;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// The JSON representation of a classification request.
///
/// @param restricted the administrator only fields, omitted entirely for the other callers
public record RequestView(
        String id,
        Instant createdAt,
        long durationMillis,
        boolean successful,
        @Nullable Rating rating,
        @Nullable Double confidence,
        String classifier,
        @Nullable String version,
        Requester requester,
        Image image,
        List<Step> steps,
        @JsonUnwrapped @Nullable Restricted restricted) {

    /// @param url only present when the image is stored
    public record Image(
            String state,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String url) {}

    /// @param error the details of a failure, only sent to the administrators
    public record Step(
            String classifier,
            @Nullable Rating rating,
            @Nullable Double confidence,
            long durationMillis,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable FailureDetails error) {}

    /// @param error the details of a failure
    public record Restricted(String remoteAddress, @Nullable FailureDetails error) {}

    /// The details of a failure. Kept from the public, they reveal the internals of the server.
    ///
    /// @param type the name of the exception class
    public record FailureDetails(String type, String stackTrace) {}

    /// @param type `user`, `mindustry-network` or `anonymous`
    /// @param name the network of a listed Mindustry server if it has one,
    ///     the username of a user for the administrators only
    public record Requester(String type, @Nullable String name) {}
}
