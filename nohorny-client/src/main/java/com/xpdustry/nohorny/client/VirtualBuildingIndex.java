// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.struct.IntMap;
import arc.struct.IntSet;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

sealed class VirtualBuildingIndex<T> permits GroupingVirtualBuildingIndex {

    protected final IntMap<VirtualBuilding<T>> index = new IntMap<>();

    public @Nullable VirtualBuilding<T> select(final int x, final int y) {
        return this.index.get(GeometryUtils.pack(x, y));
    }

    public Collection<VirtualBuilding<T>> selectAllWithinSquare(final int x, final int y, final int size) {
        return this.selectAllWithinBounds(x, y, x + size, y + size, new IntSet());
    }

    public Collection<VirtualBuilding<T>> selectAllWithinBounds(
            final int x1, final int y1, final int x2, final int y2, final IntSet visited) {
        final var results = new ArrayList<VirtualBuilding<T>>();
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                final var building = this.select(x, y);
                if (building != null && visited.add(GeometryUtils.pack(building.x(), building.y()))) {
                    results.add(building);
                }
            }
        }
        return Collections.unmodifiableCollection(results);
    }

    public Collection<VirtualBuilding<T>> selectAll() {
        final var results = new ArrayList<VirtualBuilding<T>>();
        final var visited = new IntSet();
        for (final var entry : this.index) {
            if (visited.add(GeometryUtils.pack(entry.value.x(), entry.value.y()))) {
                results.add(entry.value);
            }
        }
        return Collections.unmodifiableCollection(results);
    }

    public boolean exists(final int x, final int y) {
        return this.exists(x, y, 1);
    }

    public boolean exists(final int x, final int y, final int size) {
        for (int i = x; i < x + size; i++) {
            for (int j = y; j < y + size; j++) {
                if (this.index.containsKey(GeometryUtils.pack(i, j))) {
                    return true;
                }
            }
        }
        return false;
    }

    public @Nullable VirtualBuilding<T> insert(final int x, final int y, final int size, final T data) {
        if (this.exists(x, y, size)) {
            return null;
        }
        final var building = new VirtualBuilding<>(x, y, size, data);
        for (int i = x; i < x + size; i++) {
            for (int j = y; j < y + size; j++) {
                this.index.put(GeometryUtils.pack(i, j), building);
            }
        }
        return building;
    }

    public @Nullable VirtualBuilding<T> remove(final int x, final int y) {
        final var removing = this.select(x, y);
        if (removing != null) {
            this.remove0(removing);
        }
        return removing;
    }

    public VirtualBuilding<T> upsert(final int x, final int y, final int size, final T data) {
        this.removeAllWithinSquare(x, y, size);
        return Objects.requireNonNull(this.insert(x, y, size, data));
    }

    public void removeAll() {
        this.index.clear();
    }

    public Collection<VirtualBuilding<T>> removeAllWithinSquare(final int x, final int y, final int size) {
        final var removing = this.selectAllWithinSquare(x, y, size);
        for (final var building : removing) {
            this.remove0(building);
        }
        return removing;
    }

    private void remove0(final VirtualBuilding<T> building) {
        for (int i = building.x(); i < building.x() + building.size(); i++) {
            for (int j = building.y(); j < building.y() + building.size(); j++) {
                this.index.remove(GeometryUtils.pack(i, j));
            }
        }
    }
}
