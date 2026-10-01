// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.classifier.vit")
@Validated
public record ViTClassifierProperties(
        @NotEmpty List<String> labels,
        @NotBlank String nsfwLabel,
        @DefaultValue("1") @Positive int instances,
        @Valid @NotNull ThresholdProperties thresholds) {
    public ViTClassifierProperties {
        labels = List.copyOf(labels);
        if (!labels.contains(nsfwLabel)) {
            throw new IllegalArgumentException(
                    "The label list " + labels + " does not contain the nsfw label " + nsfwLabel);
        }
    }
}
