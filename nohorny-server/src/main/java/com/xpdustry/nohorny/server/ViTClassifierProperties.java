// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.classifier.vit")
@Validated
public record ViTClassifierProperties(
        @NotBlank String repository,
        @NotBlank String revision,
        @NotBlank String file,
        @Nullable String token,
        @NotEmpty List<String> labels,
        @NotBlank String nsfwLabel,
        @NotNull Path directory,
        @Valid @NotNull ThresholdProperties thresholds,
        @NotBlank String engine) {}
