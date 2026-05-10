// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.classifier.vit.source.local")
@Validated
public record LocalViTModelSourceProperties(@NotNull Path path) {}
