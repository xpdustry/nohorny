// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.ratelimit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.rate-limit")
@Validated
public record RateLimitProperties(
        @DefaultValue("5") @Min(0) int anonymous,
        @DefaultValue("60") @Min(0) int mindustry,
        @DefaultValue("120") @Positive int user,
        @DefaultValue("60") @Positive int reads,
        @DefaultValue("5") @Positive int imageDeletions) {}
