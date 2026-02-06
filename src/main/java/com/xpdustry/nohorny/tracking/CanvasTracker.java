// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.tracking;

import com.xpdustry.nohorny.NoHornyClient;
import com.xpdustry.nohorny.event.BuildingLifecycleEventListener;
import com.xpdustry.nohorny.event.BuildingUtils;
import com.xpdustry.nohorny.event.EventSubscriptionManager;
import com.xpdustry.nohorny.geometry.VirtualBuildingIndex;
import com.xpdustry.nohorny.image.MindustryAuthor;
import com.xpdustry.nohorny.image.MindustryCanvas;
import com.xpdustry.nohorny.struct.ImmutableByteArray;
import com.xpdustry.nohorny.struct.ImmutableIntArray;
import java.util.concurrent.ScheduledExecutorService;
import mindustry.gen.Player;
import mindustry.world.blocks.logic.CanvasBlock;
import org.jspecify.annotations.Nullable;

public final class CanvasTracker extends BaseTracker<MindustryCanvas> {

    private final EventSubscriptionManager events = new EventSubscriptionManager();
    private final CanvasTrackerConfig config;
    private final VirtualBuildingIndex<MindustryCanvas> canvases;

    public CanvasTracker(
            final ScheduledExecutorService scheduler, final NoHornyClient client, final CanvasTrackerConfig config) {
        final var canvases = new VirtualBuildingIndex<MindustryCanvas>();
        super(canvases, scheduler, client, config);
        this.config = config;
        this.canvases = canvases;
    }

    @Override
    public void onInit() {
        super.onInit();
        this.events.subscribe(CanvasBlock.CanvasBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(final CanvasBlock.CanvasBuild building, final @Nullable Player player) {
                final var x = BuildingUtils.anchorTileX(building);
                final var y = BuildingUtils.anchorTileY(building);
                final var size = building.block.size;
                final var data = CanvasTracker.this.data(building, player);

                CanvasTracker.this.serialExecute(() -> {
                    CanvasTracker.this.canvases.insert(x, y, size, data);
                    CanvasTracker.this.markForProcessing(x, y);
                });
            }

            @Override
            public void onRemoveAll() {
                CanvasTracker.this.serialExecute(() -> {
                    CanvasTracker.this.canvases.removeAll();
                    CanvasTracker.this.unmarkAllForProcessing();
                });
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                CanvasTracker.this.serialExecute(() -> {
                    for (final var element : CanvasTracker.this.canvases.removeAllWithinSquare(x, y, size)) {
                        CanvasTracker.this.unmarkForProcessing(element.x(), element.y());
                    }
                });
            }
        });
    }

    @Override
    public void onExit() {
        super.onExit();
        this.events.clear();
    }

    @Override
    protected boolean isEligible(final MindustryCanvas image) {
        return image.pixels().stream().distinct().count() >= this.config.minimumPaletteSize();
    }

    private MindustryCanvas data(final CanvasBlock.CanvasBuild building, final @Nullable Player player) {
        final var block = ((CanvasBlock) building.block);
        final var author = player == null ? null : new MindustryAuthor(player);
        final var pixels = new byte[block.canvasSize * block.canvasSize];

        for (int index = 0; index < pixels.length; index++) {
            final var bitIndex = index * block.bitsPerPixel;
            int value = 0;
            for (int offset = 0; offset < block.bitsPerPixel; offset++) {
                byte word = building.data[(bitIndex + offset) >>> 3];
                int mask = (1 << ((bitIndex + offset) & 7));
                value |= ((word & mask) == 0 ? 0 : 1) << offset;
            }
            pixels[index] = (byte) Math.min(value, block.palette.length - 1);
        }

        return new MindustryCanvas(
                block.canvasSize, ImmutableIntArray.wrap(block.palette), ImmutableByteArray.wrap(pixels), author);
    }
}
