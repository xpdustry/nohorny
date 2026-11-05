// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import org.jspecify.annotations.Nullable;

/// @param version [ValueFilter.None] for the clients sending none, or one past [DailyStat#MAX_VERSION_LENGTH], like
///     the version statistics count them
public record RequestFilter(
        @Nullable RatingBucket bucket, @Nullable RequesterFilter requester, ValueFilter version) {}
