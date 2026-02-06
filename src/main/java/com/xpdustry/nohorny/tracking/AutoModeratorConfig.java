// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.tracking;

import com.xpdustry.nohorny.classification.Classification;
import org.github.gestalt.config.annotations.Config;
import org.jspecify.annotations.Nullable;

public record AutoModeratorConfig(
        @Config(defaultVal = "NSFW") @Nullable Classification banOn,
        @Config(defaultVal = "WARN") @Nullable Classification deleteOn) {}
