// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Core;
import com.xpdustry.nohorny.common.ImmutableByteArray;
import com.xpdustry.nohorny.common.ImmutableIntArray;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.VirtualBuilding;
import com.xpdustry.nohorny.common.VirtualBuildingIndex2;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.world.blocks.logic.CanvasBlock;
import org.jspecify.annotations.Nullable;

final class CanvasTracker implements LifecycleListener {

    private static final int MAX_GROUP_RANGE = 50 * 3; // 50 large canvases around the anchor
    private static final int MIN_CANVAS_GROUP_SIZE = 2 * 4;
    private static final int CHUNK_SIZE = 6;

    private final VirtualBuildingIndex2<MindustryCanvas> canvasIndex = VirtualBuildingIndex2.create(CHUNK_SIZE);
    final VirtualBuildingIndex2.BaseView<MindustryCanvas> baseCanvases = this.canvasIndex.withBaseView();
    final VirtualBuildingIndex2.LiveView<MindustryCanvas> canvases = this.canvasIndex.withLiveView();
    private final NoHornyClient client;
    private TrackerCollectionState state = TrackerCollectionState.IDLE;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
    private final WaitForTheBuildToFinish waiter = new WaitForTheBuildToFinish();

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
                    CanvasTracker.this.canvases.markDirty(added.x(), added.y());
                }
            }

            @Override
            public void onRemoveAll() {
                CanvasTracker.this.canvases.removeAll();
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                CanvasTracker.this.canvases.removeAllWithinSquare(x, y, size);
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
            if (this.state == TrackerCollectionState.WAITING) {
                this.state = TrackerCollectionState.IDLE;
            }
            return;
        }

        if (this.state == TrackerCollectionState.PROCESSING || this.canvases.hasNoDirtyTiles()) {
            return;
        }

        if (this.waiter.isNotDone()) {
            this.waiter.countdown();
            return;
        }

        switch (this.state) {
            case IDLE -> {
                this.waiter.estimateWaitTimeFor(block -> block instanceof CanvasBlock);
                this.state = TrackerCollectionState.WAITING;
            }
            case WAITING -> {
                this.canvases.writeToBase(false);
                this.state = TrackerCollectionState.PROCESSING;
                this.executor.execute(() -> {
                    try {
                        for (final var group : this.baseCanvases.selectAllDirtyGroupsWithRange(MAX_GROUP_RANGE)) {
                            if (group.elements().size() >= MIN_CANVAS_GROUP_SIZE
                                    && group.elements().stream().anyMatch(this::isEligible)) {
                                this.client.offer(group);
                            }
                        }
                    } finally {
                        Core.app.post(() -> {
                            this.canvases.writeToBase(true);
                            this.state = TrackerCollectionState.IDLE;
                        });
                    }
                });
            }
            case PROCESSING -> {}
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
