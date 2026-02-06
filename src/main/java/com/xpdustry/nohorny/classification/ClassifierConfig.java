// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.classification;

import com.xpdustry.nohorny.config.SealedConfig;
import java.util.List;

@SealedConfig
public sealed interface ClassifierConfig {

    Thresholds thresholds();

    @SealedConfig(name = "vit")
    record ViT(List<String> labels, String nsfwLabel, String file, Thresholds thresholds) implements ClassifierConfig {}

    @SealedConfig(name = "sight-engine")
    record SightEngine(String user, String secret, Thresholds thresholds) implements ClassifierConfig {}
}
