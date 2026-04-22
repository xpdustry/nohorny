// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.classifier")
@Validated
public record ClassifierProperties(@NotNull Type type) {

    public enum Type {
        VIT,
        SIGHTENGINE,
    }
}
