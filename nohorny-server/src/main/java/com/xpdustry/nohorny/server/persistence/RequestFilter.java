// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import org.jspecify.annotations.Nullable;

/// Restricts a list of requests, each filter applying only when set.
///
/// @param bucket only the requests of this bucket
/// @param requester only the requests of this requester
/// @param version only the requests sent by this plugin version, or when empty by the clients sending none, or one
///     past [DailyStat#MAX_VERSION_LENGTH], like the version statistics count them
public record RequestFilter(
        @Nullable RatingBucket bucket,
        @Nullable RequesterFilter requester,
        @Nullable String version) {}
