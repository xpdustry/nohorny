// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.xpdustry.nohorny.common.DrawInstruction;
import com.xpdustry.nohorny.common.ImmutableByteArray;
import com.xpdustry.nohorny.common.ImmutableIntArray;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.MindustryDisplay;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MindustryImageRendererFuzzTest {

    @FuzzTest(maxDuration = "30s")
    void renders_any_group(final FuzzedDataProvider data) {
        final var buildings = new ArrayList<VirtualBuilding<MindustryImage>>();
        final var count = data.consumeInt(1, 8);
        for (int i = 0; i < count; i++) {
            final var x = data.consumeInt(-64, 64);
            final var y = data.consumeInt(-64, 64);
            final var size = data.consumeInt(1, 6);
            final var image = data.consumeBoolean() ? display(data) : canvas(data);
            buildings.add(new VirtualBuilding<>(x, y, size, image));
        }

        final var minX = buildings.stream().mapToInt(VirtualBuilding::x).min().orElseThrow();
        final var minY = buildings.stream().mapToInt(VirtualBuilding::y).min().orElseThrow();
        final var maxX =
                buildings.stream().mapToInt(b -> b.x() + b.size()).max().orElseThrow();
        final var maxY =
                buildings.stream().mapToInt(b -> b.y() + b.size()).max().orElseThrow();

        final var image = MindustryImageRenderer.render(
                new VirtualBuilding.Group<>(minX, minY, maxX - minX, maxY - minY, buildings));
        assertTrue(image.getWidth() <= 2048 && image.getHeight() <= 2048);
    }

    private static MindustryDisplay display(final FuzzedDataProvider data) {
        final var resolution = data.consumeInt(1, 256);
        final var tiled = data.consumeBoolean() ? new MindustryDisplay.Tiled(data.consumeInt(0, resolution)) : null;
        final var processors = new HashMap<Integer, MindustryDisplay.Processor>();
        final var processorCount = data.consumeInt(0, 4);
        for (int i = 0; i < processorCount; i++) {
            final var instructions = new ArrayList<DrawInstruction>();
            final var instructionCount = data.consumeInt(0, 64);
            for (int j = 0; j < instructionCount; j++) {
                instructions.add(
                        switch (data.consumeInt(0, 2)) {
                            case 0 ->
                                new DrawInstruction.SetColor(
                                        data.consumeInt(), data.consumeInt(), data.consumeInt(), data.consumeInt());
                            case 1 ->
                                new DrawInstruction.DrawRect(
                                        data.consumeInt(), data.consumeInt(), data.consumeInt(), data.consumeInt());
                            default ->
                                new DrawInstruction.DrawTrig(
                                        data.consumeInt(),
                                        data.consumeInt(),
                                        data.consumeInt(),
                                        data.consumeInt(),
                                        data.consumeInt(),
                                        data.consumeInt());
                        });
            }
            processors.put(i, new MindustryDisplay.Processor(instructions, null));
        }
        return new MindustryDisplay(resolution, processors, tiled);
    }

    private static MindustryCanvas canvas(final FuzzedDataProvider data) {
        final var resolution = data.consumeInt(1, 32);
        var palette = data.consumeInts(data.consumeInt(1, Byte.MAX_VALUE));
        if (palette.length == 0) {
            palette = new int[] {0};
        }
        final var pixels = new byte[resolution * resolution];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) data.consumeInt(0, palette.length - 1);
        }
        return new MindustryCanvas(resolution, ImmutableIntArray.wrap(palette), ImmutableByteArray.wrap(pixels), null);
    }
}
