// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.image;

import com.xpdustry.nohorny.geometry.ImmutablePoint2;
import java.util.SortedMap;

public record MindustryDisplay(int resolution, SortedMap<ImmutablePoint2, MindustryProcessor> processors)
        implements MindustryImage {}
