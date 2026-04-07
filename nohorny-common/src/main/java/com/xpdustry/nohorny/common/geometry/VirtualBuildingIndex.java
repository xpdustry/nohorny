// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common.geometry;

import com.xpdustry.nohorny.common.NoHornyChecks;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class VirtualBuildingIndex<T> {

    // TODO The bound checks should be in the insert method...
    public static long pack(final int x, final int y) {
        return (long) x << Integer.SIZE | y;
    }

    private final Map<Long, VirtualBuilding<T>> index = new HashMap<>();

    public @Nullable VirtualBuilding<T> select(final int x, final int y) {
        return this.index.get(pack(x, y));
    }

    public Collection<VirtualBuilding<T>> selectAllWithinSquare(final int x, final int y, final int size) {
        NoHornyChecks.positive(size, "size");
        final var result = new HashSet<VirtualBuilding<T>>();
        for (int i = x; i < x + size; i++) {
            for (int j = y; j < y + size; j++) {
                final var building = this.select(i, j);
                if (building != null) {
                    result.add(building);
                }
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    public Collection<VirtualBuilding<T>> selectAll() {
        return Set.copyOf(this.index.values());
    }

    public Collection<VirtualBuilding<T>> selectAllAdjacent(final int x, final int y) {
        final var building = this.select(x, y);
        if (building == null) {
            return Collections.emptyList();
        }

        final var result = new HashSet<VirtualBuilding<T>>();
        final int size = building.size();

        for (int i = 0; i < size; i++) {
            this.addIfPresent(result, building.x() - 1, building.y() + i);
            this.addIfPresent(result, building.x() + size, building.y() + i);
            this.addIfPresent(result, building.x() + i, building.y() - 1);
            this.addIfPresent(result, building.x() + i, building.y() + size);
        }

        result.remove(building);
        return Collections.unmodifiableCollection(result);
    }

    private void addIfPresent(final Set<VirtualBuilding<T>> set, final int x, final int y) {
        final var building = this.select(x, y);
        if (building != null) {
            set.add(building);
        }
    }

    public boolean exists(final int x, final int y) {
        return this.exists(x, y, 1);
    }

    public boolean exists(final int x, final int y, final int size) {
        NoHornyChecks.positive(size, "size");
        for (int i = x; i < x + size; i++) {
            for (int j = y; j < y + size; j++) {
                if (this.index.containsKey(pack(i, j))) {
                    return true;
                }
            }
        }
        return false;
    }

    public @Nullable VirtualBuilding<T> insert(final int x, final int y, final int size, final T data) {
        NoHornyChecks.positive(size, "size");
        if (this.exists(x, y, size)) {
            return null;
        }
        final var building = new VirtualBuilding<>(x, y, size, data);
        for (int i = x; i < x + size; i++) {
            for (int j = y; j < y + size; j++) {
                this.index.put(pack(i, j), building);
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
                this.index.remove(pack(i, j));
            }
        }
    }

    // TODO Refactor to convert into an Iterator, with starting positions for groups and max bound size
    public Collection<VirtualBuilding.Group<T>> groups() {
        final var queue = new ArrayDeque<Long>();
        final var visited = new HashSet<Long>();
        return this.index.values().stream()
                .map(building -> this.groupAt(building.x(), building.y(), visited, queue))
                .filter(Objects::nonNull)
                .toList();
    }

    public VirtualBuilding.@Nullable Group<T> groupAt(final int x, final int y) {
        return this.groupAt(x, y, new HashSet<>(), new ArrayDeque<>());
    }

    public VirtualBuilding.@Nullable Group<T> groupAt(
            final int x, final int y, final Set<Long> visited, final Queue<Long> queue) {
        final var building = this.select(x, y);
        if (building == null || !visited.add(pack(building.x(), building.y()))) {
            return null;
        }

        int minX = building.x();
        int minY = building.y();
        int maxX = building.x() + building.size();
        int maxY = building.y() + building.size();

        final var set = new HashSet<VirtualBuilding<T>>();
        queue.add(pack(building.x(), building.y()));

        while (!queue.isEmpty()) {
            final var visiting = this.index.get(queue.remove());
            if (visiting == null) {
                continue;
            }

            minX = Math.min(minX, visiting.x());
            minY = Math.min(minY, visiting.y());
            maxX = Math.max(maxX, visiting.x() + visiting.size());
            maxY = Math.max(maxY, visiting.y() + visiting.size());
            set.add(visiting);

            for (final var neighbor : this.selectAllAdjacent(visiting.x(), visiting.y())) {
                if (visited.add(pack(neighbor.x(), neighbor.y()))) {
                    queue.add(pack(neighbor.x(), neighbor.y()));
                }
            }
        }

        return new VirtualBuilding.Group<>(minX, minY, maxX - minX, maxY - minY, Collections.unmodifiableSet(set));
    }
}
