// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.requests")
@Validated
public record RequestProperties(
        @DefaultValue("14d") @NotNull Duration imageRetention,
        @DefaultValue("90d") @NotNull Duration retention) {}
