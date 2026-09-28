// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import mindustry.gen.Building;
import mindustry.world.Block;
import org.jspecify.annotations.Nullable;

// Groups adjacent buildings into images, using the state of each building
abstract class BuildingImageTracker<B extends Building, T extends MindustryImage> implements LifecycleListener {

    final VirtualBuildingIndex<T> index = new VirtualBuildingIndex<>();
    final Class<B> buildingType;
    private final NoHornyEventBus events;
    private final GroupCollector<T> collector;
    private final int minGroupSize;

    protected BuildingImageTracker(
            final NoHornyEventBus events,
            final GroupClassifier classifier,
            final Class<B> buildingType,
            final Class<? extends Block> blockType,
            final int maxGroupRange,
            final int minGroupSize) {
        this.events = events;
        this.buildingType = buildingType;
        this.minGroupSize = minGroupSize;
        this.collector = new GroupCollector<>(
                this.index, classifier, blockType::isInstance, this::isEligible, this::isEligible, maxGroupRange);
    }

    protected abstract T data(final B building, final @Nullable MindustryAuthor author);

    protected boolean isEligible(final VirtualBuilding<T> anchor) {
        return true;
    }

    protected boolean isEligible(final VirtualBuilding.Group<T> group) {
        return group.w() >= this.minGroupSize && group.h() >= this.minGroupSize;
    }

    @Override
    public void onInit() {
        this.events.subscribe(this.buildingType, new NoHornyEventBus.BuildingSubscriber<>() {
            @Override
            public void onCreate(final B building, final @Nullable MindustryAuthor author, final boolean queue) {
                BuildingImageTracker.this.upsert(building, author, queue);
            }

            @Override
            public void onRemoveAll() {
                BuildingImageTracker.this.index.removeAll();
                BuildingImageTracker.this.collector.clear();
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                for (final var removed : BuildingImageTracker.this.index.removeAllWithinSquare(x, y, size)) {
                    BuildingImageTracker.this.collector.dequeue(removed.packed());
                }
            }
        });
    }

    @Override
    public void onTick() {
        this.collector.tick();
    }

    private void upsert(final B building, final @Nullable MindustryAuthor author, final boolean queue) {
        final var x = MindustryUtils.anchorTileX(building);
        final var y = MindustryUtils.anchorTileY(building);
        final var added = this.index.upsert(x, y, building.block.size, this.data(building, author));
        if (queue) {
            this.collector.enqueue(added.packed());
        }
    }
}
