// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

public record HuggingFaceViTModelSourceProperties(
        @NotBlank String repository,
        @NotBlank String revision,
        @NotBlank String file,
        @Nullable String token,
        @NotNull Path downloadDirectory) {}
