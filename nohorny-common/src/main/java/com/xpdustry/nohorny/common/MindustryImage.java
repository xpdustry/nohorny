// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

public sealed interface MindustryImage permits MindustryCanvas, MindustryDisplay {

    int resolution();
}
