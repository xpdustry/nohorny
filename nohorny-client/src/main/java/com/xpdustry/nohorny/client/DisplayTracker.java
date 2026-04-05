// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.geometry.ImmutablePoint2;
import com.xpdustry.nohorny.common.geometry.VirtualBuilding;
import com.xpdustry.nohorny.common.geometry.VirtualBuildingIndex;
import com.xpdustry.nohorny.common.image.DrawInstruction;
import com.xpdustry.nohorny.common.image.MindustryAuthor;
import com.xpdustry.nohorny.common.image.MindustryDisplay;
import com.xpdustry.nohorny.common.lifecycle.LifecycleListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.logic.LExecutor;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import org.jspecify.annotations.Nullable;

final class DisplayTracker implements LifecycleListener {

    private static final int MIN_DRAW_INSTRUCTION_COUNT = 40;
    private static final int PROCESSOR_SEARCH_RADIUS = 8;

    // TODO Maybe dynamically assembling displays+processors aint a good idea, return to chirurgical updates?
    final VirtualBuildingIndex<ProcessorWithLinks> processors = new VirtualBuildingIndex<>();
    private final VirtualBuildingIndexMarker modified = new VirtualBuildingIndexMarker();
    final VirtualBuildingIndex<Integer> resolutions = new VirtualBuildingIndex<>();
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .name("display-tracker-worker")
            .daemon()
            .uncaughtExceptionHandler(LoggingExceptionHandler.INSTANCE)
            .factory());
    private final NoHornyClient client;

    private record ProcessorWithLinks(MindustryDisplay.Processor processor, Set<ImmutablePoint2> links) {}

    public DisplayTracker(final NoHornyClient client) {
        this.client = client;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Override
    public void onInit() {
        this.scheduler.scheduleWithFixedDelay(this::collect, 2, 2, TimeUnit.SECONDS);

        MindustryUtils.onEvent(LogicDisplay.LogicDisplayBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(final LogicDisplay.LogicDisplayBuild building, final @Nullable Player player) {
                final int x = BuildingUtils.anchorTileX(building);
                final int y = BuildingUtils.anchorTileY(building);
                final int size = building.block.size;
                final int value = ((LogicDisplay) building.block).displaySize;
                DisplayTracker.this.scheduler.execute(() -> DisplayTracker.this.resolutions.upsert(x, y, size, value));
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                DisplayTracker.this.scheduler.execute(
                        () -> DisplayTracker.this.resolutions.removeAllWithinSquare(x, y, size));
            }

            @Override
            public void onRemoveAll() {
                DisplayTracker.this.scheduler.execute(DisplayTracker.this.resolutions::removeAll);
            }
        });

        MindustryUtils.onEvent(LogicBlock.LogicBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(final LogicBlock.LogicBuild building, final @Nullable Player player) {
                final var x = BuildingUtils.anchorTileX(building);
                final var y = BuildingUtils.anchorTileY(building);
                final var size = building.block.size;
                final var links = new HashSet<ImmutablePoint2>(building.links.size);
                for (final var link : building.links) {
                    links.add(new ImmutablePoint2(link.x, link.y));
                }
                final var instructions = DisplayTracker.this.instructions(building.executor);
                if (instructions == null || instructions.size() < MIN_DRAW_INSTRUCTION_COUNT) {
                    return;
                }
                final var author = player == null ? null : new MindustryAuthor(player.uuid(), player.ip());
                final var data = new MindustryDisplay.Processor(instructions, author);

                DisplayTracker.this.scheduler.execute(() -> {
                    final var processor = DisplayTracker.this.processors.upsert(
                            x, y, size, new ProcessorWithLinks(data, Collections.unmodifiableSet(links)));
                    DisplayTracker.this.modified.mark(processor);
                });
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                DisplayTracker.this.scheduler.execute(() -> {
                    final var removed = DisplayTracker.this.processors.removeAllWithinSquare(x, y, size);
                    DisplayTracker.this.modified.unmarkAll(removed);
                });
            }

            @Override
            public void onRemoveAll() {
                DisplayTracker.this.scheduler.execute(() -> {
                    DisplayTracker.this.processors.removeAll();
                    DisplayTracker.this.resolutions.removeAll();
                    DisplayTracker.this.modified.unmarkAll();
                });
            }
        });
    }

    @Override
    public void onExit() {
        this.scheduler.close();
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
        final var displays = this.assembleDisplayIndex();
        final var marked = displays.groups().stream()
                .filter(group -> group.elements().stream()
                        .anyMatch(display -> display.data().processors().keySet().stream()
                                .anyMatch(
                                        link -> this.modified.marked(link.x() + display.x(), link.y() + display.y()))))
                .toList();
        for (final var group : marked) {
            this.client.accept(group);
            for (final var display : group.elements()) {
                for (final var link : display.data().processors().keySet()) {
                    this.modified.unmark(display.x() + link.x(), display.y() + link.y());
                }
            }
        }
    }

    VirtualBuildingIndex<MindustryDisplay> assembleDisplayIndex() {
        final var displays = new VirtualBuildingIndex<MindustryDisplay>();
        for (final var building : this.resolutions.selectAll()) {
            final var processors = this.getProcessorsLinkedTo(building.x(), building.y(), building.size());
            if (processors.isEmpty()) {
                continue;
            }
            displays.insert(
                    building.x(), building.y(), building.size(), new MindustryDisplay(building.data(), processors));
        }
        return displays;
    }

    // TODO This does not make me very happy :(
    VirtualBuilding.@Nullable Group<MindustryDisplay> getGroupAt(final int x, final int y) {
        final var displays = new VirtualBuildingIndex<MindustryDisplay>();
        for (final var building : this.resolutions.selectAll()) {
            final var processors = this.getProcessorsLinkedTo(building.x(), building.y(), building.size());
            if (processors.isEmpty()) {
                continue;
            }
            displays.insert(
                    building.x(), building.y(), building.size(), new MindustryDisplay(building.data(), processors));
        }
        return displays.groupAt(x, y);
    }

    private Map<ImmutablePoint2, MindustryDisplay.Processor> getProcessorsLinkedTo(
            final int x, final int y, final int size) {
        final int minX = Math.max(0, x - PROCESSOR_SEARCH_RADIUS);
        final int minY = Math.max(0, y - PROCESSOR_SEARCH_RADIUS);
        final int maxX = x + size + PROCESSOR_SEARCH_RADIUS;
        final int maxY = y + size + PROCESSOR_SEARCH_RADIUS;
        final int radius = Math.max(maxX - minX, maxY - minY);
        final var result = new HashMap<ImmutablePoint2, MindustryDisplay.Processor>();
        for (final var processor : this.processors.selectAllWithinSquare(minX, minY, radius)) {
            for (final var link : processor.data().links()) {
                if (x <= link.x() && link.x() < x + size && y <= link.y() && link.y() < y + size) {
                    result.put(
                            new ImmutablePoint2(processor.x() - x, processor.y() - y),
                            processor.data().processor());
                    break;
                }
            }
        }
        return result;
    }
}
