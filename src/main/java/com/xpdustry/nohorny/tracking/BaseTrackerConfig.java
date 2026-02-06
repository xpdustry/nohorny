// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.tracking;

import java.time.Duration;

public interface BaseTrackerConfig {

    int minimumGroupSize();

    float processingThreshold();

    Duration processingDelay();
}
