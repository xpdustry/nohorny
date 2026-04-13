// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.util.Collection;

public record VirtualBuilding<T>(int x, int y, int size, T data) {

    public int packed() {
        return GeometryUtils.pack(this.x, this.y);
    }

    public VirtualBuilding {
        NoHornyChecks.positive(size, "size");
    }

    public record Group<T>(int x, int y, int w, int h, Collection<VirtualBuilding<T>> elements) {
        public Group {
            NoHornyChecks.notEmpty(elements, "elements");
        }
    }
}
