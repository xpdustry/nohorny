// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.image;

public sealed interface MindustryImage permits MindustryCanvas, MindustryDisplay {

    int resolution();
}
