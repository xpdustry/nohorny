// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.classifier.vit")
@Validated
public record ViTClassifierProperties(
        @NotEmpty List<String> labels,
        @NotBlank String nsfwLabel,
        @NotBlank String engine,
        @Valid @NotNull ThresholdProperties thresholds) {
    public ViTClassifierProperties {
        labels = List.copyOf(labels);
        if (!labels.contains(nsfwLabel)) {
            throw new IllegalArgumentException(
                    "The label list " + labels + " does not contain the nsfw label " + nsfwLabel);
        }
    }
}
