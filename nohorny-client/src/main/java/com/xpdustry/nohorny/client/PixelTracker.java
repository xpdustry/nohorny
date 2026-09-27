// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.graphics.Color;
import arc.struct.IntIntMap;
import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryPixel;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.function.ToIntFunction;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.power.LightBlock;
import org.jspecify.annotations.Nullable;

// Pixel art made of single tile buildings displaying a configurable color
final class PixelTracker<B extends Building> extends BuildingImageTracker<B, MindustryPixel> {

    private static final int MAX_GROUP_RANGE = 150;
    private static final int MAX_GROUP_STEPS = 200;
    private static final int MIN_PIXEL_GROUP_SIZE = 16;
    // Linked processors can change the color without firing any event,
    // so linked positions are checked round-robin, a few per tick to keep the main loop smooth
    private static final int POLL_BUDGET_PER_TICK = 256;

    private final ToIntFunction<B> color;
    // Processor anchor -> linked positions
    private final IntMap<IntSeq> processors = new IntMap<>();
    // Linked position -> number of processors linking it
    private final IntIntMap linked = new IntIntMap();
    private final IntSeq polled = new IntSeq();
    private final IntSet polledSet = new IntSet();
    private int cursor = 0;

    private PixelTracker(
            final NoHornyClient client,
            final Class<B> buildingType,
            final Class<? extends Block> blockType,
            final ToIntFunction<B> color) {
        super(client, buildingType, blockType, MAX_GROUP_RANGE, MAX_GROUP_STEPS, MIN_PIXEL_GROUP_SIZE);
        this.color = color;
    }

    public static PixelTracker<Sorter.SorterBuild> sorters(final NoHornyClient client) {
        // Unconfigured sorters display a dark cross
        return new PixelTracker<>(
                client,
                Sorter.SorterBuild.class,
                Sorter.class,
                building -> building.sortItem == null ? Color.blackRgba : building.sortItem.color.rgba());
    }

    public static PixelTracker<LightBlock.LightBuild> illuminators(final NoHornyClient client) {
        // In game, the color is blended with the sprite, but we render it raw to classify the intended image
        return new PixelTracker<>(
                client, LightBlock.LightBuild.class, LightBlock.class, building -> building.color | 0xFF);
    }

    @Override
    public void onInit() {
        super.onInit();

        // Non-privileged processors can only control linked buildings, see LExecutor.ControlI
        MindustryUtils.onEvent(LogicBlock.LogicBuild.class, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(
                    final LogicBlock.LogicBuild building, final @Nullable MindustryAuthor author, final boolean queue) {
                final var links = new IntSeq(building.links.size);
                for (final var link : building.links) {
                    final var packed = GeometryUtils.pack(link.x, link.y);
                    links.add(packed);
                    PixelTracker.this.linked.put(packed, PixelTracker.this.linked.get(packed) + 1);
                    if (PixelTracker.this.polledSet.add(packed)) {
                        PixelTracker.this.polled.add(packed);
                    }
                }
                PixelTracker.this.processors.put(
                        GeometryUtils.pack(MindustryUtils.anchorTileX(building), MindustryUtils.anchorTileY(building)),
                        links);
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                final var links = PixelTracker.this.processors.remove(GeometryUtils.pack(x, y));
                if (links == null) {
                    return;
                }
                for (int i = 0; i < links.size; i++) {
                    final var packed = links.get(i);
                    final var count = PixelTracker.this.linked.get(packed) - 1;
                    if (count == 0) {
                        PixelTracker.this.linked.remove(packed);
                    } else {
                        PixelTracker.this.linked.put(packed, count);
                    }
                }
            }

            @Override
            public void onRemoveAll() {
                PixelTracker.this.processors.clear();
                PixelTracker.this.linked.clear();
                PixelTracker.this.polled.clear();
                PixelTracker.this.polledSet.clear();
            }
        });

        MindustryUtils.onEvent(EventType.Trigger.update, _ -> {
            if (Vars.state.isGame()) {
                this.poll();
            }
        });
    }

    @Override
    protected MindustryPixel data(final B building, final @Nullable MindustryAuthor author) {
        return new MindustryPixel(this.color.applyAsInt(building), author);
    }

    @Override
    protected boolean isEligible(final VirtualBuilding.Group<MindustryPixel> group) {
        if (!super.isEligible(group)) {
            return false;
        }
        final var first = group.elements().iterator().next().data().rgba();
        for (final var element : group.elements()) {
            if (element.data().rgba() != first) {
                return true;
            }
        }
        return false;
    }

    private void poll() {
        final var budget = Math.min(POLL_BUDGET_PER_TICK, this.polled.size);
        for (int i = 0; i < budget; i++) {
            if (this.cursor >= this.polled.size) {
                this.cursor = 0;
            }
            final var packed = this.polled.get(this.cursor);
            if (!this.linked.containsKey(packed)) {
                // Swap remove, the swapped element is checked next
                this.polledSet.remove(packed);
                this.polled.set(this.cursor, this.polled.peek());
                this.polled.pop();
                continue;
            }
            this.cursor++;
            final var tracked = this.index.select(GeometryUtils.x(packed), GeometryUtils.y(packed));
            if (tracked == null) {
                continue;
            }
            final var building = Vars.world.build(GeometryUtils.x(packed), GeometryUtils.y(packed));
            if (this.buildingType.isInstance(building)) {
                final var casted = this.buildingType.cast(building);
                if (this.color.applyAsInt(casted) != tracked.data().rgba()) {
                    this.upsert(casted, tracked.data().author(), true);
                }
            }
        }
    }
}
