// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.tracking;

import com.xpdustry.nohorny.NoHornyClient;
import com.xpdustry.nohorny.event.BuildingLifecycleEventListener;
import com.xpdustry.nohorny.event.BuildingUtils;
import com.xpdustry.nohorny.event.EventSubscriptionManager;
import com.xpdustry.nohorny.geometry.ImmutablePoint2;
import com.xpdustry.nohorny.geometry.VirtualBuilding;
import com.xpdustry.nohorny.geometry.VirtualBuildingIndex;
import com.xpdustry.nohorny.image.DrawInstruction;
import com.xpdustry.nohorny.image.MindustryAuthor;
import com.xpdustry.nohorny.image.MindustryDisplay;
import com.xpdustry.nohorny.image.MindustryProcessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import mindustry.gen.Player;
import mindustry.logic.LExecutor;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import org.jspecify.annotations.Nullable;

public final class DisplayTracker extends BaseTracker<MindustryDisplay> {

    private final VirtualBuildingIndex<MindustryProcessor> processors = new VirtualBuildingIndex<>();
    private final EventSubscriptionManager events = new EventSubscriptionManager();
    private final DisplayTrackerConfig config;
    private final VirtualBuildingIndex<MindustryDisplay> displays;

    public DisplayTracker(
            final ScheduledExecutorService scheduler, final NoHornyClient client, final DisplayTrackerConfig config) {
        final var displays = new VirtualBuildingIndex<MindustryDisplay>();
        super(displays, scheduler, client, config);
        this.config = config;
        this.displays = displays;
    }

    @Override
    public void onInit() {
        super.onInit();
        this.events.subscribe(LogicDisplay.LogicDisplayBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(LogicDisplay.LogicDisplayBuild building, final @Nullable Player player) {
                final var radius = DisplayTracker.this.config.processorSearchRadius();
                final int x = BuildingUtils.anchorTileX(building);
                final int y = BuildingUtils.anchorTileY(building);
                final int size = building.block.size;
                final int resolution = ((LogicDisplay) building.block).displaySize;

                DisplayTracker.this.serialExecute(() -> {
                    final var processors = DisplayTracker.this
                            .processors
                            .selectAllWithinCircle(x + Math.floorDiv(size, 2), y + Math.floorDiv(size, 2), radius)
                            .stream()
                            .filter(entry -> entry.data().links().stream().anyMatch(link -> {
                                final int xd = link.x() - x;
                                final int yd = link.y() - y;
                                return (xd * xd) + (yd * yd) < radius * radius;
                            }))
                            .collect(Collectors.collectingAndThen(
                                    Collectors.toMap(
                                            processor -> new ImmutablePoint2(processor.x(), processor.y()),
                                            VirtualBuilding::data,
                                            (_, _) -> {
                                                throw new IllegalStateException("2 processors occupy the same space");
                                            },
                                            TreeMap::new),
                                    Collections::unmodifiableSortedMap));
                    DisplayTracker.this.displays.insert(x, y, size, new MindustryDisplay(resolution, processors));
                    DisplayTracker.this.markForProcessing(x, y);
                });
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                DisplayTracker.this.serialExecute(() -> {
                    for (final var element : DisplayTracker.this.displays.removeAllWithinSquare(x, y, size)) {
                        DisplayTracker.this.unmarkForProcessing(element.x(), element.y());
                    }
                });
            }

            @Override
            public void onRemoveAll() {
                DisplayTracker.this.serialExecute(() -> {
                    DisplayTracker.this.displays.removeAll();
                    DisplayTracker.this.unmarkAllForProcessing();
                });
            }
        });

        this.events.subscribe(LogicBlock.LogicBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(final LogicBlock.LogicBuild building, final @Nullable Player player) {
                final var x = BuildingUtils.anchorTileX(building);
                final var y = BuildingUtils.anchorTileY(building);
                final var size = building.block.size;
                final var author = player == null ? null : new MindustryAuthor(player);
                final var links = new ArrayList<ImmutablePoint2>(building.links.size);
                for (final var link : building.links) {
                    links.add(new ImmutablePoint2(link.x, link.y));
                }
                final var instructions = DisplayTracker.this.instructions(building.executor);
                final var data = new MindustryProcessor(instructions, links, author);

                DisplayTracker.this.serialExecute(() -> {
                    final var processor = DisplayTracker.this.processors.insert(x, y, size, data);
                    if (processor != null) {
                        DisplayTracker.this.forEachDisplayUpdateProcessorLink(links, processor, LinkUpdateKind.CREATE);
                    }
                });
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                DisplayTracker.this.serialExecute(() -> {
                    for (final var element : DisplayTracker.this.processors.removeAllWithinSquare(x, y, size)) {
                        DisplayTracker.this.forEachDisplayUpdateProcessorLink(
                                element.data().links(), element, LinkUpdateKind.REMOVE);
                    }
                });
            }

            @Override
            public void onRemoveAll() {
                DisplayTracker.this.serialExecute(DisplayTracker.this.processors::removeAll);
            }
        });
    }

    @Override
    public void onExit() {
        super.onExit();
        this.events.clear();
    }

    @Override
    protected boolean isEligible(final MindustryDisplay image) {
        return image.processors().values().stream()
                        .filter(processor -> processor.instructions().size() >= this.config.minimumInstructionCount())
                        .count()
                >= this.config.minimumProcessorCount();
    }

    private List<DrawInstruction> instructions(final LExecutor executor) {
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
        return result;
    }

    private void forEachDisplayUpdateProcessorLink(
            final Collection<ImmutablePoint2> displayLocations,
            final VirtualBuilding<MindustryProcessor> processor,
            final LinkUpdateKind kind) {
        for (final var location : displayLocations) {
            final var display = this.displays.select(location.x(), location.y());
            if (display == null) {
                continue;
            }
            final var processors = new TreeMap<>(display.data().processors());
            final var point = new ImmutablePoint2(processor.x(), processor.y());
            switch (kind) {
                case CREATE -> processors.put(point, processor.data());
                case REMOVE -> processors.remove(point);
            }
            final var data =
                    new MindustryDisplay(display.data().resolution(), Collections.unmodifiableSortedMap(processors));
            this.displays.remove(display.x(), display.y());
            this.displays.insert(display.x(), display.y(), display.size(), data);
            this.markForProcessing(display.x(), display.y());
        }
    }

    private enum LinkUpdateKind {
        CREATE,
        REMOVE,
    }
}
