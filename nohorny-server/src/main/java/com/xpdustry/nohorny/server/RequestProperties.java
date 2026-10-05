// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// @param imageRetention how long the stored images are kept
/// @param retention how long the requests are kept, the all-time statistics are not affected
/// @param rateLimit the limits of the public request endpoints, per client address
@ConfigurationProperties("nohorny.requests")
@Validated
public record RequestProperties(
        @DefaultValue("14d") @NotNull Duration imageRetention,
        @DefaultValue("90d") @NotNull Duration retention,
        @DefaultValue @Valid RateLimit rateLimit) {

    /// @param readsPerMinute the maximum number of `GET` requests per minute
    /// @param purgesPerMinute the maximum number of purges per minute
    public record RateLimit(
            @DefaultValue("60") @Positive int readsPerMinute,
            @DefaultValue("5") @Positive int purgesPerMinute) {}
}
