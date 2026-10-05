// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.DrawInstruction;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryDisplay;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import mindustry.logic.LExecutor;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import mindustry.world.blocks.logic.TileableLogicDisplay;
import org.jspecify.annotations.Nullable;

final class DisplayTracker implements LifecycleListener {

    private static final int MIN_DRAW_INSTRUCTION_COUNT = 20;
    private static final int PROCESSOR_SEARCH_RADIUS = 10;
    private static final int MAX_GROUP_RANGE = 10 * 6; // 10 large displays around the anchor

    final VirtualBuildingIndex<MindustryDisplay> displays = new VirtualBuildingIndex<>();
    final VirtualBuildingIndex<ProcessorWithLinks> processors = new VirtualBuildingIndex<>();
    private final NoHornyEventBus events;
    private final GroupCollector<MindustryDisplay> collector;

    record ProcessorWithLinks(MindustryDisplay.Processor processor, Set<Integer> links) {}

    public DisplayTracker(final NoHornyEventBus events, final GroupClassifier classifier) {
        this.events = events;
        this.collector = new GroupCollector<>(
                this.displays,
                classifier,
                block -> block instanceof LogicBlock || block instanceof LogicDisplay,
                DisplayTracker::isEligible,
                _ -> true,
                MAX_GROUP_RANGE);
    }

    @Override
    public void onInit() {
        this.events.subscribe(LogicBlock.LogicBuild.class, new NoHornyEventBus.BuildingSubscriber<>() {
            @Override
            public void onCreate(
                    final LogicBlock.LogicBuild building, final @Nullable MindustryAuthor author, final boolean queue) {
                final var x = MindustryUtils.anchorTileX(building);
                final var y = MindustryUtils.anchorTileY(building);
                final var size = building.block.size;
                final var links = new HashSet<Integer>(building.links.size);
                for (final var link : building.links) {
                    links.add(GeometryUtils.pack(link.x, link.y));
                }
                final var instructions = DisplayTracker.this.instructions(building.executor);
                if (instructions == null) {
                    return;
                }
                final var data = new MindustryDisplay.Processor(instructions, author);
                final var processor = DisplayTracker.this.processors.upsert(
                        x, y, size, new ProcessorWithLinks(data, Collections.unmodifiableSet(links)));
                DisplayTracker.this.forEachLinkUpdateDisplay(processor, LinkUpdateKind.CREATE, queue);
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                for (final var processor : DisplayTracker.this.processors.removeAllWithinSquare(x, y, size)) {
                    DisplayTracker.this.forEachLinkUpdateDisplay(processor, LinkUpdateKind.REMOVE, false);
                }
            }

            @Override
            public void onRemoveAll() {
                DisplayTracker.this.processors.removeAll();
            }
        });

        this.events.subscribe(LogicDisplay.LogicDisplayBuild.class, new NoHornyEventBus.BuildingSubscriber<>() {
            @Override
            public void onCreate(
                    final LogicDisplay.LogicDisplayBuild building,
                    final @Nullable MindustryAuthor author,
                    final boolean queue) {
                final int x = MindustryUtils.anchorTileX(building);
                final int y = MindustryUtils.anchorTileY(building);
                final int size = building.block.size;
                final int resolution = ((LogicDisplay) building.block).displaySize;
                final var processors = DisplayTracker.this
                        .processors
                        .selectAllWithinSquare(
                                x - PROCESSOR_SEARCH_RADIUS,
                                y - PROCESSOR_SEARCH_RADIUS,
                                (PROCESSOR_SEARCH_RADIUS * 2) + size)
                        .stream()
                        .filter(entry -> {
                            final var links = entry.data().links();
                            // Processors can link their whole radius to inflate link scans, so large link sets are
                            // matched by scanning the display area instead.
                            if (links.size() <= size * size) {
                                return links.stream().anyMatch(link -> {
                                    final var linkX = GeometryUtils.x(link);
                                    final var linkY = GeometryUtils.y(link);
                                    return x <= linkX && linkX < x + size && y <= linkY && linkY < y + size;
                                });
                            }
                            for (int i = x; i < x + size; i++) {
                                for (int j = y; j < y + size; j++) {
                                    if (links.contains(GeometryUtils.pack(i, j))) {
                                        return true;
                                    }
                                }
                            }
                            return false;
                        })
                        .collect(Collectors.toUnmodifiableMap(
                                processor -> GeometryUtils.pack(processor.x() - x, processor.y() - y),
                                processor -> processor.data().processor(),
                                // That should never happen, but meh...
                                (a, b) -> a.instructions().size()
                                                > b.instructions().size()
                                        ? a
                                        : b));

                final var tiled = building.block instanceof TileableLogicDisplay t
                        ? new MindustryDisplay.Tiled(t.frameSize)
                        : null;
                final var added = DisplayTracker.this.displays.upsert(
                        x, y, size, new MindustryDisplay(resolution, processors, tiled));
                if (queue) {
                    DisplayTracker.this.collector.enqueue(added.packed());
                }
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                for (final var removed : DisplayTracker.this.displays.removeAllWithinSquare(x, y, size)) {
                    DisplayTracker.this.collector.dequeue(removed.packed());
                }
            }

            @Override
            public void onRemoveAll() {
                DisplayTracker.this.displays.removeAll();
                DisplayTracker.this.collector.clear();
            }
        });
    }

    @Override
    public void onTick() {
        this.collector.tick();
    }

    // TODO
    //  Track DrawFlush targets to partition processors linked to multiple displays
    //  Common picture-to-logic tools only link one processor to one display so it's fine for now.
    private @Nullable List<DrawInstruction> instructions(final LExecutor executor) {
        if (Arrays.stream(executor.instructions).noneMatch(LExecutor.DrawFlushI.class::isInstance)) {
            return null;
        }
        final var result = new ArrayList<DrawInstruction>();
        for (final var i : executor.instructions) {
            if (!(i instanceof LExecutor.DrawI draw)) {
                continue;
            }
            final DrawInstruction instruction;
            switch (draw.type) {
                case LogicDisplay.commandColor -> {
                    final int r = wrap(draw.x.numi());
                    final int g = wrap(draw.y.numi());
                    final int b = wrap(draw.p1.numi());
                    final int a = wrap(draw.p2.numi());
                    instruction = new DrawInstruction.SetColor(r, g, b, a);
                }
                case LogicDisplay.commandColorPack -> {
                    // The rgba8888 color is stored in the lower bits of the double
                    final int rgba = (int) Double.doubleToRawLongBits(draw.x.num());
                    final int r = (rgba >>> 24) & 0xFF;
                    final int g = (rgba >>> 16) & 0xFF;
                    final int b = (rgba >>> 8) & 0xFF;
                    final int a = rgba & 0xFF;
                    instruction = new DrawInstruction.SetColor(r, g, b, a);
                }
                case LogicDisplay.commandRect -> {
                    final int x = wrap(draw.x.numi());
                    final int y = wrap(draw.y.numi());
                    final int w = wrap(draw.p1.numi());
                    final int h = wrap(draw.p2.numi());
                    instruction = new DrawInstruction.DrawRect(x, y, w, h);
                }
                case LogicDisplay.commandTriangle -> {
                    final int x1 = wrap(draw.x.numi());
                    final int y1 = wrap(draw.y.numi());
                    final int x2 = wrap(draw.p1.numi());
                    final int y2 = wrap(draw.p2.numi());
                    final int x3 = wrap(draw.p3.numi());
                    final int y3 = wrap(draw.p4.numi());
                    instruction = new DrawInstruction.DrawTrig(x1, y1, x2, y2, x3, y3);
                }
                default -> {
                    continue;
                }
            }
            result.add(instruction);
        }
        return result.isEmpty() ? null : result;
    }

    // Mirrors the packSign/unpackSign round trip of the display graphics buffer,
    // which only keeps the sign and the lower 9 bits of each argument.
    private static int wrap(final int value) {
        return Integer.signum(value) * (Math.abs(value) & 0x1FF);
    }

    private void forEachLinkUpdateDisplay(
            final VirtualBuilding<ProcessorWithLinks> processor, final LinkUpdateKind kind, final boolean queue) {
        for (final var link : processor.data().links()) {
            var display = this.displays.select(GeometryUtils.x(link), GeometryUtils.y(link));
            if (display == null) {
                continue;
            }
            final var processors = new HashMap<>(display.data().processors());
            final var point = GeometryUtils.pack(processor.x() - display.x(), processor.y() - display.y());
            switch (kind) {
                case CREATE -> processors.put(point, processor.data().processor());
                case REMOVE -> processors.remove(point);
            }
            display = this.displays.upsert(
                    display.x(),
                    display.y(),
                    display.size(),
                    new MindustryDisplay(
                            display.data().resolution(),
                            processors,
                            display.data().tiled()));
            if (queue) {
                this.collector.enqueue(display.packed());
            }
        }
    }

    private static boolean isEligible(final VirtualBuilding<MindustryDisplay> building) {
        return building.data().processors().values().stream()
                .anyMatch(processor -> processor.instructions().size() >= MIN_DRAW_INSTRUCTION_COUNT);
    }

    private enum LinkUpdateKind {
        CREATE,
        REMOVE,
    }
}
