// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.ratelimit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// The limits per minute of the API.
///
/// @param anonymous the classifications of an anonymous client address, `0` requires an account
/// @param mindustry the classifications of a listed Mindustry network without an account, `0` requires one
/// @param user the classifications of a new account, each account has its own limit set from the admin panel
/// @param reads the reads of the request pages, the administrators are not limited
/// @param purges the purges from the request pages, the administrators are not limited
@ConfigurationProperties("nohorny.rate-limit")
@Validated
public record RateLimitProperties(
        @DefaultValue("5") @Min(0) int anonymous,
        @DefaultValue("60") @Min(0) int mindustry,
        @DefaultValue("120") @Positive int user,
        @DefaultValue("60") @Positive int reads,
        @DefaultValue("5") @Positive int purges) {}
