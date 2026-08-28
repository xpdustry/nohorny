// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import org.jspecify.annotations.Nullable;

final class VirtualBuildingIndex2Impl<T> implements VirtualBuildingIndex2<T> {

    private final Int2ObjectOpenHashMap<Chunk<T>> baseIndex = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Chunk<T>> liveIndex = new Int2ObjectOpenHashMap<>();
    private final int chunkSize;
    private final BaseViewImpl baseView = new BaseViewImpl();
    private final LiveViewImpl liveView = new LiveViewImpl();

    VirtualBuildingIndex2Impl(final int chunkSize) {
        this.chunkSize = NoHornyPreconditions.positive(chunkSize, "chunkSize");
    }

    @Override
    public BaseView<T> withBaseView() {
        return this.baseView;
    }

    @Override
    public LiveView<T> withLiveView() {
        return this.liveView;
    }

    private record Chunk<T>(ArrayList<VirtualBuilding<T>> buildings, IntOpenHashSet dirtyTiles) {

        public Chunk() {
            this(new ArrayList<>(), new IntOpenHashSet());
        }

        public Chunk<T> copy() {
            return new Chunk<>(new ArrayList<>(this.buildings), new IntOpenHashSet());
        }
    }

    private class CommonViewImpl implements CommonView<T> {

        @Override
        public @Nullable VirtualBuilding<T> select(final int x, final int y) {
            return VirtualBuildingIndex2Impl.this.select(this.useLiveView(), x, y);
        }

        @Override
        public Collection<VirtualBuilding<T>> selectAllWithinSquare(final int x, final int y, final int size) {
            return VirtualBuildingIndex2Impl.this.selectAllWithinSquare(this.useLiveView(), x, y, size);
        }

        @Override
        public Collection<VirtualBuilding<T>> selectAll() {
            return VirtualBuildingIndex2Impl.this.selectAll(this.useLiveView());
        }

        @Override
        public boolean exists(final int x, final int y) {
            return VirtualBuildingIndex2Impl.this.exists(this.useLiveView(), x, y);
        }

        @Override
        public boolean exists(final int x, final int y, final int size) {
            return VirtualBuildingIndex2Impl.this.exists(this.useLiveView(), x, y, size);
        }

        boolean useLiveView() {
            return false;
        }
    }

    private final class BaseViewImpl extends CommonViewImpl implements BaseView<T> {

        @Override
        public VirtualBuilding.@Nullable Group<T> selectGroupWithinRange(final int x, final int y, final int range) {
            return VirtualBuildingIndex2Impl.this.selectGroupWithinRange(x, y, range);
        }

        @Override
        public List<VirtualBuilding.Group<T>> selectAllDirtyGroupsWithRange(final int range) {
            return VirtualBuildingIndex2Impl.this.selectAllDirtyGroupsWithRange(range);
        }
    }

    private final class LiveViewImpl extends CommonViewImpl implements LiveView<T> {

        private boolean writeToBase = true;

        @Override
        public @Nullable VirtualBuilding<T> insert(final int x, final int y, final int size, final T data) {
            return VirtualBuildingIndex2Impl.this.insert(x, y, size, data);
        }

        @Override
        public @Nullable VirtualBuilding<T> remove(final int x, final int y) {
            return VirtualBuildingIndex2Impl.this.remove(x, y);
        }

        @Override
        public VirtualBuilding<T> upsert(final int x, final int y, final int size, final T data) {
            return VirtualBuildingIndex2Impl.this.upsert(x, y, size, data);
        }

        @Override
        public void removeAll() {
            VirtualBuildingIndex2Impl.this.removeAll();
        }

        @Override
        public Collection<VirtualBuilding<T>> removeAllWithinSquare(final int x, final int y, final int size) {
            return VirtualBuildingIndex2Impl.this.removeAllWithinSquare(x, y, size);
        }

        @Override
        public void markDirty(final int x, final int y) {
            VirtualBuildingIndex2Impl.this.markDirty(x, y);
        }

        @Override
        public boolean hasNoDirtyTiles() {
            return VirtualBuildingIndex2Impl.this.hasNoDirtyTiles();
        }

        @Override
        public void writeToBase(final boolean write) {
            VirtualBuildingIndex2Impl.this.writeToBase(write);
        }

        @Override
        boolean useLiveView() {
            return true;
        }
    }

    private @Nullable VirtualBuilding<T> insert(final int x, final int y, final int size, final T data) {
        if (this.exists(true, x, y, size)) {
            return null;
        }
        final var building = new VirtualBuilding<>(x, y, size, data);
        this.forEachChunk(
                building, chunkKey -> this.mutableChunk(chunkKey).buildings().add(building));
        return building;
    }

    private @Nullable VirtualBuilding<T> remove(final int x, final int y) {
        final var removing = this.select(true, x, y);
        if (removing != null) {
            this.remove0(removing);
        }
        return removing;
    }

    private VirtualBuilding<T> upsert(final int x, final int y, final int size, final T data) {
        var dirty = false;
        for (final var building : this.selectAllWithinSquare(true, x, y, size)) {
            dirty |= this.isDirtyInLiveView(building);
        }
        this.removeAllWithinSquare(x, y, size);
        final var building = Objects.requireNonNull(this.insert(x, y, size, data));
        if (dirty) {
            this.mutableChunk(this.chunkKeyAt(building.x(), building.y()))
                    .dirtyTiles()
                    .add(building.packed());
        }
        return building;
    }

    private void removeAll() {
        if (this.liveView.writeToBase) {
            this.baseIndex.clear();
            return;
        }
        this.liveIndex.clear();
        for (final int chunkKey : this.baseIndex.keySet()) {
            this.liveIndex.put(chunkKey, new Chunk<>());
        }
    }

    private Collection<VirtualBuilding<T>> removeAllWithinSquare(final int x, final int y, final int size) {
        final var removing = this.selectAllWithinSquare(true, x, y, size);
        for (final var building : removing) {
            this.remove0(building);
        }
        return removing;
    }

    private void markDirty(final int x, final int y) {
        final var building = this.select(true, x, y);
        if (building != null) {
            this.mutableChunk(this.chunkKeyAt(building.x(), building.y()))
                    .dirtyTiles()
                    .add(building.packed());
        }
    }

    private boolean hasNoDirtyTiles() {
        final var chunks = this.liveView.writeToBase ? this.baseIndex.values() : this.liveIndex.values();
        for (final var chunk : chunks) {
            if (!chunk.dirtyTiles().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void writeToBase(final boolean write) {
        if (write) {
            for (final var chunk : this.baseIndex.values()) {
                chunk.dirtyTiles().clear();
            }
            for (final var entry : this.liveIndex.int2ObjectEntrySet()) {
                final int chunkKey = entry.getIntKey();
                final var chunk = entry.getValue();
                if (chunk.buildings().isEmpty()) {
                    this.baseIndex.remove(chunkKey);
                } else {
                    this.baseIndex.put(chunkKey, chunk);
                }
            }
            this.liveIndex.clear();
        } else {
            this.liveIndex.clear();
        }
        this.liveView.writeToBase = write;
    }

    private void remove0(final VirtualBuilding<T> building) {
        final int anchorChunkKey = this.chunkKeyAt(building.x(), building.y());
        this.mutableChunk(anchorChunkKey).dirtyTiles().remove(building.packed());
        this.forEachChunk(building, chunkKey -> {
            final var chunk = this.mutableChunk(chunkKey);
            chunk.buildings().remove(building);
            if (this.liveView.writeToBase && chunk.buildings().isEmpty()) {
                this.baseIndex.remove(chunkKey);
            }
        });
    }

    private Chunk<T> mutableChunk(final int chunkKey) {
        if (this.liveView.writeToBase) {
            final var existing = this.baseIndex.get(chunkKey);
            if (existing != null) {
                return existing;
            }
            final var created = new Chunk<T>();
            this.baseIndex.put(chunkKey, created);
            return created;
        }

        final var existing = this.liveIndex.get(chunkKey);
        if (existing != null) {
            return existing;
        }
        final var base = this.baseIndex.get(chunkKey);
        final var created = base == null ? new Chunk<T>() : base.copy();
        this.liveIndex.put(chunkKey, created);
        return created;
    }

    private boolean isDirtyInLiveView(final VirtualBuilding<T> building) {
        final int chunkKey = this.chunkKeyAt(building.x(), building.y());
        final var chunk = this.liveView.writeToBase ? this.baseIndex.get(chunkKey) : this.liveIndex.get(chunkKey);
        return chunk != null && chunk.dirtyTiles().contains(building.packed());
    }

    private void forEachChunk(final VirtualBuilding<T> building, final IntConsumer consumer) {
        final int x2 = building.x() + building.size() - 1;
        final int y2 = building.y() + building.size() - 1;
        for (int chunkX = Math.floorDiv(building.x(), this.chunkSize);
                chunkX <= Math.floorDiv(x2, this.chunkSize);
                chunkX++) {
            for (int chunkY = Math.floorDiv(building.y(), this.chunkSize);
                    chunkY <= Math.floorDiv(y2, this.chunkSize);
                    chunkY++) {
                consumer.accept(GeometryUtils.pack(chunkX, chunkY));
            }
        }
    }

    private @Nullable Chunk<T> selectChunk(final boolean useLiveView, final int chunkKey) {
        if (useLiveView && !this.liveView.writeToBase) {
            final var liveChunk = this.liveIndex.get(chunkKey);
            if (liveChunk != null) {
                return liveChunk;
            }
        }
        return this.baseIndex.get(chunkKey);
    }

    private @Nullable VirtualBuilding<T> select(final boolean useLiveView, final int x, final int y) {
        final var chunk = this.selectChunk(useLiveView, this.chunkKeyAt(x, y));
        if (chunk == null) {
            return null;
        }
        for (final var building : chunk.buildings()) {
            if (contains(building, x, y)) {
                return building;
            }
        }
        return null;
    }

    private Collection<VirtualBuilding<T>> selectAllWithinSquare(
            final boolean useLiveView, final int x, final int y, final int size) {
        final int x2 = x + size;
        final int y2 = y + size;
        final var results = new ArrayList<VirtualBuilding<T>>();
        final var visited = new IntOpenHashSet();
        for (int chunkX = Math.floorDiv(x, this.chunkSize); chunkX <= Math.floorDiv(x2 - 1, this.chunkSize); chunkX++) {
            for (int chunkY = Math.floorDiv(y, this.chunkSize);
                    chunkY <= Math.floorDiv(y2 - 1, this.chunkSize);
                    chunkY++) {
                final var chunk = this.selectChunk(useLiveView, GeometryUtils.pack(chunkX, chunkY));
                if (chunk == null) {
                    continue;
                }
                for (final var building : chunk.buildings()) {
                    if (intersects(building, x, y, x2, y2) && visited.add(building.packed())) {
                        results.add(building);
                    }
                }
            }
        }
        return List.copyOf(results);
    }

    private Collection<VirtualBuilding<T>> selectAll(final boolean useLiveView) {
        final var results = new ArrayList<VirtualBuilding<T>>();
        final var visitedBuildings = new IntOpenHashSet();
        final var visitedChunks = new IntOpenHashSet();
        for (final int chunkKey : this.baseIndex.keySet()) {
            this.addChunkBuildings(this.selectChunk(useLiveView, chunkKey), results, visitedBuildings);
            visitedChunks.add(chunkKey);
        }
        if (useLiveView && !this.liveView.writeToBase) {
            for (final var entry : this.liveIndex.int2ObjectEntrySet()) {
                if (visitedChunks.add(entry.getIntKey())) {
                    this.addChunkBuildings(entry.getValue(), results, visitedBuildings);
                }
            }
        }
        return List.copyOf(results);
    }

    private void addChunkBuildings(
            final @Nullable Chunk<T> chunk,
            final Collection<VirtualBuilding<T>> results,
            final IntSet visitedBuildings) {
        if (chunk == null) {
            return;
        }
        for (final var building : chunk.buildings()) {
            if (visitedBuildings.add(building.packed())) {
                results.add(building);
            }
        }
    }

    private boolean exists(final boolean useLiveView, final int x, final int y) {
        return this.select(useLiveView, x, y) != null;
    }

    private boolean exists(final boolean useLiveView, final int x, final int y, final int size) {
        final int x2 = x + size;
        final int y2 = y + size;
        for (int chunkX = Math.floorDiv(x, this.chunkSize); chunkX <= Math.floorDiv(x2 - 1, this.chunkSize); chunkX++) {
            for (int chunkY = Math.floorDiv(y, this.chunkSize);
                    chunkY <= Math.floorDiv(y2 - 1, this.chunkSize);
                    chunkY++) {
                final var chunk = this.selectChunk(useLiveView, GeometryUtils.pack(chunkX, chunkY));
                if (chunk == null) {
                    continue;
                }
                for (final var building : chunk.buildings()) {
                    if (intersects(building, x, y, x2, y2)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private VirtualBuilding.@Nullable Group<T> selectGroupWithinRange(final int x, final int y, final int range) {
        final var initial = this.select(false, x, y);
        if (initial == null) {
            return null;
        }
        return this.selectGroupWithinBounds0(
                initial, new BoundingBox(x - range, y - range, x + range, y + range), new IntOpenHashSet());
    }

    private List<VirtualBuilding.Group<T>> selectAllDirtyGroupsWithRange(final int range) {
        final var groups = new ArrayList<VirtualBuilding.Group<T>>();
        final var visited = new IntOpenHashSet();
        final var dirtyTiles = new IntOpenHashSet();
        for (final var chunk : this.baseIndex.values()) {
            dirtyTiles.addAll(chunk.dirtyTiles());
        }
        for (final int tile : dirtyTiles) {
            final var building = this.select(false, GeometryUtils.x(tile), GeometryUtils.y(tile));
            if (building == null) {
                continue;
            }
            final var group = this.selectGroupWithinBounds0(
                    building,
                    new BoundingBox(
                            building.x() - range,
                            building.y() - range,
                            building.x() + building.size() + range,
                            building.y() + building.size() + range),
                    visited);
            if (group != null) {
                groups.add(group);
            }
        }
        return List.copyOf(groups);
    }

    private VirtualBuilding.@Nullable Group<T> selectGroupWithinBounds0(
            final VirtualBuilding<T> initial, final BoundingBox bounds, final IntSet visited) {
        if (!visited.add(initial.packed())) {
            return null;
        }

        final var queue = new IntArrayFIFOQueue();
        queue.enqueue(initial.packed());
        final var buildings = new ArrayList<VirtualBuilding<T>>();
        int minX = initial.x();
        int minY = initial.y();
        int maxX = initial.x() + initial.size();
        int maxY = initial.y() + initial.size();

        while (!queue.isEmpty()) {
            final int packed = queue.dequeueInt();
            final var current = this.select(false, GeometryUtils.x(packed), GeometryUtils.y(packed));
            if (current == null) {
                continue;
            }
            minX = Math.min(minX, current.x());
            minY = Math.min(minY, current.y());
            maxX = Math.max(maxX, current.x() + current.size());
            maxY = Math.max(maxY, current.y() + current.size());
            buildings.add(current);

            this.resolveNeighborsOnXAxis(bounds, visited, current, queue, current.y() - 1);
            this.resolveNeighborsOnXAxis(bounds, visited, current, queue, current.y() + current.size());
            this.resolveNeighborsOnYAxis(bounds, visited, current, queue, current.x() - 1);
            this.resolveNeighborsOnYAxis(bounds, visited, current, queue, current.x() + current.size());
        }

        return new VirtualBuilding.Group<>(minX, minY, maxX - minX, maxY - minY, buildings);
    }

    @SuppressWarnings("DuplicatedCode")
    private void resolveNeighborsOnXAxis(
            final BoundingBox bounds,
            final IntSet visited,
            final VirtualBuilding<T> visiting,
            final IntArrayFIFOQueue queue,
            final int y) {
        int x = visiting.x();
        while (x < visiting.x() + visiting.size()) {
            final var neighbor = this.select(false, x, y);
            if (neighbor == null) {
                x++;
                continue;
            }
            if (isInsideTheBoundingBox(bounds, neighbor) && visited.add(neighbor.packed())) {
                queue.enqueue(neighbor.packed());
            }
            x = Math.max(x + 1, neighbor.x() + neighbor.size());
        }
    }

    @SuppressWarnings("DuplicatedCode")
    private void resolveNeighborsOnYAxis(
            final BoundingBox bounds,
            final IntSet visited,
            final VirtualBuilding<T> visiting,
            final IntArrayFIFOQueue queue,
            final int x) {
        int y = visiting.y();
        while (y < visiting.y() + visiting.size()) {
            final var neighbor = this.select(false, x, y);
            if (neighbor == null) {
                y++;
                continue;
            }
            if (isInsideTheBoundingBox(bounds, neighbor) && visited.add(neighbor.packed())) {
                queue.enqueue(neighbor.packed());
            }
            y = Math.max(y + 1, neighbor.y() + neighbor.size());
        }
    }

    private int chunkKeyAt(final int x, final int y) {
        return GeometryUtils.pack(Math.floorDiv(x, this.chunkSize), Math.floorDiv(y, this.chunkSize));
    }

    private static boolean contains(final VirtualBuilding<?> building, final int x, final int y) {
        return building.x() <= x
                && x < building.x() + building.size()
                && building.y() <= y
                && y < building.y() + building.size();
    }

    private static boolean intersects(
            final VirtualBuilding<?> building, final int x1, final int y1, final int x2, final int y2) {
        return building.x() < x2
                && x1 < building.x() + building.size()
                && building.y() < y2
                && y1 < building.y() + building.size();
    }

    private static boolean isInsideTheBoundingBox(final BoundingBox bounds, final VirtualBuilding<?> building) {
        return intersects(building, bounds.x1, bounds.y1, bounds.x2, bounds.y2);
    }

    private record BoundingBox(int x1, int y1, int x2, int y2) {}
}
