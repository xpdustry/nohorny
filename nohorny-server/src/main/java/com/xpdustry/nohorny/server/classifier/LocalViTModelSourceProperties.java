// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;

public record LocalViTModelSourceProperties(@NotNull Path path) {}
