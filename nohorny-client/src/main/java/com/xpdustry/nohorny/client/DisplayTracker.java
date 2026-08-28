// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Core;
import com.xpdustry.nohorny.common.DrawInstruction;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryDisplay;
import com.xpdustry.nohorny.common.VirtualBuilding;
import com.xpdustry.nohorny.common.VirtualBuildingIndex2;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.logic.LExecutor;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import mindustry.world.blocks.logic.TileableLogicDisplay;
import org.jspecify.annotations.Nullable;

final class DisplayTracker implements LifecycleListener {

    private static final int MIN_DRAW_INSTRUCTION_COUNT = 20;
    private static final int PROCESSOR_SEARCH_RADIUS = 8;
    private static final int MAX_GROUP_RANGE = 10 * 6; // 10 large displays around the anchor
    private static final int CHUNK_SIZE = 6;

    private final VirtualBuildingIndex2<MindustryDisplay> displayIndex = VirtualBuildingIndex2.create(CHUNK_SIZE);
    final VirtualBuildingIndex2.BaseView<MindustryDisplay> baseDisplays = this.displayIndex.withBaseView();
    final VirtualBuildingIndex2.LiveView<MindustryDisplay> displays = this.displayIndex.withLiveView();
    final VirtualBuildingIndex2.LiveView<ProcessorWithLinks> processors =
            VirtualBuildingIndex2.<ProcessorWithLinks>create(CHUNK_SIZE).withLiveView();
    private final NoHornyClient client;
    private TrackerCollectionState state = TrackerCollectionState.IDLE;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
    private final WaitForTheBuildToFinish waiter = new WaitForTheBuildToFinish();

    record ProcessorWithLinks(MindustryDisplay.Processor processor, IntSet links) {}

    public DisplayTracker(final NoHornyClient client) {
        this.client = client;
    }

    @Override
    public void onInit() {
        MindustryUtils.onEvent(LogicBlock.LogicBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(
                    final LogicBlock.LogicBuild building, final @Nullable MindustryAuthor author, final boolean queue) {
                final var x = MindustryUtils.anchorTileX(building);
                final var y = MindustryUtils.anchorTileY(building);
                final var size = building.block.size;
                final var links = new IntOpenHashSet(building.links.size);
                for (final var link : building.links) {
                    links.add(GeometryUtils.pack(link.x, link.y));
                }
                final var instructions = DisplayTracker.this.instructions(building.executor);
                if (instructions == null) {
                    return;
                }
                final var data = new MindustryDisplay.Processor(instructions, author);
                final var processor = DisplayTracker.this.processors.upsert(
                        x, y, size, new ProcessorWithLinks(data, IntSets.unmodifiable(links)));
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

        MindustryUtils.onEvent(LogicDisplay.LogicDisplayBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(
                    final LogicDisplay.LogicDisplayBuild building,
                    final @Nullable MindustryAuthor author,
                    final boolean queue) {
                // TODO Add proper support for tileable display
                if (building instanceof TileableLogicDisplay.TileableLogicDisplayBuild) {
                    return;
                }
                final int x = MindustryUtils.anchorTileX(building);
                final int y = MindustryUtils.anchorTileY(building);
                final int size = building.block.size;
                final int resolution = ((LogicDisplay) building.block).displaySize;
                final var processors = new Int2ObjectOpenHashMap<MindustryDisplay.Processor>();
                for (final var processor : DisplayTracker.this.processors.selectAllWithinSquare(
                        x - PROCESSOR_SEARCH_RADIUS,
                        y - PROCESSOR_SEARCH_RADIUS,
                        (PROCESSOR_SEARCH_RADIUS * 2) + size)) {
                    if (!isLinkedToDisplay(processor.data().links(), x, y, size)) {
                        continue;
                    }
                    final int point = GeometryUtils.pack(processor.x() - x, processor.y() - y);
                    final var candidate = processor.data().processor();
                    final var existing = processors.get(point);
                    if (existing == null
                            || candidate.instructions().size()
                                    > existing.instructions().size()) {
                        processors.put(point, candidate);
                    }
                }
                final var added =
                        DisplayTracker.this.displays.upsert(x, y, size, new MindustryDisplay(resolution, processors));
                if (queue) {
                    DisplayTracker.this.displays.markDirty(added.x(), added.y());
                }
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                DisplayTracker.this.displays.removeAllWithinSquare(x, y, size);
            }

            @Override
            public void onRemoveAll() {
                DisplayTracker.this.displays.removeAll();
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
            if (this.state == TrackerCollectionState.WAITING) {
                this.state = TrackerCollectionState.IDLE;
            }
            return;
        }

        if (this.state == TrackerCollectionState.PROCESSING || this.displays.hasNoDirtyTiles()) {
            return;
        }

        if (this.waiter.isNotDone()) {
            this.waiter.countdown();
            return;
        }

        switch (this.state) {
            case IDLE -> {
                this.waiter.estimateWaitTimeFor(block -> block instanceof LogicBlock || block instanceof LogicDisplay);
                this.state = TrackerCollectionState.WAITING;
            }
            case WAITING -> {
                this.displays.writeToBase(false);
                this.state = TrackerCollectionState.PROCESSING;
                this.executor.execute(() -> {
                    try {
                        for (final var group : this.baseDisplays.selectAllDirtyGroupsWithRange(MAX_GROUP_RANGE)) {
                            if (group.elements().stream().anyMatch(this::isEligible)) {
                                this.client.offer(group);
                            }
                        }
                    } finally {
                        Core.app.post(() -> {
                            this.displays.writeToBase(true);
                            this.state = TrackerCollectionState.IDLE;
                        });
                    }
                });
            }
            case PROCESSING -> {}
        }
    }

    private void forEachLinkUpdateDisplay(
            final VirtualBuilding<ProcessorWithLinks> processor, final LinkUpdateKind kind, final boolean queue) {
        final IntIterator links = processor.data().links().iterator();
        while (links.hasNext()) {
            final int link = links.nextInt();
            var display = this.displays.select(GeometryUtils.x(link), GeometryUtils.y(link));
            if (display == null) {
                continue;
            }
            final var processors = new Int2ObjectOpenHashMap<>(display.data().processors());
            final var point = GeometryUtils.pack(processor.x() - display.x(), processor.y() - display.y());
            switch (kind) {
                case CREATE -> processors.put(point, processor.data().processor());
                case REMOVE -> processors.remove(point);
            }
            display = this.displays.upsert(
                    display.x(),
                    display.y(),
                    display.size(),
                    new MindustryDisplay(display.data().resolution(), processors));
            if (queue) {
                this.displays.markDirty(display.x(), display.y());
            }
        }
    }

    private boolean isEligible(final VirtualBuilding<MindustryDisplay> building) {
        return building.data().processors().values().stream()
                .anyMatch(processor -> processor.instructions().size() >= MIN_DRAW_INSTRUCTION_COUNT);
    }

    private static boolean isLinkedToDisplay(final IntSet links, final int x, final int y, final int size) {
        // Processors can link their whole radius to inflate link scans,
        // so large link sets are matched by scanning the display area instead.
        if (links.size() <= size * size) {
            final var iterator = links.iterator();
            while (iterator.hasNext()) {
                final int link = iterator.nextInt();
                final int linkX = GeometryUtils.x(link);
                final int linkY = GeometryUtils.y(link);
                if (x <= linkX && linkX < x + size && y <= linkY && linkY < y + size) {
                    return true;
                }
            }
            return false;
        }

        for (int i = x; i < x + size; i++) {
            for (int j = y; j < y + size; j++) {
                if (links.contains(GeometryUtils.pack(i, j))) {
                    return true;
                }
            }
        }

        return false;
    }

    private enum LinkUpdateKind {
        CREATE,
        REMOVE,
    }
}
