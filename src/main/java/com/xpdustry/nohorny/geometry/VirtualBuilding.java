// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.geometry;

import java.util.Set;

public record VirtualBuilding<T>(int x, int y, int size, T data) implements Comparable<VirtualBuilding<?>> {
    public VirtualBuilding {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than 0, got " + size);
        }
    }

    @Override
    public int compareTo(final VirtualBuilding<?> o) {
        int result = Integer.compare(this.x, o.x);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(this.y, o.y);
        if (result != 0) {
            return result;
        }
        return Integer.compare(this.size, o.size);
    }

    public record Group<T>(int x, int y, int w, int h, Set<VirtualBuilding<T>> elements) {
        public Group {
            if (elements.isEmpty()) {
                throw new IllegalArgumentException("elements must not be empty");
            }
        }
    }
}
