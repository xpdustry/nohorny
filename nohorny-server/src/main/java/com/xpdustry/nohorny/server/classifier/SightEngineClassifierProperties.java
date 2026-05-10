// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.classifier.sight-engine")
@Validated
public record SightEngineClassifierProperties(
        @NotBlank String user,
        @NotBlank String secret,
        @Valid @NotNull ThresholdProperties thresholds) {}
