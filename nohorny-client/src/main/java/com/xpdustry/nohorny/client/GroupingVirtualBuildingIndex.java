// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.struct.IntMap;
import arc.struct.IntQueue;
import arc.struct.IntSet;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class GroupingVirtualBuildingIndex<T> extends VirtualBuildingIndex<T> {

    private final IntMap<IntSet> adjacencyUnion = new IntMap<>();

    @Override
    public @Nullable VirtualBuilding<T> insert(final int x, final int y, final int size, final T data) {
        final var building = super.insert(x, y, size, data);
        if (building != null) {
            final var buildingPacked = GeometryUtils.pack(building.x(), building.y());
            final var visited = new IntSet();
            for (int i = 0; i < building.size(); i++) {
                this.link(buildingPacked, visited, building.x() - 1, building.y() + i);
                this.link(buildingPacked, visited, building.x() + building.size(), building.y() + i);
                this.link(buildingPacked, visited, building.x() + i, building.y() - 1);
                this.link(buildingPacked, visited, building.x() + i, building.y() + building.size());
            }
        }
        return building;
    }

    private void link(final int buildingPacked, final IntSet visited, final int x, final int y) {
        final var other = this.select(x, y);
        if (other == null) {
            return;
        }
        final var otherPacked = GeometryUtils.pack(other.x(), other.y());
        if (visited.add(otherPacked)) {
            this.adjacencyUnion.get(buildingPacked, IntSet::new).add(otherPacked);
            this.adjacencyUnion.get(otherPacked, IntSet::new).add(buildingPacked);
        }
    }

    @Override
    public @Nullable VirtualBuilding<T> remove(int x, int y) {
        final var building = super.remove(x, y);
        if (building != null) {
            final var buildingPacked = GeometryUtils.pack(building.x(), building.y());
            final var buildingSet = this.adjacencyUnion.remove(buildingPacked);
            if (buildingSet == null) {
                return building;
            }
            final var iterator = buildingSet.iterator();
            while (iterator.hasNext) {
                final var linkPacked = iterator.next();
                final var linkSet = this.adjacencyUnion.get(iterator.next());
                if (linkSet == null) {
                    continue;
                }
                linkSet.remove(buildingPacked);
                if (linkSet.isEmpty()) {
                    this.adjacencyUnion.remove(linkPacked);
                }
            }
        }
        return building;
    }

    public VirtualBuilding.@Nullable Group<T> groupAt(
            final int initialX, final int initialY, final int range, final IntSet visited) {
        final var building = this.select(initialX, initialY);
        if (building == null || !visited.add(GeometryUtils.pack(building.x(), building.y()))) {
            return null;
        }

        final var queue = new IntQueue();
        final var halfRange = Math.floorDiv(range, 2);
        int minX = building.x();
        int minY = building.y();
        int maxX = building.x() + building.size();
        int maxY = building.y() + building.size();

        final var buildings = new ArrayList<VirtualBuilding<T>>();
        queue.addLast(GeometryUtils.pack(building.x(), building.y()));

        while (!queue.isEmpty()) {
            final var visitingPacked = queue.removeFirst();
            final var visiting = Objects.requireNonNull(this.index.get(visitingPacked));

            minX = Math.min(minX, visiting.x());
            minY = Math.min(minY, visiting.y());
            maxX = Math.max(maxX, visiting.x() + visiting.size());
            maxY = Math.max(maxY, visiting.y() + visiting.size());
            buildings.add(visiting);

            final var set = this.adjacencyUnion.get(visitingPacked);
            if (set == null) {
                continue;
            }
            final var iterator = set.iterator();
            while (iterator.hasNext) {
                final var neighborPacked = iterator.next();
                final var neighbor = Objects.requireNonNull(this.index.get(neighborPacked));
                if (!overlaps(
                        initialX - halfRange,
                        initialY - halfRange,
                        range,
                        neighbor.x(),
                        neighbor.y(),
                        neighbor.size())) {
                    continue;
                }
                if (visited.add(neighborPacked)) {
                    queue.addLast(neighborPacked);
                }
            }
        }

        return new VirtualBuilding.Group<>(
                minX, minY, maxX - minX, maxY - minY, Collections.unmodifiableList(buildings));
    }

    private static boolean overlaps(
            final int x1, final int y1, final int size1, final int x2, final int y2, final int size2) {
        return (x1 < x2 + size2 && x2 < x1 + size1) && (y1 < y2 + size2 && y2 < y1 + size1);
    }
}
