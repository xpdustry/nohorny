// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common.geometry;

import com.xpdustry.nohorny.common.NoHornyChecks;
import java.util.Set;

public record VirtualBuilding<T>(int x, int y, int size, T data) {

    public VirtualBuilding {
        NoHornyChecks.positive(size, "size");
    }

    public record Group<T>(int x, int y, int w, int h, Set<VirtualBuilding<T>> elements) {
        public Group {
            NoHornyChecks.notEmpty(elements, "elements");
        }
    }
}
