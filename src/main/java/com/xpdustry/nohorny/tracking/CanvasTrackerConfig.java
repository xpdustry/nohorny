// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.tracking;

import java.time.Duration;
import org.github.gestalt.config.annotations.Config;

public record CanvasTrackerConfig(
        @Config(defaultVal = "9") int minimumGroupSize,
        @Config(defaultVal = "0.3") float processingThreshold,
        @Config(defaultVal = "5s") Duration processingDelay,
        @Config(defaultVal = "3") int minimumPaletteSize)
        implements BaseTrackerConfig {}
