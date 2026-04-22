// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.struct.IntMap;
import arc.struct.IntQueue;
import arc.struct.IntSet;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.ArrayList;
import java.util.List;
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
                final var linkSet = this.adjacencyUnion.get(linkPacked);
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

    public Grouper startGrouperAt(final int initialX, final int initialY, final int range, final int steps) {
        return new Grouper(initialX, initialY, range, steps);
    }

    private static boolean overlaps(
            final int x1, final int y1, final int size1, final int x2, final int y2, final int size2) {
        return (x1 < x2 + size2 && x2 < x1 + size1) && (y1 < y2 + size2 && y2 < y1 + size1);
    }

    // NOTE: We are not dealing with deleted buildings, this solution is good enough...
    public final class Grouper {

        private final int initialX;
        private final int initialY;
        private final int range;
        private final int maxSteps;
        private final int halfRange;
        private final IntQueue queue = new IntQueue();
        private final IntSet visited = new IntSet();
        private final List<VirtualBuilding<T>> buildings = new ArrayList<>();

        private int minX;
        private int minY;
        private int maxX;
        private int maxY;

        private Grouper(final int initialX, final int initialY, final int range, final int maxSteps) {
            this.initialX = initialX;
            this.initialY = initialY;
            this.range = range;
            this.halfRange = Math.floorDiv(range, 2);
            this.maxSteps = maxSteps;

            final var building = GroupingVirtualBuildingIndex.this.select(initialX, initialY);
            if (building == null) {
                return;
            }

            this.visited.add(GeometryUtils.pack(building.x(), building.y()));
            this.queue.addLast(GeometryUtils.pack(building.x(), building.y()));

            this.minX = building.x();
            this.minY = building.y();
            this.maxX = building.x() + building.size();
            this.maxY = building.y() + building.size();
        }

        public void progress() {
            for (int i = 0; i < this.maxSteps && !this.isCompleted(); i++) {
                final var visitingPacked = this.queue.removeFirst();
                final var visiting = GroupingVirtualBuildingIndex.this.index.get(visitingPacked);
                if (visiting == null) {
                    continue;
                }

                this.minX = Math.min(this.minX, visiting.x());
                this.minY = Math.min(this.minY, visiting.y());
                this.maxX = Math.max(this.maxX, visiting.x() + visiting.size());
                this.maxY = Math.max(this.maxY, visiting.y() + visiting.size());
                this.buildings.add(visiting);

                final var set = GroupingVirtualBuildingIndex.this.adjacencyUnion.get(visitingPacked);
                if (set == null) {
                    continue;
                }
                final var iterator = set.iterator();
                while (iterator.hasNext) {
                    final var neighborPacked = iterator.next();
                    final var neighbor = GroupingVirtualBuildingIndex.this.index.get(neighborPacked);
                    // That's not supposed to happen, but we never know...
                    if (neighbor == null) {
                        iterator.remove();
                        continue;
                    }
                    if (!overlaps(
                            this.initialX - this.halfRange,
                            this.initialY - this.halfRange,
                            this.range,
                            neighbor.x(),
                            neighbor.y(),
                            neighbor.size())) {
                        continue;
                    }
                    if (this.visited.add(neighborPacked)) {
                        this.queue.addLast(neighborPacked);
                    }
                }
            }
        }

        public boolean isCompleted() {
            return this.queue.isEmpty();
        }

        public boolean isVisited(final int packed) {
            return this.visited.contains(packed);
        }

        public VirtualBuilding.@Nullable Group<T> create() {
            if (this.buildings.isEmpty()) {
                return null;
            }
            return new VirtualBuilding.Group<>(
                    this.minX, this.minY, this.maxX - this.minX, this.maxY - this.minY, List.copyOf(this.buildings));
        }
    }
}
