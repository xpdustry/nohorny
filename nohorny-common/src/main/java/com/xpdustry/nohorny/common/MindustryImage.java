// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

public sealed interface MindustryImage permits MindustryCanvas, MindustryDisplay, MindustryPixel {

    int MAX_RENDER_SIZE = 2048;

    int resolution();
}
