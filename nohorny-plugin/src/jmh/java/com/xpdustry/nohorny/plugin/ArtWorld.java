// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.plugin;

import arc.struct.Seq;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tiles;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.logic.CanvasBlock;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.power.LightBlock;

// A worst case world, entirely covered by pixel art, split in 4 horizontal bands:
// - canvases
// - large displays, each drawn by 6 processors with 100 draw instructions
// - sorters
// - illuminators
final class ArtWorld {

    private static final Team TEAM = Team.sharded;
    private static final int DRAW_INSTRUCTIONS = 100;

    final List<CanvasBlock.CanvasBuild> canvases = new ArrayList<>();
    final List<LogicBlock.LogicBuild> displayProcessors = new ArrayList<>();
    final List<Sorter.SorterBuild> sorters = new ArrayList<>();
    final List<LightBlock.LightBuild> illuminators = new ArrayList<>();
    private final List<byte[]> displayProcessorConfigs = new ArrayList<>();
    private final RandomGenerator random;

    private ArtWorld(final long seed) {
        this.random = RandomGeneratorFactory.of("Xoroshiro128PlusPlus").create(seed);
    }

    static ArtWorld generate(final int size, final long seed) {
        final var world = new ArtWorld(seed);
        final var band = size / 4;
        Groups.clear();
        Vars.world.loadGenerator(size, size, tiles -> {
            tiles.fill();
            world.placeCanvases(tiles, 0, band);
            world.placeDisplays(tiles, band, band * 2);
            world.placePixels(tiles, Blocks.sorter, band * 2, band * 3);
            world.placePixels(tiles, Blocks.illuminator, band * 3, size);
        });
        world.configure();
        return world;
    }

    int buildings() {
        return this.canvases.size()
                + this.displayProcessors.size() / 6 * 7
                + this.sorters.size()
                + this.illuminators.size();
    }

    // A player changing a random piece of art
    void change() {
        switch (this.random.nextInt(4)) {
            case 0 -> {
                final var canvas = this.pick(this.canvases);
                final var data = new byte[canvas.data.length];
                this.random.nextBytes(data);
                canvas.configure(data);
            }
            case 1 -> this.pick(this.sorters).configure(this.item());
            case 2 -> this.pick(this.illuminators).configure(this.color());
            default -> {
                final var index = this.random.nextInt(this.displayProcessors.size());
                this.displayProcessors.get(index).configure(this.displayProcessorConfigs.get(index));
            }
        }
    }

    private void placeCanvases(final Tiles tiles, final int y1, final int y2) {
        final var size = Blocks.canvas.size;
        for (int x = 0; x + size <= tiles.width; x += size) {
            for (int y = y1; y + size <= y2; y += size) {
                this.canvases.add((CanvasBlock.CanvasBuild) place(tiles, Blocks.canvas, x, y));
            }
        }
    }

    private void placeDisplays(final Tiles tiles, final int y1, final int y2) {
        final var size = Blocks.largeLogicDisplay.size;
        for (int x = 0; x + size + 1 <= tiles.width; x += size + 1) {
            for (int y = y1; y + size <= y2; y += size) {
                place(tiles, Blocks.largeLogicDisplay, x, y);
                for (int i = 0; i < size; i++) {
                    this.displayProcessors.add(
                            (LogicBlock.LogicBuild) place(tiles, Blocks.microProcessor, x + size, y + i));
                }
            }
        }
    }

    private void placePixels(final Tiles tiles, final Block block, final int y1, final int y2) {
        for (int x = 0; x < tiles.width; x++) {
            for (int y = y1; y < y2; y++) {
                if (place(tiles, block, x, y) instanceof Sorter.SorterBuild sorter) {
                    this.sorters.add(sorter);
                } else {
                    this.illuminators.add((LightBlock.LightBuild) tiles.getn(x, y).build);
                }
            }
        }
    }

    private void configure() {
        for (final var canvas : this.canvases) {
            this.random.nextBytes(canvas.data);
        }
        for (final var sorter : this.sorters) {
            sorter.sortItem = this.item();
        }
        for (final var illuminator : this.illuminators) {
            illuminator.color = this.color();
        }

        final var drawing = this.drawing();
        final var displaySize = Blocks.largeLogicDisplay.size;
        final var displayCenter = -Blocks.largeLogicDisplay.sizeOffset;
        for (int i = 0; i < this.displayProcessors.size(); i++) {
            // The processors are stacked on the right side of their display
            final var links = Seq.with(new LogicBlock.LogicLink(
                    displayCenter - displaySize, displayCenter - i % displaySize, "display1", true));
            final var config = LogicBlock.compress(drawing, links);
            this.displayProcessors.get(i).readCompressed(config, true);
            this.displayProcessorConfigs.add(config);
        }
    }

    private String drawing() {
        final var code = new StringBuilder();
        for (int i = 0; i < DRAW_INSTRUCTIONS / 2; i++) {
            code.append("draw color ")
                    .append(this.random.nextInt(256))
                    .append(' ')
                    .append(this.random.nextInt(256))
                    .append(' ')
                    .append(this.random.nextInt(256))
                    .append(" 255\n");
            code.append("draw rect ")
                    .append(this.random.nextInt(176))
                    .append(' ')
                    .append(this.random.nextInt(176))
                    .append(" 8 8\n");
        }
        return code.append("drawflush display1\n").toString();
    }

    private <T> T pick(final List<T> list) {
        return list.get(this.random.nextInt(list.size()));
    }

    private Item item() {
        return Vars.content.items().get(this.random.nextInt(Vars.content.items().size));
    }

    private int color() {
        return this.random.nextInt() | 0xFF;
    }

    private static Building place(final Tiles tiles, final Block block, final int x, final int y) {
        final var tile = tiles.getn(x - block.sizeOffset, y - block.sizeOffset);
        tile.setBlock(block, TEAM, 0);
        return tile.build;
    }
}
