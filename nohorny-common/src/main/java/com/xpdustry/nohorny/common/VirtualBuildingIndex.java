// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Internal, don't use for now...
public final class VirtualBuildingIndex<T> {

    private final TroveIntObjectMap<VirtualBuilding<T>> index = new TroveIntObjectMap<>();

    public @Nullable VirtualBuilding<T> select(final int x, final int y) {
        return this.index.get(GeometryUtils.pack(x, y));
    }

    public Collection<VirtualBuilding<T>> selectAllWithinSquare(final int x, final int y, final int size) {
        return this.selectAllWithinBounds(x, y, x + size, y + size, new TroveIntObjectMap<>());
    }

    private Collection<VirtualBuilding<T>> selectAllWithinBounds(
            final int x1, final int y1, final int x2, final int y2, final TroveIntObjectMap<Boolean> visited) {
        final var results = new ArrayList<VirtualBuilding<T>>();
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                final var building = this.select(x, y);
                if (building != null
                        && visited.put(GeometryUtils.pack(building.x(), building.y()), Boolean.TRUE) == null) {
                    results.add(building);
                }
            }
        }
        return Collections.unmodifiableCollection(results);
    }

    public Collection<VirtualBuilding<T>> selectAll() {
        final var results = new ArrayList<VirtualBuilding<T>>();
        final var visited = new HashSet<Integer>();
        this.index.forEachValue(building -> {
            if (visited.add(GeometryUtils.pack(building.x(), building.y()))) {
                results.add(building);
            }
        });
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

    public Grouper startGrouperAt(final int initialX, final int initialY, final int range, final int steps) {
        return new Grouper(
                this.index.clone(),
                initialX,
                initialY,
                steps,
                initialX - range,
                initialY - range,
                initialX + range,
                initialY + range);
    }

    // Buildings removed from the live index remain available in this frozen view.
    public final class Grouper {

        private final TroveIntObjectMap<VirtualBuilding<T>> snapshot;
        private final int maxSteps;
        private final Deque<Integer> queue = new ArrayDeque<>();
        private final TroveIntObjectMap<Boolean> visited = new TroveIntObjectMap<>();
        private final List<VirtualBuilding<T>> buildings = new ArrayList<>();

        private final int boundingBoxX1;
        private final int boundingBoxY1;
        private final int boundingBoxX2;
        private final int boundingBoxY2;

        private int minX;
        private int minY;
        private int maxX;
        private int maxY;
        private VirtualBuilding.@Nullable Group<T> result;

        private Grouper(
                final TroveIntObjectMap<VirtualBuilding<T>> snapshot,
                final int initialX,
                final int initialY,
                final int maxSteps,
                final int boundingBoxX1,
                final int boundingBoxY1,
                final int boundingBoxX2,
                final int boundingBoxY2) {
            this.snapshot = snapshot;
            this.maxSteps = maxSteps;
            this.boundingBoxX1 = boundingBoxX1;
            this.boundingBoxY1 = boundingBoxY1;
            this.boundingBoxX2 = boundingBoxX2;
            this.boundingBoxY2 = boundingBoxY2;

            final var building = this.snapshot.get(GeometryUtils.pack(initialX, initialY));
            if (building == null) {
                return;
            }

            this.visited.put(GeometryUtils.pack(building.x(), building.y()), Boolean.TRUE);
            this.queue.addLast(GeometryUtils.pack(building.x(), building.y()));

            this.minX = building.x();
            this.minY = building.y();
            this.maxX = building.x() + building.size();
            this.maxY = building.y() + building.size();
        }

        public void progress() {
            for (int i = 0; i < this.maxSteps && !this.isCompleted(); i++) {
                final var visitingPacked = this.queue.removeFirst();
                final var visiting = this.snapshot.get(visitingPacked);
                if (visiting == null) {
                    continue;
                }

                this.minX = Math.min(this.minX, visiting.x());
                this.minY = Math.min(this.minY, visiting.y());
                this.maxX = Math.max(this.maxX, visiting.x() + visiting.size());
                this.maxY = Math.max(this.maxY, visiting.y() + visiting.size());
                this.buildings.add(visiting);
                this.result = null;

                this.resolveNeighborsOnXAxis(visiting, this.visited, this.queue, visiting.y());
                this.resolveNeighborsOnXAxis(visiting, this.visited, this.queue, visiting.y() + visiting.size());

                this.resolveNeighborsOnYAxis(visiting, this.visited, this.queue, visiting.x());
                this.resolveNeighborsOnYAxis(visiting, this.visited, this.queue, visiting.x() + visiting.size());
            }
        }

        public boolean isCompleted() {
            return this.queue.isEmpty();
        }

        public boolean isVisited(final int packed) {
            return this.visited.containsKey(packed);
        }

        public VirtualBuilding.@Nullable Group<T> create() {
            if (this.buildings.isEmpty()) {
                return null;
            }
            if (this.result == null) {
                this.result = new VirtualBuilding.Group<>(
                        this.minX,
                        this.minY,
                        this.maxX - this.minX,
                        this.maxY - this.minY,
                        List.copyOf(this.buildings));
            }
            return this.result;
        }

        @SuppressWarnings("DuplicatedCode")
        private void resolveNeighborsOnXAxis(
                final VirtualBuilding<T> visiting,
                final TroveIntObjectMap<Boolean> visited,
                final Deque<Integer> queue,
                final int y) {
            int x = visiting.x();
            while (x < visiting.x() + visiting.size() && !this.snapshot.isEmpty()) {
                final var neighbor = this.snapshot.get(GeometryUtils.pack(x, y));
                if (neighbor == null) {
                    x++;
                    continue;
                }
                if (isInsideTheBoundingBox(neighbor) && visited.put(neighbor.packed(), Boolean.TRUE) == null) {
                    queue.add(neighbor.packed());
                }
                x = Math.max(x + 1, neighbor.x() + neighbor.size());
            }
        }

        @SuppressWarnings("DuplicatedCode")
        private void resolveNeighborsOnYAxis(
                final VirtualBuilding<T> visiting,
                final TroveIntObjectMap<Boolean> visited,
                final Deque<Integer> queue,
                final int x) {
            int y = visiting.y();
            while (y < visiting.y() + visiting.size() && !this.snapshot.isEmpty()) {
                final var neighbor = this.snapshot.get(GeometryUtils.pack(x, y));
                if (neighbor == null) {
                    y++;
                    continue;
                }
                if (isInsideTheBoundingBox(neighbor) && visited.put(neighbor.packed(), Boolean.TRUE) == null) {
                    queue.add(neighbor.packed());
                }
                y = Math.max(y + 1, neighbor.y() + neighbor.size());
            }
        }

        private boolean isInsideTheBoundingBox(final VirtualBuilding<T> building) {
            return building.x() < this.boundingBoxX2
                    && this.boundingBoxX1 < building.x() + building.size()
                    && building.y() < this.boundingBoxY2
                    && this.boundingBoxY1 < building.y() + building.size();
        }
    }
}
