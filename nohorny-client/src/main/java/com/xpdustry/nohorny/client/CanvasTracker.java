// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.ImmutableByteArray;
import com.xpdustry.nohorny.common.ImmutableIntArray;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.VirtualBuilding;
import mindustry.world.blocks.logic.CanvasBlock;
import org.jspecify.annotations.Nullable;

final class CanvasTracker extends BuildingImageTracker<CanvasBlock.CanvasBuild, MindustryCanvas> {

    private static final int MAX_GROUP_RANGE = 50 * 3; // 50 large canvases around the anchor
    private static final int MAX_GROUP_STEPS = 50;
    private static final int MIN_CANVAS_GROUP_SIZE = 2 * 4;

    public CanvasTracker(final NoHornyClient client) {
        super(
                client,
                CanvasBlock.CanvasBuild.class,
                CanvasBlock.class,
                MAX_GROUP_RANGE,
                MAX_GROUP_STEPS,
                MIN_CANVAS_GROUP_SIZE);
    }

    @Override
    protected MindustryCanvas data(final CanvasBlock.CanvasBuild building, final @Nullable MindustryAuthor author) {
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

    @Override
    protected boolean isEligible(final VirtualBuilding<MindustryCanvas> anchor) {
        final var pixels = anchor.data().pixels();
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
