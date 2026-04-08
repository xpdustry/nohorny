// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.geometry.ImmutablePoint2;
import com.xpdustry.nohorny.common.geometry.VirtualBuilding;
import com.xpdustry.nohorny.common.geometry.VirtualBuildingIndex;
import com.xpdustry.nohorny.common.image.DrawInstruction;
import com.xpdustry.nohorny.common.image.MindustryAuthor;
import com.xpdustry.nohorny.common.image.MindustryDisplay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.logic.LExecutor;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import org.jspecify.annotations.Nullable;

final class DisplayTracker implements LifecycleListener {

    private static final int MIN_DRAW_INSTRUCTION_COUNT = 40;
    private static final int PROCESSOR_SEARCH_RADIUS = 8;

    final VirtualBuildingIndex<ProcessorWithLinks> processors = new VirtualBuildingIndex<>();
    private final VirtualBuildingIndexMarker modified = new VirtualBuildingIndexMarker();
    final VirtualBuildingIndex<MindustryDisplay> displays = new VirtualBuildingIndex<>();
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .name("display-tracker-worker")
            .daemon()
            .uncaughtExceptionHandler(LoggingExceptionHandler.INSTANCE)
            .factory());
    private final NoHornyClient client;

    private record ProcessorWithLinks(MindustryDisplay.Processor processor, List<ImmutablePoint2> links) {}

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
                final int resolution = ((LogicDisplay) building.block).displaySize;
                DisplayTracker.this.scheduler.execute(() -> {
                    final var processors = DisplayTracker.this
                            .processors
                            .selectAllWithinSquare(
                                    x - PROCESSOR_SEARCH_RADIUS,
                                    y - PROCESSOR_SEARCH_RADIUS,
                                    (PROCESSOR_SEARCH_RADIUS * 2) + size)
                            .stream()
                            .filter(entry -> entry.data().links().stream()
                                    .anyMatch(link -> x <= link.x()
                                            && link.x() < x + size
                                            && y <= link.y()
                                            && link.y() < y + size))
                            .collect(Collectors.toUnmodifiableMap(
                                    processor -> new ImmutablePoint2(processor.x() - x, processor.y() - y),
                                    processor -> processor.data().processor(),
                                    (a, b) -> a.instructions().size()
                                                    > b.instructions().size()
                                            ? a
                                            : b));
                    final var added = DisplayTracker.this.displays.upsert(
                            x, y, size, new MindustryDisplay(resolution, processors));
                    DisplayTracker.this.modified.mark(added);
                });
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                DisplayTracker.this.scheduler.execute(() -> {
                    final var removed = DisplayTracker.this.displays.removeAllWithinSquare(x, y, size);
                    DisplayTracker.this.modified.unmarkAll(removed);
                });
            }

            @Override
            public void onRemoveAll() {
                DisplayTracker.this.scheduler.execute(() -> {
                    DisplayTracker.this.displays.removeAll();
                    DisplayTracker.this.modified.unmarkAll();
                });
            }
        });

        MindustryUtils.onEvent(LogicBlock.LogicBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(final LogicBlock.LogicBuild building, final @Nullable Player player) {
                final var x = BuildingUtils.anchorTileX(building);
                final var y = BuildingUtils.anchorTileY(building);
                final var size = building.block.size;
                final var links = new ArrayList<ImmutablePoint2>(building.links.size);
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
                            x, y, size, new ProcessorWithLinks(data, Collections.unmodifiableList(links)));
                    DisplayTracker.this.forEachLinkUpdateDisplay(processor, LinkUpdateKind.CREATE);
                });
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                DisplayTracker.this.scheduler.execute(() -> {
                    for (final var processor : DisplayTracker.this.processors.removeAllWithinSquare(x, y, size)) {
                        DisplayTracker.this.forEachLinkUpdateDisplay(processor, LinkUpdateKind.REMOVE);
                    }
                });
            }

            @Override
            public void onRemoveAll() {
                DisplayTracker.this.scheduler.execute(DisplayTracker.this.processors::removeAll);
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
        final var marked = this.displays.groups().stream()
                .filter(group ->
                        group.elements().stream().anyMatch(display -> this.modified.marked(display.x(), display.y())))
                .toList();
        for (final var group : marked) {
            this.client.accept(group);
            for (final var display : group.elements()) {
                this.modified.unmark(display.x(), display.y());
            }
        }
    }

    private void forEachLinkUpdateDisplay(
            final VirtualBuilding<ProcessorWithLinks> processor, final LinkUpdateKind kind) {
        for (final var link : processor.data().links()) {
            final var display = this.displays.select(link.x(), link.y());
            if (display == null) {
                continue;
            }
            final var processors = new HashMap<>(display.data().processors());
            final var point = new ImmutablePoint2(processor.x() - display.x(), processor.y() - display.y());
            switch (kind) {
                case CREATE -> processors.put(point, processor.data().processor());
                case REMOVE -> processors.remove(point);
            }
            final var updated = this.displays.upsert(
                    display.x(),
                    display.y(),
                    display.size(),
                    new MindustryDisplay(display.data().resolution(), Collections.unmodifiableMap(processors)));
            this.modified.mark(updated);
        }
    }

    private enum LinkUpdateKind {
        CREATE,
        REMOVE,
    }
}
