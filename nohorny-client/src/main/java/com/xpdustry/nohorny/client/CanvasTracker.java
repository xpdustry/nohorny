// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.ImmutableByteArray;
import com.xpdustry.nohorny.common.ImmutableIntArray;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.world.blocks.logic.CanvasBlock;
import org.jspecify.annotations.Nullable;

final class CanvasTracker implements LifecycleListener {

    private static final int MAX_GROUP_RANGE = 50 * 3; // 50 large canvases around the anchor
    private static final int MAX_GROUP_STEPS = 50;
    private static final int MIN_CANVAS_GROUP_SIZE = 2 * 4;

    final GroupingVirtualBuildingIndex<MindustryCanvas> canvases = new GroupingVirtualBuildingIndex<>();
    private final NoHornyClient client;
    private final SequencedSet<Integer> queue = new LinkedHashSet<>();
    private final WaitForTheBuildToFinish waiter = new WaitForTheBuildToFinish();
    private GroupingVirtualBuildingIndex<MindustryCanvas>.@Nullable Grouper grouper = null;

    public CanvasTracker(final NoHornyClient client) {
        this.client = client;
    }

    @Override
    public void onInit() {
        MindustryUtils.onEvent(CanvasBlock.CanvasBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(
                    final CanvasBlock.CanvasBuild building,
                    final @Nullable MindustryAuthor author,
                    final boolean queue) {
                final var x = MindustryUtils.anchorTileX(building);
                final var y = MindustryUtils.anchorTileY(building);
                final var size = building.block.size;
                final var data = CanvasTracker.this.data(building, author);
                final var added = CanvasTracker.this.canvases.upsert(x, y, size, data);
                if (queue) {
                    CanvasTracker.this.enqueue(added.packed());
                }
            }

            @Override
            public void onRemoveAll() {
                CanvasTracker.this.canvases.removeAll();
                CanvasTracker.this.queue.clear();
                CanvasTracker.this.grouper = null;
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

        if (this.grouper != null) {
            this.continueGrouperProcessing();
            return;
        }

        while (!this.queue.isEmpty()) {
            final int point = this.queue.removeFirst();
            final var x = GeometryUtils.x(point);
            final var y = GeometryUtils.y(point);
            final var anchor = this.canvases.select(x, y);
            if (anchor == null || !this.isEligible(anchor)) {
                continue;
            }
            this.waiter.estimateWaitTimeFor(block -> block instanceof CanvasBlock);
            this.grouper = this.canvases.startGrouperAt(x, y, MAX_GROUP_RANGE, MAX_GROUP_STEPS);
            this.continueGrouperProcessing();
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

    private void continueGrouperProcessing() {
        Objects.requireNonNull(this.grouper);
        if (this.waiter.isNotDone()) {
            this.waiter.countdown();
            return;
        }
        this.grouper.progress();
        this.queue.removeIf(this.grouper::isVisited);
        if (this.grouper.isCompleted()) {
            final var group = this.grouper.create();
            if (group == null || group.w() < MIN_CANVAS_GROUP_SIZE || group.h() < MIN_CANVAS_GROUP_SIZE) {
                this.grouper = null;
                return;
            }
            if (this.client.tryAccept(group)) {
                this.grouper = null;
            }
        }
    }

    private void enqueue(final int packed) {
        if (this.grouper != null && this.grouper.isVisited(packed)) {
            return;
        }
        this.queue.addLast(packed);
    }
}
