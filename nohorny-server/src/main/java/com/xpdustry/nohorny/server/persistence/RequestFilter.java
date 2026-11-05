// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import org.jspecify.annotations.Nullable;

/// @param version only the requests sent by this plugin version, or when empty by the clients sending none, or one
///     past [DailyStat#MAX_VERSION_LENGTH], like the version statistics count them
public record RequestFilter(
        @Nullable RatingBucket bucket,
        @Nullable RequesterFilter requester,
        @Nullable String version) {}
