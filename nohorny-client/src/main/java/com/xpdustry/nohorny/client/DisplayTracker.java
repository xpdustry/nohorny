// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.struct.IntSet;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Collectors;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.logic.LExecutor;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import org.jspecify.annotations.Nullable;

final class DisplayTracker implements LifecycleListener {

    private static final int MIN_DRAW_INSTRUCTION_COUNT = 40;
    private static final int PROCESSOR_SEARCH_RADIUS = 8;
    private static final int MAX_GROUP_RANGE = 3 * 6; // 3 large displays around the anchor

    final GroupingVirtualBuildingIndex<MindustryDisplay> displays = new GroupingVirtualBuildingIndex<>();
    final VirtualBuildingIndex<ProcessorWithLinks> processors = new VirtualBuildingIndex<>();
    private final NoHornyClient client;
    private final SequencedSet<Integer> queue = new LinkedHashSet<>();

    private record ProcessorWithLinks(MindustryDisplay.Processor processor, Set<Integer> links) {}

    public DisplayTracker(final NoHornyClient client) {
        this.client = client;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Override
    public void onInit() {
        MindustryUtils.onEvent(LogicDisplay.LogicDisplayBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(
                    final LogicDisplay.LogicDisplayBuild building, final @Nullable MindustryAuthor author) {
                final int x = BuildingUtils.anchorTileX(building);
                final int y = BuildingUtils.anchorTileY(building);
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
                final var added =
                        DisplayTracker.this.displays.upsert(x, y, size, new MindustryDisplay(resolution, processors));
                DisplayTracker.this.queue.addLast(added.packed());
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                for (final var removed : DisplayTracker.this.displays.removeAllWithinSquare(x, y, size)) {
                    DisplayTracker.this.queue.remove(removed.packed());
                }
            }

            @Override
            public void onRemoveAll() {
                DisplayTracker.this.displays.removeAll();
                DisplayTracker.this.queue.clear();
            }
        });

        MindustryUtils.onEvent(LogicBlock.LogicBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(final LogicBlock.LogicBuild building, final @Nullable MindustryAuthor author) {
                final var x = BuildingUtils.anchorTileX(building);
                final var y = BuildingUtils.anchorTileY(building);
                final var size = building.block.size;
                final var links = new HashSet<Integer>(building.links.size);
                for (final var link : building.links) {
                    links.add(GeometryUtils.pack(link.x, link.y));
                }
                final var instructions = DisplayTracker.this.instructions(building.executor);
                if (instructions == null || instructions.size() < MIN_DRAW_INSTRUCTION_COUNT) {
                    return;
                }
                final var data = new MindustryDisplay.Processor(instructions, author);
                final var processor = DisplayTracker.this.processors.upsert(
                        x, y, size, new ProcessorWithLinks(data, Collections.unmodifiableSet(links)));
                DisplayTracker.this.forEachLinkUpdateDisplay(processor, LinkUpdateKind.CREATE);
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                for (final var processor : DisplayTracker.this.processors.removeAllWithinSquare(x, y, size)) {
                    DisplayTracker.this.forEachLinkUpdateDisplay(processor, LinkUpdateKind.REMOVE);
                }
            }

            @Override
            public void onRemoveAll() {
                DisplayTracker.this.processors.removeAll();
            }
        });

        MindustryUtils.onEvent(EventType.Trigger.update, _ -> this.collect());
    }

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
                    final int r = draw.x.numi();
                    final int g = draw.y.numi();
                    final int b = draw.p1.numi();
                    final int a = draw.p2.numi();
                    instruction = new DrawInstruction.SetColor(r, g, b, a);
                }
                case LogicDisplay.commandRect -> {
                    final int x = draw.x.numi();
                    final int y = draw.y.numi();
                    final int w = draw.p1.numi();
                    final int h = draw.p2.numi();
                    instruction = new DrawInstruction.DrawRect(x, y, w, h);
                }
                case LogicDisplay.commandTriangle -> {
                    final int x1 = draw.x.numi();
                    final int y1 = draw.y.numi();
                    final int x2 = draw.p1.numi();
                    final int y2 = draw.p2.numi();
                    final int x3 = draw.p3.numi();
                    final int y3 = draw.p4.numi();
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

    private void collect() {
        if (!Vars.state.isGame()) {
            return;
        }
        final var visited = new IntSet();
        while (!this.queue.isEmpty()) {
            final int point = this.queue.removeFirst();
            final var x = GeometryUtils.x(point);
            final var y = GeometryUtils.y(point);
            final var anchor = this.displays.select(x, y);
            if (anchor == null || !this.isEligible(anchor)) {
                continue;
            }
            final var group = this.displays.groupAt(x, y, MAX_GROUP_RANGE, visited);
            if (group == null) {
                continue;
            }
            this.client.accept(group);
            for (final var building : group.elements()) {
                this.queue.remove(building.packed());
            }
            break;
        }
    }

    private void forEachLinkUpdateDisplay(
            final VirtualBuilding<ProcessorWithLinks> processor, final LinkUpdateKind kind) {
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
                    new MindustryDisplay(display.data().resolution(), Collections.unmodifiableMap(processors)));
            this.queue.addLast(GeometryUtils.pack(display.x(), display.y()));
        }
    }

    private boolean isEligible(final VirtualBuilding<MindustryDisplay> building) {
        return !building.data().processors().isEmpty();
    }

    private enum LinkUpdateKind {
        CREATE,
        REMOVE,
    }
}
