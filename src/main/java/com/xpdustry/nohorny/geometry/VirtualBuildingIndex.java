// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.geometry;

import arc.math.geom.Point2;
import arc.struct.IntMap;
import arc.struct.IntQueue;
import arc.struct.IntSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class VirtualBuildingIndex<T> {

    private final IntMap<VirtualBuilding<T>> index = new IntMap<>();

    public @Nullable VirtualBuilding<T> select(final int x, final int y) {
        return this.index.get(Point2.pack(x, y));
    }

    public Collection<VirtualBuilding<T>> selectAllWithinCircle(final int x, final int y, final int radius) {
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be greater than 0, got " + radius);
        }
        final var result = new HashSet<VirtualBuilding<T>>();
        for (int i = x - radius; i <= x + radius; i++) {
            for (int j = y - radius; j <= y + radius; j++) {
                final int dx = i - x;
                final int dy = j - y;
                if (dx * dx + dy * dy <= radius * radius) {
                    final var element = this.select(i, j);
                    if (element != null) {
                        result.add(element);
                    }
                }
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    public Collection<VirtualBuilding<T>> selectAllWithinSquare(final int x, final int y, final int size) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than 0, got " + size);
        }
        final var result = new HashSet<VirtualBuilding<T>>();
        for (int i = x; i < x + size; i++) {
            for (int j = y; j < y + size; j++) {
                final var element = this.select(i, j);
                if (element != null) {
                    result.add(element);
                }
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    public Collection<VirtualBuilding<T>> selectAll() {
        final var result = new HashSet<VirtualBuilding<T>>();
        for (final var element : this.index.values()) {
            result.add(element);
        }
        return Collections.unmodifiableCollection(result);
    }

    public Collection<VirtualBuilding<T>> selectAllAdjacent(final int x, final int y) {
        final var initial = this.select(x, y);
        if (initial == null) {
            return Collections.emptyList();
        }

        final var result = new HashSet<VirtualBuilding<T>>();
        final int size = initial.size();

        for (int i = 0; i < size; i++) {
            this.addIfPresent(result, initial.x() - 1, initial.y() + i);
            this.addIfPresent(result, initial.x() + size, initial.y() + i);
            this.addIfPresent(result, initial.x() + i, initial.y() - 1);
            this.addIfPresent(result, initial.x() + i, initial.y() + size);
        }

        result.remove(initial);
        return Collections.unmodifiableCollection(result);
    }

    private void addIfPresent(final Set<VirtualBuilding<T>> result, final int px, final int py) {
        final var element = this.select(px, py);
        if (element != null) {
            result.add(element);
        }
    }

    public boolean exists(final int x, final int y) {
        return this.exists(x, y, 1);
    }

    public boolean exists(final int x, final int y, final int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Size must be greater than 0, got " + size);
        }
        for (int i = x; i < x + size; i++) {
            for (int j = y; j < y + size; j++) {
                if (this.index.containsKey(Point2.pack(i, j))) {
                    return true;
                }
            }
        }
        return false;
    }

    public @Nullable VirtualBuilding<T> insert(final int x, final int y, final int size, final T data) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than 0, got " + size);
        }
        if (this.exists(x, y, size)) {
            return null;
        }
        final var element = new VirtualBuilding<>(x, y, size, data);
        for (int i = x; i < x + size; i++) {
            for (int j = y; j < y + size; j++) {
                this.index.put(Point2.pack(i, j), element);
            }
        }
        return element;
    }

    public @Nullable VirtualBuilding<T> remove(final int x, final int y) {
        final var removing = this.select(x, y);
        if (removing != null) {
            for (int i = removing.x(); i < removing.x() + removing.size(); i++) {
                for (int j = removing.y(); j < removing.y() + removing.size(); j++) {
                    this.index.remove(Point2.pack(i, j));
                }
            }
        }
        return removing;
    }

    public void removeAll() {
        this.index.clear();
    }

    public Collection<VirtualBuilding<T>> removeAllWithinSquare(final int x, final int y, final int size) {
        final var removing = this.selectAllWithinSquare(x, y, size);
        for (final var element : removing) {
            for (int i = element.x(); i < element.x() + element.size(); i++) {
                for (int j = element.y(); j < element.y() + element.size(); j++) {
                    this.index.remove(Point2.pack(i, j));
                }
            }
        }
        return removing;
    }

    public Collection<VirtualBuilding.Group<T>> groups() {
        final var keys = this.index.keys();
        final var queue = new IntQueue();
        final var visited = new IntSet();
        final var groups = new ArrayList<VirtualBuilding.Group<T>>();

        while (keys.hasNext) {
            final var initial = Objects.requireNonNull(this.index.get(keys.next()));
            if (!visited.add(Point2.pack(initial.x(), initial.y()))) {
                continue;
            }

            int minX = initial.x();
            int minY = initial.y();
            int maxX = initial.x() + initial.size();
            int maxY = initial.y() + initial.size();

            final var set = new HashSet<VirtualBuilding<T>>();
            queue.addLast(Point2.pack(initial.x(), initial.y()));

            while (!queue.isEmpty()) {
                final var visiting = this.index.get(queue.removeFirst());
                if (visiting == null) {
                    continue;
                }

                minX = Math.min(minX, visiting.x());
                minY = Math.min(minY, visiting.y());
                maxX = Math.max(maxX, visiting.x() + visiting.size());
                maxY = Math.max(maxY, visiting.y() + visiting.size());
                set.add(visiting);

                for (final var neighbor : this.selectAllAdjacent(visiting.x(), visiting.y())) {
                    if (visited.add(Point2.pack(neighbor.x(), neighbor.y()))) {
                        queue.addLast(Point2.pack(neighbor.x(), neighbor.y()));
                    }
                }
            }

            groups.add(new VirtualBuilding.Group<>(
                    minX, minY, maxX - minX, maxY - minY, Collections.unmodifiableSet(set)));
        }

        return Collections.unmodifiableList(groups);
    }
}
