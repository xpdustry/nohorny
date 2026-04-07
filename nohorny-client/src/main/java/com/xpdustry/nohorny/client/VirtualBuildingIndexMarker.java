// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.geometry.VirtualBuilding;
import com.xpdustry.nohorny.common.geometry.VirtualBuildingIndex;
import java.util.HashSet;
import java.util.Set;

final class VirtualBuildingIndexMarker {

    // TODO Consider using gnu trove
    private final Set<Long> flags = new HashSet<>();

    public boolean marked(final VirtualBuilding<?> building) {
        return this.flags.contains(VirtualBuildingIndex.pack(building.x(), building.y()));
    }

    public boolean marked(final int x, final int y) {
        return this.flags.contains(VirtualBuildingIndex.pack(x, y));
    }

    public void mark(final VirtualBuilding<?> building) {
        this.flags.add(VirtualBuildingIndex.pack(building.x(), building.y()));
    }

    public void unmark(final int x, final int y) {
        this.flags.remove(VirtualBuildingIndex.pack(x, y));
    }

    public void unmarkAll() {
        this.flags.clear();
    }

    // I swear to god those generics...
    public <T, I extends Iterable<VirtualBuilding<T>>> void unmarkAll(final I buildings) {
        for (final var building : buildings) {
            this.flags.remove(VirtualBuildingIndex.pack(building.x(), building.y()));
        }
    }
}
