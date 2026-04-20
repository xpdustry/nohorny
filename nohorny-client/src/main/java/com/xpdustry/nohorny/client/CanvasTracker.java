// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.struct.IntSet;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.ImmutableByteArray;
import com.xpdustry.nohorny.common.ImmutableIntArray;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.world.blocks.logic.CanvasBlock;
import org.jspecify.annotations.Nullable;

final class CanvasTracker implements LifecycleListener {

    private static final int MAX_GROUP_RANGE = 6 * 2; // 6 regular canvases around the anchor

    final GroupingVirtualBuildingIndex<MindustryCanvas> canvases = new GroupingVirtualBuildingIndex<>();
    private final NoHornyClient client;
    private final SequencedSet<Integer> queue = new LinkedHashSet<>();

    public CanvasTracker(final NoHornyClient client) {
        this.client = client;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Override
    public void onInit() {
        MindustryUtils.onEvent(CanvasBlock.CanvasBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(final CanvasBlock.CanvasBuild building, final @Nullable MindustryAuthor author) {
                final var x = BuildingUtils.anchorTileX(building);
                final var y = BuildingUtils.anchorTileY(building);
                final var size = building.block.size;
                final var data = CanvasTracker.this.data(building, author);
                final var added = CanvasTracker.this.canvases.upsert(x, y, size, data);
                CanvasTracker.this.queue.addLast(added.packed());
            }

            @Override
            public void onRemoveAll() {
                CanvasTracker.this.canvases.removeAll();
                CanvasTracker.this.queue.clear();
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                for (final var removed : CanvasTracker.this.canvases.removeAllWithinSquare(x, y, size)) {
                    CanvasTracker.this.queue.remove(removed.packed());
                }
            }
        });

        MindustryUtils.onEvent(EventType.Trigger.update, _ -> this.collect());
    }

    private MindustryCanvas data(final CanvasBlock.CanvasBuild building, final @Nullable MindustryAuthor author) {
        final var block = ((CanvasBlock) building.block);
        final var pixels = new byte[block.canvasSize * block.canvasSize];

        for (int index = 0; index < pixels.length; index++) {
            final int bitIndex = index * block.bitsPerPixel;
            int value = 0;
            for (int offset = 0; offset < block.bitsPerPixel; offset++) {
                final byte word = building.data[(bitIndex + offset) >>> 3]; // Divide by 8
                final int mask = (1 << ((bitIndex + offset) & 7)); // Modulo 8
                value |= ((word & mask) == 0 ? 0 : 1) << offset;
            }
            pixels[index] = (byte) Math.min(value, block.palette.length - 1);
        }

        return new MindustryCanvas(
                block.canvasSize, ImmutableIntArray.wrap(block.palette), ImmutableByteArray.wrap(pixels), author);
    }

    private void collect() {
        if (!Vars.state.isGame()) {
            return;
        }
        final var visited = new IntSet();
        while (!this.queue.isEmpty()) {
            final int point = this.queue.removeFirst();
            final var x = GeometryUtils.x(point);
            final var y = GeometryUtils.y(point);
            final var group = this.canvases.groupAt(x, y, MAX_GROUP_RANGE, visited);
            if (group == null) {
                continue;
            }
            if (group.elements().stream().noneMatch(this::isEligible)) {
                continue;
            }
            this.client.accept(group);
            for (final var building : group.elements()) {
                this.queue.remove(building.packed());
            }
            break;
        }
    }

    private boolean isEligible(final VirtualBuilding<MindustryCanvas> building) {
        final var pixels = building.data().pixels();
        var isSolidColor = true;
        for (int i = 0; i < pixels.length(); i++) {
            isSolidColor = i == 0 || pixels.get(i) == pixels.get(i - 1);
            if (!isSolidColor) {
                break;
            }
        }
        return !isSolidColor;
    }
}
