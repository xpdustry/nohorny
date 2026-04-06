// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.struct.ByteSeq;
import com.xpdustry.nohorny.common.geometry.VirtualBuilding;
import com.xpdustry.nohorny.common.geometry.VirtualBuildingIndex;
import com.xpdustry.nohorny.common.image.MindustryAuthor;
import com.xpdustry.nohorny.common.image.MindustryCanvas;
import com.xpdustry.nohorny.common.lifecycle.LifecycleListener;
import com.xpdustry.nohorny.common.struct.ImmutableByteArray;
import com.xpdustry.nohorny.common.struct.ImmutableIntArray;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.world.blocks.logic.CanvasBlock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CanvasTracker implements LifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanvasTracker.class);

    final VirtualBuildingIndex<MindustryCanvas> canvases = new VirtualBuildingIndex<>();
    private final VirtualBuildingIndexMarker modified = new VirtualBuildingIndexMarker();
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .name("canvas-tracker-worker")
            .daemon()
            .uncaughtExceptionHandler(LoggingExceptionHandler.INSTANCE)
            .factory());
    private final NoHornyClient client;

    public CanvasTracker(final NoHornyClient client) {
        this.client = client;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Override
    public void onInit() {
        this.scheduler.scheduleWithFixedDelay(this::collect, 2, 2, TimeUnit.SECONDS);

        MindustryUtils.onEvent(CanvasBlock.CanvasBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(final CanvasBlock.CanvasBuild building, final @Nullable Player player) {
                final var x = BuildingUtils.anchorTileX(building);
                final var y = BuildingUtils.anchorTileY(building);
                final var size = building.block.size;
                final var data = CanvasTracker.this.data(building, player);
                CanvasTracker.this.scheduler.execute(() -> {
                    final var upserted = CanvasTracker.this.canvases.upsert(x, y, size, data);
                    CanvasTracker.this.modified.mark(upserted);
                });
            }

            @Override
            public void onRemoveAll() {
                CanvasTracker.this.scheduler.execute(() -> {
                    CanvasTracker.this.canvases.removeAll();
                    CanvasTracker.this.modified.unmarkAll();
                });
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                CanvasTracker.this.scheduler.execute(() -> {
                    final var removed = CanvasTracker.this.canvases.removeAllWithinSquare(x, y, size);
                    CanvasTracker.this.modified.unmarkAll(removed);
                });
            }
        });
    }

    @Override
    public void onExit() {
        this.scheduler.close();
    }

    // TODO Consider using RLE, using the highest bit as a marker for either count or color value
    private MindustryCanvas data(final CanvasBlock.CanvasBuild building, final @Nullable Player player) {
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

        final var author = player == null ? null : new MindustryAuthor(player.uuid(), player.ip());
        return new MindustryCanvas(
                block.canvasSize, ImmutableIntArray.wrap(block.palette), ImmutableByteArray.wrap(pixels), author);
    }

    private void collect() {
        if (!Vars.state.isGame()) {
            return;
        }
        try {
            for (final var group : this.canvases.groups()) {
                if (group.elements().stream().noneMatch(this.modified::marked)) {
                    continue;
                }
                if (group.elements().stream().anyMatch(this::isEligible)) {
                    this.client.accept(group);
                }
                this.modified.unmarkAll(group.elements());
            }
        } catch (final Exception e) {
            LOGGER.error("An error occurred while collecting canvas groups for processing", e);
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
